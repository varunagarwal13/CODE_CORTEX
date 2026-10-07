#!/usr/bin/env python3
"""Reproducible benchmark for VOCIS voice-clone defence scores.

Reads eval/manifest.csv, runs each labelled session through the shipped ONNX models
(speaker_encoder.onnx and spoof_detector.onnx), and reports precision / recall /
accuracy at session level using median-of-five stabilisation (SessionScores).

Usage (from repo root):

    python eval/benchmark.py
    python eval/benchmark.py --manifest eval/manifest.csv --out eval/output

Each manifest row needs enrol_audio, probe_audio, and label (genuine | clone | impostor).
Optional probe_channel: mic | voip-wb | voip-nb (default mic).
"""

from __future__ import annotations

import argparse
import csv
import io
import json
import subprocess
import sys
import wave
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import onnxruntime as ort
from scipy.signal import butter, sosfilt

REPO_ROOT = Path(__file__).resolve().parent.parent
EVAL_DIR = REPO_ROOT / "eval"
sys.path.insert(0, str(EVAL_DIR))

from session_scores import SessionScores, WindowAnalysis, WindowVerdict  # noqa: E402

SAMPLE_RATE = 16000
WINDOW_SAMPLES = 64600
HOP_SAMPLES = 32300
PARTIAL_SAMPLES = 25600
PARTIAL_HOP = 12800

SIMILARITY_HIGH = 0.75
SIMILARITY_LOW = 0.60
SYNTHETIC_HIGH = 0.50
SYNTHETIC_ELEVATED = 0.30
MIN_RMS = 0.005
SYNTHETIC_BASELINE_MARGIN = 0.15
AUDIO_NORM_TARGET_DBFS = -30.0

POSITIVE_LABELS = {"clone", "impostor", "attack", "scam"}
NEGATIVE_LABELS = {"genuine", "benign", "safe"}


def load_16k_mono(path: Path) -> np.ndarray:
    """Load audio from any format (wav, ogg, mpeg, mp3) into 16kHz mono float32 [-1, 1]."""
    cmd = [
        "ffmpeg",
        "-y",
        "-v",
        "quiet",
        "-i",
        str(path),
        "-ar",
        str(SAMPLE_RATE),
        "-ac",
        "1",
        "-f",
        "wav",
        "pipe:1",
    ]
    try:
        proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
        with wave.open(io.BytesIO(proc.stdout), "rb") as wf:
            frames = wf.readframes(wf.getnframes())
            return np.frombuffer(frames, dtype=np.int16).astype(np.float32) / 32768.0
    except Exception as e:
        raise RuntimeError(f"Failed to decode {path} using ffmpeg: {e}") from e


def normalize_volume(
    wav: np.ndarray, target_dbfs: float = AUDIO_NORM_TARGET_DBFS, increase_only: bool = True
) -> np.ndarray:
    rms = np.sqrt(np.mean((wav.astype(np.float64)) ** 2))
    if rms < 1e-10:
        return wav
    dbfs = 20 * np.log10(rms)
    delta = target_dbfs - dbfs
    if increase_only and delta < 0:
        return wav
    return (wav * (10 ** (delta / 20))).astype(np.float32)


# ---------------------------------------------------------------- Channel simulation


def _bandpass(wav: np.ndarray, low: float, high: float, rate: int) -> np.ndarray:
    nyq = rate / 2
    high = min(high, nyq * 0.99)
    sos = butter(6, [low / nyq, high / nyq], btype="band", output="sos")
    return sosfilt(sos, wav).astype(np.float32)


def _mulaw_roundtrip(wav: np.ndarray) -> np.ndarray:
    mu = 255.0
    x = np.clip(wav, -1.0, 1.0)
    encoded = np.sign(x) * np.log1p(mu * np.abs(x)) / np.log1p(mu)
    quantised = np.round((encoded + 1.0) * 127.5) / 127.5 - 1.0
    decoded = np.sign(quantised) * (1.0 / mu) * ((1.0 + mu) ** np.abs(quantised) - 1.0)
    return decoded.astype(np.float32)


def _agc(wav: np.ndarray, target_dbfs: float = -23.0) -> np.ndarray:
    rms = float(np.sqrt((wav**2).mean()))
    if rms <= 0:
        return wav
    gain = 10 ** ((target_dbfs - 20 * np.log10(rms)) / 20)
    return np.clip(wav * gain, -1.0, 1.0).astype(np.float32)


def _noise(wav: np.ndarray, snr_db: float) -> np.ndarray:
    rms = float(np.sqrt((wav**2).mean()))
    if rms <= 0:
        return wav
    noise_rms = rms / (10 ** (snr_db / 20))
    rng = np.random.default_rng(1234)
    return (wav + rng.normal(0, noise_rms, wav.shape)).astype(np.float32)


def channel_mic(wav: np.ndarray, rate: int = SAMPLE_RATE) -> np.ndarray:
    return wav


def channel_voip_wideband(wav: np.ndarray, rate: int = SAMPLE_RATE) -> np.ndarray:
    out = _agc(wav)
    out = _bandpass(out, 100.0, 7000.0, rate)
    return _noise(out, snr_db=32.0)


def channel_voip_narrowband(wav: np.ndarray, rate: int = SAMPLE_RATE) -> np.ndarray:
    out = _agc(wav)
    out = _bandpass(out, 300.0, 3400.0, rate)
    out = _mulaw_roundtrip(out)
    return _noise(out, snr_db=28.0).astype(np.float32)


CHANNELS = [
    ("mic", channel_mic),
    ("voip-wb", channel_voip_wideband),
    ("voip-nb", channel_voip_narrowband),
]
CHANNEL_BY_NAME = dict(CHANNELS)


def apply_probe_channel(wav: np.ndarray, channel: str) -> np.ndarray:
    fn = CHANNEL_BY_NAME.get(channel.strip().lower(), channel_mic)
    return fn(wav.copy(), SAMPLE_RATE)


# ---------------------------------------------------------------- Model Pipeline


class OnnxVcdEngine:
    def __init__(self, encoder_path: Path, detector_path: Path):
        opts = ort.SessionOptions()
        opts.inter_op_num_threads = 2
        opts.intra_op_num_threads = 2
        self.encoder = ort.InferenceSession(str(encoder_path), sess_options=opts)
        self.detector = ort.InferenceSession(str(detector_path), sess_options=opts)

    def embed_utterance(self, wav: np.ndarray) -> np.ndarray:
        norm_wav = normalize_volume(wav)
        if len(norm_wav) < PARTIAL_SAMPLES:
            norm_wav = np.pad(norm_wav, (0, PARTIAL_SAMPLES - len(norm_wav)))

        partials = []
        start = 0
        while start + PARTIAL_SAMPLES <= len(norm_wav):
            partials.append(norm_wav[start : start + PARTIAL_SAMPLES])
            start += PARTIAL_HOP
        if not partials:
            partials.append(norm_wav[:PARTIAL_SAMPLES])

        embeds = []
        for p in partials:
            out = self.encoder.run(None, {"waveform": p.reshape(1, PARTIAL_SAMPLES).astype(np.float32)})[0]
            embeds.append(out[0])

        mean = np.mean(embeds, axis=0)
        norm = np.linalg.norm(mean)
        return mean / (norm + 1e-9)

    def detect_synthetic(self, window: np.ndarray) -> float:
        if len(window) < WINDOW_SAMPLES:
            window = np.pad(window, (0, WINDOW_SAMPLES - len(window)))
        elif len(window) > WINDOW_SAMPLES:
            window = window[:WINDOW_SAMPLES]

        logits = self.detector.run(None, {"waveform": window.reshape(1, WINDOW_SAMPLES).astype(np.float32)})[0]
        shifted = logits - np.max(logits, axis=-1, keepdims=True)
        exp = np.exp(shifted)
        # Logits are ordered [spoof, bonafide], return P(spoof)
        return float(exp[0, 0] / np.sum(exp[0]))

    def measure_synthetic_baseline(self, wav: np.ndarray) -> float | None:
        if len(wav) < WINDOW_SAMPLES:
            return None
        scores = []
        for start in range(0, len(wav) - WINDOW_SAMPLES + 1, WINDOW_SAMPLES):
            scores.append(self.detect_synthetic(wav[start : start + WINDOW_SAMPLES]))
        return float(np.median(scores)) if scores else None


# ---------------------------------------------------------------- Fusion Rules


def spoof_check_status(baseline: float | None) -> str:
    if baseline is None:
        return "NO_BASELINE"
    if baseline + SYNTHETIC_BASELINE_MARGIN >= 1.0:
        return "UNRELIABLE"
    return "USABLE"


def effective_synthetic_threshold(baseline: float | None) -> float:
    if baseline is None:
        return SYNTHETIC_HIGH
    return max(SYNTHETIC_HIGH, baseline + SYNTHETIC_BASELINE_MARGIN)


def fuse(
    similarity: float | None,
    synthetic: float | None,
    baseline: float | None = None,
) -> tuple[str, str]:
    if synthetic is None:
        return "INDETERMINATE", "PIPELINE_UNAVAILABLE"
    if similarity is None:
        if synthetic >= SYNTHETIC_HIGH:
            return "SUSPICIOUS", "SYNTHETIC_UNKNOWN_SPEAKER"
        return "INDETERMINATE", "NO_VOICEPRINT_SELECTED"

    high = similarity >= SIMILARITY_HIGH
    low = similarity < SIMILARITY_LOW
    status = spoof_check_status(baseline)
    threshold = effective_synthetic_threshold(baseline)

    if high and status == "UNRELIABLE":
        return "SAFE", "MATCH_SPOOF_CHECK_UNRELIABLE"
    if high and synthetic >= threshold:
        return "CRITICAL", "CLONE_SIGNATURE"
    if high and synthetic >= SYNTHETIC_ELEVATED:
        return "SUSPICIOUS", "POSSIBLE_SYNTHESIS"
    if high:
        return "SAFE", "MATCH_AUTHENTIC"
    if low:
        return "SUSPICIOUS", "NOT_CLAIMED_CONTACT"
    return "SUSPICIOUS", "BORDERLINE_SIMILARITY"


def slice_windows(wav: np.ndarray) -> list[tuple[int, np.ndarray]]:
    if len(wav) < WINDOW_SAMPLES:
        return []
    out, start = [], 0
    while start + WINDOW_SAMPLES <= len(wav):
        out.append((start, wav[start : start + WINDOW_SAMPLES]))
        start += HOP_SAMPLES
    last = len(wav) - WINDOW_SAMPLES
    if out and out[-1][0] < last:
        out.append((last, wav[last:]))
    return out


# ---------------------------------------------------------------- Session Evaluation


@dataclass
class Voiceprint:
    label: str
    embedding: np.ndarray
    baseline: float | None


@dataclass
class SessionResult:
    session_id: str
    label: str
    contact_name: str
    source: str
    peak_level: str
    stable_peak_level: str
    stable_level: str
    windows: int
    median_similarity: float | None
    median_synthetic: float | None
    baseline: float | None
    notes: str


def enrol_variants(engine: OnnxVcdEngine, wav: np.ndarray) -> list[Voiceprint]:
    out: list[Voiceprint] = []
    for label, fn in CHANNELS:
        degraded = fn(wav.copy(), SAMPLE_RATE)
        embedding = engine.embed_utterance(degraded)
        baseline = engine.measure_synthetic_baseline(degraded)
        out.append(Voiceprint(label, embedding, baseline))
    return out


def best_match(voiceprints: list[Voiceprint], embedding: np.ndarray) -> tuple[float, Voiceprint]:
    best_sim = -1.0
    best_vp = voiceprints[0]
    for vp in voiceprints:
        sim = float(np.dot(embedding, vp.embedding))
        if sim > best_sim:
            best_sim = sim
            best_vp = vp
    return best_sim, best_vp


def score_session(
    engine: OnnxVcdEngine,
    voiceprints: list[Voiceprint],
    probe_wav: np.ndarray,
    contact_name: str,
) -> SessionResult:
    scores = SessionScores()

    def fuse_for_session(sim: float | None, syn: float | None) -> WindowVerdict:
        baseline = voiceprints[0].baseline
        level, reason = fuse(sim, syn, baseline)
        return WindowVerdict(level, reason, sim, syn, contact_name)

    sims: list[float] = []
    syns: list[float] = []

    for start, window in slice_windows(probe_wav):
        rms = float(np.sqrt((window**2).mean()))
        if rms < MIN_RMS:
            verdict = WindowVerdict("INDETERMINATE", "NO_SPEECH", None, None, contact_name)
        else:
            synthetic = engine.detect_synthetic(window)
            embedding = engine.embed_utterance(window)
            similarity, matched = best_match(voiceprints, embedding)
            level, reason = fuse(similarity, synthetic, matched.baseline)
            verdict = WindowVerdict(level, reason, similarity, synthetic, contact_name)
            sims.append(similarity)
            syns.append(synthetic)

        analysis = WindowAnalysis(start / SAMPLE_RATE, verdict)
        scores = scores.accept(analysis, fuse_for_session)

    return SessionResult(
        session_id="",
        label="",
        contact_name=contact_name,
        source="",
        peak_level=scores.peak_level,
        stable_peak_level=scores.stable_peak_level,
        stable_level=scores.stable_level,
        windows=scores.measured_windows,
        median_similarity=float(np.median(sims)) if sims else None,
        median_synthetic=float(np.median(syns)) if syns else None,
        baseline=voiceprints[0].baseline,
        notes="",
    )


def is_positive_prediction(level: str, tier: str) -> bool:
    if tier == "critical":
        return level == "CRITICAL"
    return level in {"SUSPICIOUS", "CRITICAL"}


def is_positive_label(label: str) -> bool:
    return label.strip().lower() in POSITIVE_LABELS


def compute_metrics(results: list[SessionResult], tier: str) -> dict:
    tp = fp = tn = fn = 0
    indeterminate = 0
    for r in results:
        level = r.stable_peak_level
        if level == "INDETERMINATE":
            indeterminate += 1
            continue
        predicted = is_positive_prediction(level, tier)
        actual = is_positive_label(r.label)
        if predicted and actual:
            tp += 1
        elif predicted and not actual:
            fp += 1
        elif not predicted and actual:
            fn += 1
        else:
            tn += 1
    total = tp + fp + tn + fn
    precision = tp / (tp + fp) if (tp + fp) else None
    recall = tp / (tp + fn) if (tp + fn) else None
    accuracy = (tp + tn) / total if total else None
    fpr = fp / (fp + tn) if (fp + tn) else None
    f1 = (
        2 * precision * recall / (precision + recall)
        if precision is not None and recall is not None and (precision + recall) > 0
        else None
    )
    return {
        "tier": tier,
        "tp": tp,
        "fp": fp,
        "tn": tn,
        "fn": fn,
        "indeterminate_sessions": indeterminate,
        "precision": precision,
        "recall": recall,
        "accuracy": accuracy,
        "fpr": fpr,
        "f1": f1,
    }


def load_manifest(path: Path) -> list[dict]:
    rows = []
    with path.open(newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            if row.get("session_id", "").startswith("#"):
                continue
            rows.append(row)
    return rows


def _print_metrics(m: dict) -> None:
    def pct(x: float | None) -> str:
        return f"{x * 100:.1f}%" if x is not None else "n/a"

    print(
        f"  TP={m['tp']} FP={m['fp']} TN={m['tn']} FN={m['fn']} "
        f"indeterminate={m['indeterminate_sessions']}"
    )
    print(
        f"  precision={pct(m['precision'])}  recall={pct(m['recall'])}  "
        f"accuracy={pct(m['accuracy'])}  fpr={pct(m['fpr'])}  f1={pct(m['f1'])}"
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="VOCIS VCD labelled benchmark")
    parser.add_argument(
        "--manifest",
        type=Path,
        default=REPO_ROOT / "eval" / "manifest.csv",
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=REPO_ROOT / "eval" / "output",
    )
    args = parser.parse_args()

    manifest_path = args.manifest.resolve()
    out_dir = args.out.resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    rows = load_manifest(manifest_path)
    if not rows:
        print(f"No sessions in {manifest_path}")
        return 2

    encoder_path = REPO_ROOT / "app" / "src" / "main" / "assets" / "models" / "speaker_encoder.onnx"
    detector_path = REPO_ROOT / "app" / "src" / "main" / "assets" / "models" / "spoof_detector.onnx"

    if not encoder_path.exists() or not detector_path.exists():
        print(f"ERROR: Missing ONNX models in {encoder_path.parent}")
        return 1

    print(f"Loading ONNX models from {encoder_path.parent}...")
    engine = OnnxVcdEngine(encoder_path, detector_path)

    results: list[SessionResult] = []
    for row in rows:
        enrol = REPO_ROOT / row["enrol_audio"]
        probe = REPO_ROOT / row["probe_audio"]
        if not enrol.exists() or not probe.exists():
            print(f"SKIP {row['session_id']}: missing audio ({enrol.name} / {probe.name})")
            continue

        enrol_wav = load_16k_mono(enrol)
        probe_wav = load_16k_mono(probe)
        probe_channel = (row.get("probe_channel") or "mic").strip().lower()
        probe_wav = apply_probe_channel(probe_wav, probe_channel)
        voiceprints = enrol_variants(engine, enrol_wav)
        session = score_session(
            engine,
            voiceprints,
            probe_wav,
            row.get("contact_name", ""),
        )
        session.session_id = row["session_id"]
        session.label = row["label"].strip().lower()
        session.source = row.get("source", "")
        session.notes = row.get("notes", "")
        results.append(session)
        print(
            f"{session.session_id:30} label={session.label:8} "
            f"peak={session.peak_level:13} stable_peak={session.stable_peak_level:13} "
            f"sim={session.median_similarity:.3f} syn={session.median_synthetic:.3f}"
            if session.median_similarity is not None and session.median_synthetic is not None
            else f"{session.session_id:30} label={session.label:8} peak={session.peak_level}"
        )

    if not results:
        print("No sessions scored — check manifest paths and model checkpoints.")
        return 2

    metrics_high = compute_metrics(results, "high_plus")
    metrics_critical = compute_metrics(results, "critical")

    sessions_csv = out_dir / "sessions.csv"
    with sessions_csv.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(asdict(results[0]).keys()))
        writer.writeheader()
        for r in results:
            writer.writerow(asdict(r))

    report = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "manifest": str(manifest_path.relative_to(REPO_ROOT)),
        "sessions_scored": len(results),
        "aggregation": "session-level stable_peak_level (median-of-five stabilisation)",
        "thresholds": {
            "similarity_high": SIMILARITY_HIGH,
            "similarity_low": SIMILARITY_LOW,
            "synthetic_high": SYNTHETIC_HIGH,
            "synthetic_elevated": SYNTHETIC_ELEVATED,
            "synthetic_baseline_margin": SYNTHETIC_BASELINE_MARGIN,
        },
        "metrics": {
            "high_plus": metrics_high,
            "critical_only": metrics_critical,
        },
        "sessions": [asdict(r) for r in results],
    }

    metrics_json = out_dir / "metrics.json"
    metrics_json.write_text(json.dumps(report, indent=2), encoding="utf-8")

    print(f"\nWrote {sessions_csv.relative_to(REPO_ROOT)}")
    print(f"Wrote {metrics_json.relative_to(REPO_ROOT)}")
    print("\n--- high_plus (SUSPICIOUS or CRITICAL) ---")
    _print_metrics(metrics_high)
    print("\n--- critical_only (CRITICAL) ---")
    _print_metrics(metrics_critical)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
