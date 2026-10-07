"""
Comprehensive VOCIS + VOCIS Test Suite for All Audio Recordings in random folder.
Executes 9 Forensic and Intelligence Tests on:
1. 1234
2. qazwsx
3. qwerty
4. test_sample (WhatsApp Audio)
5. zxcv
"""
import os, sys, json, wave, re
import numpy as np
import requests
import onnxruntime as ort

RANDOM_DIR = r"C:\Users\varun\OneDrive\Desktop\random"
API_KEY    = os.environ.get("GROQ_API_KEY", "")
TRANSCRIBE_URL = "https://api.groq.com/openai/v1/audio/transcriptions"
CHAT_URL       = "https://api.groq.com/openai/v1/chat/completions"

AASIST_MODEL   = os.path.join(RANDOM_DIR, r"app\src\main\assets\models\spoof_detector.onnx")
ENCODER_MODEL  = os.path.join(RANDOM_DIR, r"app\src\main\assets\models\speaker_encoder.onnx")

WINDOW_SAMPLES   = 64_600
FILE_HOP_SAMPLES = 32_300
PARTIAL_LEN      = 25_600
PARTIAL_N        = 160
PARTIAL_HOP      = 40
MIN_RMS          = 0.005

SIMILARITY_HIGH  = 0.75
SIMILARITY_LOW   = 0.60
SYNTHETIC_HIGH   = 0.50
SYNTHETIC_ELEVATED = 0.30
SYNTHETIC_BASELINE_MARGIN = 0.15

SYSTEM_PROMPT = """You are a real-time telecommunication scam analysis system.
Analyze the provided conversational speech transcript.
The transcript may be in English, Hindi, Hinglish, or any Indian regional language.
Output ONLY valid JSON:
{
  "isScam": boolean,
  "scamCategory": "NONE"|"DIGITAL_ARREST"|"OTP_THEFT"|"BANKING_IMPERSONATION"|"REMOTE_ACCESS"|"COURIER_FRAUD"|"COERCIVE_AUTHORITY",
  "urgencyLevel": "LOW"|"MEDIUM"|"HIGH"|"EXTREME",
  "coercionTactics": ["string"],
  "scamScore": 0-100,
  "confidence": 0.0-1.0,
  "explanation": "concise rationale in English"
}"""

def w(msg):
    sys.stdout.buffer.write(msg.encode('utf-8', errors='replace'))

def load_wav(path):
    with wave.open(path, 'rb') as wf:
        raw = wf.readframes(wf.getnframes())
        sr  = wf.getframerate()
        ch  = wf.getnchannels()
        sw  = wf.getsampwidth()
        nf  = wf.getnframes()
    s = np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0
    if ch == 2: s = s[::2]
    return s, sr, ch, sw, nf

def normalize_volume(wav, target_dbfs=-30.0, increase_only=True):
    rms = float(np.sqrt(np.mean(wav**2))) + 1e-9
    gain = (10 ** (target_dbfs / 20.0)) / rms
    if increase_only and gain < 1.0: return wav
    return wav * gain

def softmax_spoof(logits):
    shifted = logits - logits.max()
    exp = np.exp(shifted)
    return float(exp[0] / exp.sum())

def embed_utterance(encoder_session, wav):
    inp_name = encoder_session.get_inputs()[0].name
    norm = normalize_volume(wav, target_dbfs=-30.0, increase_only=True)
    partials = []
    for i in range(PARTIAL_N):
        s = i * PARTIAL_HOP
        e = s + PARTIAL_LEN
        if e <= len(norm):
            partials.append(norm[s:e])
        else:
            p = norm[s:]
            partials.append(np.pad(p, (0, PARTIAL_LEN - len(p))))
    partials_arr = [p[np.newaxis, :].astype(np.float32) for p in partials]
    embeds = [encoder_session.run(None, {inp_name: p})[0] for p in partials_arr]
    mean_emb = np.mean(np.concatenate(embeds, axis=0), axis=0)
    norm_val = np.linalg.norm(mean_emb) + 1e-9
    return mean_emb / norm_val

def run_all():
    recordings = [
        ("1234", os.path.join(RANDOM_DIR, "1234_16k.wav"), False),
        ("qazwsx", os.path.join(RANDOM_DIR, "qazwsx_16k.wav"), False),
        ("qwerty", os.path.join(RANDOM_DIR, "qwerty_16k.wav"), False),
        ("test_sample", os.path.join(RANDOM_DIR, "test_sample_16k.wav"), False),
        ("zxcv", os.path.join(RANDOM_DIR, "zxcv_16k.wav"), True) # True = owner enrollment
    ]

    aasist_sess = ort.InferenceSession(AASIST_MODEL)
    enc_sess    = ort.InferenceSession(ENCODER_MODEL)

    results = []
    embeddings = {}

    w("\n========================================================================================\n")
    w("              VOCIS + VOCIS CORE FULL FORENSIC & INTELLIGENCE TEST SUITE                  \n")
    w("========================================================================================\n\n")

    for name, path, is_owner in recordings:
        w(f"----------------------------------------------------------------------------------------\n")
        w(f"  TESTING RECORDING: {name} ({os.path.basename(path)})\n")
        w(f"----------------------------------------------------------------------------------------\n")

        # 1. Format & Acoustic Stats
        wav, sr, ch, sw, nf = load_wav(path)
        duration = len(wav) / sr
        rms = float(np.sqrt(np.mean(wav**2)))
        peak = float(np.max(np.abs(wav)))
        w(f"[TEST 1] AUDIO SIGNAL METRICS:\n")
        w(f"   Duration: {duration:.2f}s | Sample Rate: {sr}Hz | Channels: {ch} | Bit Depth: {sw*8}bit\n")
        w(f"   RMS Amplitude: {rms:.4f} | Peak Amplitude: {peak:.4f}\n\n")

        # 2. Whisper ASR & Language Identification
        w(f"[TEST 2] SPEECH RECOGNITION & MULTILINGUAL ID (Whisper Large v3 Turbo):\n")
        with open(path, 'rb') as f:
            r = requests.post(
                TRANSCRIBE_URL,
                headers={'Authorization': f'Bearer {API_KEY}'},
                files={'file': (os.path.basename(path), f, 'audio/wav')},
                data={'model': 'whisper-large-v3-turbo', 'response_format': 'verbose_json'},
                timeout=30
            )
        td = r.json()
        lang = td.get('language', 'unknown')
        text = td.get('text', '').strip()
        is_eng = (lang.lower() in ('en', 'english'))
        w(f"   Detected Language : {lang.title()} ({'English - AASIST Reliable' if is_eng else 'Non-English - AASIST Suppressed'})\n")
        w(f"   Raw Transcript    : \"{text}\"\n\n")

        # 3. AASIST Spoof Inference
        w(f"[TEST 3] AASIST ACOUSTIC DEEPFAKE WINDOW EVALUATION:\n")
        win_scores = []
        win_idx = 1
        for start in range(0, len(wav) - WINDOW_SAMPLES + 1, FILE_HOP_SAMPLES):
            win = wav[start:start+WINDOW_SAMPLES]
            w_rms = float(np.sqrt(np.mean(win**2)))
            if w_rms < MIN_RMS:
                w(f"   Win #{win_idx} [{start/sr:.1f}s->{(start+WINDOW_SAMPLES)/sr:.1f}s] RMS:{w_rms:.4f} [SILENCE - SKIPPED]\n")
                win_idx += 1
                continue
            inp = win[np.newaxis, :].astype(np.float32)
            out = aasist_sess.run(None, {aasist_sess.get_inputs()[0].name: inp})[0]
            p = softmax_spoof(out[0])
            win_scores.append(p)
            w(f"   Win #{win_idx} [{start/sr:.1f}s->{(start+WINDOW_SAMPLES)/sr:.1f}s] RMS:{w_rms:.4f} Logits:[{out[0][0]:+.3f}, {out[0][1]:+.3f}] -> P(Synth): {p*100:.2f}%\n")
            win_idx += 1

        mean_synth = float(np.mean(win_scores)) if win_scores else 0.0
        peak_synth = float(np.max(win_scores)) if win_scores else 0.0
        w(f"   AASIST Aggregate  : Mean={mean_synth*100:.2f}% | Peak={peak_synth*100:.2f}%\n\n")

        # 4. Speaker Biometric Embedding
        w(f"[TEST 4] RESEMBLYZER SPEAKER EMBEDDING (256-D GE2E):\n")
        emb = embed_utterance(enc_sess, wav)
        embeddings[name] = emb
        w(f"   Embedding Vector  : Norm={np.linalg.norm(emb):.4f}, First 5 dims: [{', '.join(f'{x:+.3f}' for x in emb[:5])}]\n\n")

        # 5. VOCIS Baseline Calibration & SpoofCheck
        w(f"[TEST 5] VOCIS CORE BASELINE CALIBRATION & SATURATION CHECK:\n")
        if is_owner:
            baseline = mean_synth
            is_saturated = (baseline + SYNTHETIC_BASELINE_MARGIN >= 1.0)
            spoof_check = "UNRELIABLE" if is_saturated else "USABLE"
            eff_synth_thresh = max(SYNTHETIC_HIGH, baseline + SYNTHETIC_BASELINE_MARGIN)
        else:
            baseline = None
            spoof_check = "NO_BASELINE"
            eff_synth_thresh = SYNTHETIC_HIGH

        w(f"   Calibration Role   : {'Enrolled Owner / Profile' if is_owner else 'Incoming Caller'}\n")
        w(f"   Measured Baseline  : {f'{baseline*100:.2f}%' if baseline is not None else 'N/A'}\n")
        w(f"   Spoof Check Status : {spoof_check} ({'Saturation Suppressed' if spoof_check == 'UNRELIABLE' else 'Normal'})\n")
        w(f"   Effective Threshold: {eff_synth_thresh*100:.1f}%\n\n")

        # 6. Groq LLM Semantic & Threat Analysis
        w(f"[TEST 6] GROQ LLM SEMANTIC ANALYSIS (compound-mini):\n")
        payload = {
            'model': 'groq/compound-mini',
            'messages': [
                {'role': 'system', 'content': SYSTEM_PROMPT},
                {'role': 'user', 'content': f'Analyze caller speech: "{text}"'}
            ],
            'temperature': 0.1
        }
        chat_resp = requests.post(
            CHAT_URL,
            headers={'Authorization': f'Bearer {API_KEY}', 'Content-Type': 'application/json'},
            json=payload,
            timeout=30
        )
        llm_content = chat_resp.json()['choices'][0]['message']['content']
        m = re.search(r'\{.*\}', llm_content, re.DOTALL)
        llm_res = json.loads(m.group(0)) if m else {}

        is_scam = llm_res.get('isScam', False)
        category = llm_res.get('scamCategory', 'NONE')
        urgency = llm_res.get('urgencyLevel', 'LOW')
        llm_score = llm_res.get('scamScore', 0)
        explanation = llm_res.get('explanation', '')
        tactics = llm_res.get('coercionTactics', [])

        w(f"   Is Scam Lure       : {is_scam}\n")
        w(f"   Scam Category      : {category}\n")
        w(f"   Urgency Level      : {urgency}\n")
        w(f"   Semantic Score     : {llm_score}/100\n")
        w(f"   Coercion Tactics   : {', '.join(tactics) if tactics else 'None'}\n")
        w(f"   Rationale          : {explanation}\n\n")

        # 7. VOCIS Attack Context Correlation
        w(f"[TEST 7] ATTACK CONTEXT ENGINE CORRELATION (5-Min Temporal Window):\n")
        context_patterns = []
        if "otp" in text.lower() or "six digit" in text.lower():
            context_patterns.append("OTP Authentication Solicitation (+40)")
        if "crime branch" in text.lower() or "police" in text.lower() or "cbi" in text.lower() or "digital arrest" in text.lower():
            context_patterns.append("Law Enforcement / Digital Arrest Threat (+50)")
        if "debit card" in text.lower() or "bank security" in text.lower():
            context_patterns.append("Banking Impersonation Alert (+25)")
        if "courier" in text.lower() or "customs" in text.lower():
            context_patterns.append("Contraband Courier Notice (+35)")

        w(f"   Correlated Patterns: {'; '.join(context_patterns) if context_patterns else 'None (Clean Context)'}\n\n")

        # Store for cross-speaker and final verdict
        results.append({
            'name': name,
            'duration': duration,
            'lang': lang,
            'text': text,
            'is_eng': is_eng,
            'mean_synth': mean_synth,
            'peak_synth': peak_synth,
            'spoof_check': spoof_check,
            'eff_synth_thresh': eff_synth_thresh,
            'is_scam': is_scam,
            'category': category,
            'urgency': urgency,
            'llm_score': llm_score,
            'explanation': explanation,
            'is_owner': is_owner,
            'context_patterns': context_patterns
        })

    # Cross-Speaker Similarity Matrix
    w("========================================================================================\n")
    w("  [TEST 8] CROSS-RECORDING SPEAKER SIMILARITY MATRIX (Cosine Distance):\n")
    w("========================================================================================\n")
    names = [r['name'] for r in results]
    header = f"{'Speaker':<15}" + "".join(f"{n:>14}" for n in names)
    w(header + "\n")
    w("-" * len(header) + "\n")
    sim_matrix = {}
    for n1 in names:
        row = f"{n1:<15}"
        sim_matrix[n1] = {}
        for n2 in names:
            s = float(np.dot(embeddings[n1], embeddings[n2]))
            sim_matrix[n1][n2] = s
            row += f"{s:>14.4f}"
        w(row + "\n")
    w("\n")

    # Final Decision & Evidence Fusion
    w("========================================================================================\n")
    w("  [TEST 9] FINAL VOCIS CORE COMPOSITE EVIDENCE FUSION & RISK VERDICT:\n")
    w("========================================================================================\n\n")

    for r in results:
        name = r['name']
        # Compare against 1234 baseline
        sim_vs_ref = sim_matrix[name]['1234']
        is_eng = r['is_eng']
        spoof_check = r['spoof_check']
        mean_synth = r['mean_synth']
        eff_synth_thresh = r['eff_synth_thresh']

        # VOCIS Fusion rules
        is_spoof_usable = is_eng and (spoof_check != "UNRELIABLE")
        sim_high = sim_vs_ref >= SIMILARITY_HIGH
        sim_low  = sim_vs_ref < SIMILARITY_LOW

        if r['is_owner']:
            # Owner voice enrollment
            vcd_level = "SAFE"
            vcd_reason = "MATCH_SPOOF_CHECK_UNRELIABLE"
            vcd_risk = 0
        elif sim_high:
            if not is_spoof_usable:
                vcd_level = "SAFE"
                vcd_reason = "MATCH_SPOOF_CHECK_UNRELIABLE"
                vcd_risk = 0
            elif mean_synth >= eff_synth_thresh:
                vcd_level = "CRITICAL"
                vcd_reason = "CLONE_SIGNATURE"
                vcd_risk = 95
            elif mean_synth >= SYNTHETIC_ELEVATED:
                vcd_level = "SUSPICIOUS"
                vcd_reason = "POSSIBLE_SYNTHESIS"
                vcd_risk = 60
            else:
                vcd_level = "SAFE"
                vcd_reason = "MATCH_AUTHENTIC"
                vcd_risk = 0
        elif sim_low:
            if is_spoof_usable and mean_synth >= eff_synth_thresh:
                vcd_level = "CRITICAL"
                vcd_reason = "UNKNOWN_SYNTHETIC"
                vcd_risk = 90
            else:
                vcd_level = "SUSPICIOUS"
                vcd_reason = "NOT_CLAIMED_CONTACT"
                vcd_risk = 35
        else:
            vcd_level = "SUSPICIOUS"
            vcd_reason = "BORDERLINE_SIMILARITY"
            vcd_risk = 25

        # Evidence Fusion Score
        total_risk = max(r['llm_score'] if r['is_scam'] else 0, vcd_risk)
        if r['is_scam'] and vcd_level == "CRITICAL":
            total_risk = 98

        if total_risk >= 75:
            verdict_text = "CONFIRMED SCAM / ATTACK"
            action = "SHOW_SECURITY_INTERVENTION / BLOCK_CALL"
        elif total_risk >= 50:
            verdict_text = "HIGH RISK / LIKELY SCAM"
            action = "SHOW_RISK_CARD"
        elif total_risk >= 25:
            verdict_text = "SUSPICIOUS / ELEVATED RISK"
            action = "SHOW_COMPACT_WARNING"
        else:
            verdict_text = "SAFE (BENIGN)"
            action = "MONITOR_ONLY"

        w(f"  RECORDING: {name:<12} | Risk: {total_risk:>3}/100 | Verdict: {verdict_text:<25} | Action: {action}\n")
        w(f"     * Lang: {r['lang'].title():<8} | AASIST: {mean_synth*100:>5.1f}% ({'Usable' if is_spoof_usable else 'Suppressed'}) | Sim vs 1234: {sim_vs_ref:.4f}\n")
        w(f"     * Biometric VCD : {vcd_level} ({vcd_reason})\n")
        w(f"     * LLM Semantic  : {r['category']} (Score: {r['llm_score']}/100, Urgency: {r['urgency']})\n")
        w(f"     * Context Threat: {', '.join(r['context_patterns']) if r['context_patterns'] else 'None'}\n\n")

    w("========================================================================================\n")
    w("                             FULL TEST EXECUTION COMPLETE                               \n")
    w("========================================================================================\n")

if __name__ == '__main__':
    run_all()
