import os
import sys
import wave
import numpy as np
import onnxruntime as ort

def to_target_dbfs(samples, target_dbfs=-30.0, increase_only=True):
    if len(samples) == 0:
        return samples
    rms = np.sqrt(np.mean(samples ** 2))
    if rms < 1e-10:
        return samples
    dbfs = 20.0 * np.log10(rms)
    delta = target_dbfs - dbfs
    if increase_only and delta < 0:
        return samples
    gain = 10.0 ** (delta / 20.0)
    return samples * gain

def softmax2(logits):
    max_l = np.max(logits)
    exp = np.exp(logits - max_l)
    return exp / np.sum(exp)

def embed_utterance(samples, encoder_session, in_name):
    width = 25600
    hop = 12800
    normalized = to_target_dbfs(samples)
    if len(normalized) < width:
        padded = np.zeros(width, dtype=np.float32)
        padded[:len(normalized)] = normalized
        inp = np.expand_dims(padded, axis=0)
        out = encoder_session.run(None, {in_name: inp})[0][0]
        norm = np.linalg.norm(out)
        return out / (norm if norm > 0 else 1.0)
    
    partials = []
    start = 0
    while start + width <= len(normalized):
        chunk = normalized[start:start + width]
        inp = np.expand_dims(chunk, axis=0)
        out = encoder_session.run(None, {in_name: inp})[0][0]
        partials.append(out)
        start += hop
    
    if start - hop + width < len(normalized):
        chunk = normalized[len(normalized) - width:]
        inp = np.expand_dims(chunk, axis=0)
        out = encoder_session.run(None, {in_name: inp})[0][0]
        partials.append(out)
        
    mean_vec = np.mean(partials, axis=0)
    norm = np.linalg.norm(mean_vec)
    return mean_vec / (norm if norm > 0 else 1.0)

def run_tests():
    wav_path = r"C:\Users\varun\OneDrive\Desktop\VOCIS\app\src\main\assets\1234_16k.wav"
    ref_wav_path = r"C:\Users\varun\OneDrive\Desktop\VOCIS\app\src\main\assets\test_sample_16k.wav"
    spoof_model_path = r"C:\Users\varun\OneDrive\Desktop\VOCIS\app\src\main\assets\models\spoof_detector.onnx"
    encoder_model_path = r"C:\Users\varun\OneDrive\Desktop\VOCIS\app\src\main\assets\models\speaker_encoder.onnx"

    print("=" * 75)
    print(" VOCIS COMPREHENSIVE BIOMETRIC & ANTI-SPOOF FORENSIC TEST SUITE")
    print("=" * 75)
    print(f"Target Voice Sample  : {wav_path}")

    with wave.open(wav_path, 'rb') as wf:
        n_channels = wf.getnchannels()
        sample_width = wf.getsampwidth()
        framerate = wf.getframerate()
        n_frames = wf.getnframes()
        raw_data = wf.readframes(n_frames)
    
    duration = n_frames / float(framerate)
    print(f"Sampling Frequency   : {framerate} Hz ({sample_width * 8}-bit Signed Linear PCM)")
    print(f"Audio Channel Mode   : {'Mono' if n_channels == 1 else 'Stereo'}")
    print(f"Utterance Duration   : {duration:.2f} seconds ({n_frames} total samples)")
    print("-" * 75)

    int16_samples = np.frombuffer(raw_data, dtype=np.int16)
    float_samples = int16_samples.astype(np.float32) / 32768.0

    print("[1] Initializing AASIST Anti-Spoof ONNX Engine...")
    spoof_session = ort.InferenceSession(spoof_model_path)
    spoof_in_name = spoof_session.get_inputs()[0].name
    print("    * Model Loaded: spoof_detector.onnx (AASIST Architecture)")

    print("[2] Initializing Resemblyzer GE2E Speaker Encoder ONNX Engine...")
    encoder_session = ort.InferenceSession(encoder_model_path)
    encoder_in_name = encoder_session.get_inputs()[0].name
    print("    * Model Loaded: speaker_encoder.onnx (Resemblyzer 256-D Centroid)")
    print("-" * 75)

    # Window slicing: 64,600 samples (4.0375s) with 48,000 samples (3.0s) hop
    window_size = 64600
    hop_size = 48000

    windows = []
    start = 0
    while start + window_size <= len(float_samples):
        window = float_samples[start:start + window_size]
        rms = np.sqrt(np.mean(window ** 2))
        windows.append((start, window, rms))
        start += hop_size

    # Tail window
    if not windows and len(float_samples) >= 25600:
        pad = np.zeros(64600, dtype=np.float32)
        pad[:len(float_samples)] = float_samples
        windows.append((0, pad, np.sqrt(np.mean(float_samples ** 2))))
    elif len(float_samples) - (start - hop_size + window_size) >= 16000:
        tail = float_samples[len(float_samples) - window_size:]
        windows.append((len(float_samples) - window_size, tail, np.sqrt(np.mean(tail ** 2))))

    print(f"[3] Processing {len(windows)} Slid Verification Windows (Window=4.04s, Hop=3.00s):")

    synthetic_probs = []
    bonafide_probs = []
    window_embeddings = []

    for idx, (offset, window, rms) in enumerate(windows):
        sec_start = offset / 16000.0
        sec_end = (offset + len(window)) / 16000.0
        
        # 1. AASIST anti-spoof inference
        input_tensor = np.expand_dims(window, axis=0)
        spoof_out = spoof_session.run(None, {spoof_in_name: input_tensor})[0]
        logits = spoof_out[0]
        probs = softmax2(logits)
        
        # In AASIST / VOCIS: index 0 is spoof/synthetic, index 1 is bonafide
        prob_synthetic = float(probs[0])
        prob_bonafide = float(probs[1])
        synthetic_probs.append(prob_synthetic)
        bonafide_probs.append(prob_bonafide)

        # 2. Resemblyzer embedding
        emb = embed_utterance(window, encoder_session, encoder_in_name)
        window_embeddings.append(emb)

        tag = "[AI SPOOF]" if prob_synthetic >= 0.50 else "[BONAFIDE]"
        print(f"\n   Window #{idx+1} [{sec_start:5.2f}s -> {sec_end:5.2f}s] | RMS: {rms:.5f} | Logits: [{logits[0]:6.3f}, {logits[1]:6.3f}]")
        print(f"   --> Synthetic Probability : {prob_synthetic * 100:6.2f}%  |  Bonafide Score: {prob_bonafide * 100:6.2f}%  [{tag}]")

    print("\n" + "=" * 75)
    print(" AGGREGATE BIOMETRIC ASSESSMENT & DECISION MATRIX")
    print("=" * 75)

    median_synthetic = float(np.median(synthetic_probs))
    mean_synthetic = float(np.mean(synthetic_probs))
    peak_synthetic = float(np.max(synthetic_probs))
    min_synthetic = float(np.min(synthetic_probs))

    median_bonafide = float(np.median(bonafide_probs))
    mean_bonafide = float(np.mean(bonafide_probs))

    threat_score = int(round(median_synthetic * 100))
    overall_utterance_embedding = embed_utterance(float_samples, encoder_session, encoder_in_name)

    if median_synthetic >= 0.50:
        verdict = "CRITICAL_UNKNOWN_SYNTHETIC / CRITICAL_CLONE_DETECTED"
        threat_level = "CRITICAL (RED ALERT)"
        confidence_desc = f"Strong acoustic evidence of neural voice cloning / AI text-to-speech synthesis ({threat_score}% synthetic confidence)."
    elif median_synthetic >= 0.30:
        verdict = "SUSPICIOUS_POSSIBLE_SYNTHESIS"
        threat_level = "ELEVATED (AMBER ALERT)"
        confidence_desc = f"Moderate synthetic artifacts detected ({threat_score}%). Borderline acoustic resonance."
    else:
        verdict = "SAFE_VERIFIED_AUTHENTIC"
        threat_level = "LOW RISK (GREEN - SAFE)"
        confidence_desc = f"Organic vocal tract harmonic resonances present ({median_bonafide*100:.1f}% bonafide confidence). No synthetic artifacts."

    print(f" Threat Score (0-100)        : {threat_score}%")
    print(f" Security Threat Level       : [{threat_level}]")
    print(f" Primary System Verdict      : {verdict}")
    print(f" Median Synthetic Risk       : {median_synthetic * 100:.2f}%")
    print(f" Mean Synthetic Risk         : {mean_synthetic * 100:.2f}%")
    print(f" Peak Window Synthetic Risk  : {peak_synthetic * 100:.2f}%")
    print(f" Minimum Window Synthetic    : {min_synthetic * 100:.2f}%")
    print(f" Median Bonafide Human Score : {median_bonafide * 100:.2f}%")
    print(f" Forensic Assessment Notes   : {confidence_desc}")
    print("-" * 75)

    # Cross-reference with the earlier WhatsApp voice clip
    if os.path.exists(ref_wav_path):
        with wave.open(ref_wav_path, 'rb') as rwf:
            r_frames = rwf.getnframes()
            r_data = rwf.readframes(r_frames)
        r_int16 = np.frombuffer(r_data, dtype=np.int16)
        r_float = r_int16.astype(np.float32) / 32768.0
        ref_embedding = embed_utterance(r_float, encoder_session, encoder_in_name)
        sim_to_ref = float(np.dot(overall_utterance_embedding, ref_embedding))
        print(f" [Cross-Clip Speaker Similarity]")
        print(f" Cosine Similarity with 'WhatsApp Audio': {sim_to_ref:.4f} (Threshold >= 0.75 for same speaker)")
        if sim_to_ref >= 0.75:
            print(" Result: MATCH -> Likely the SAME speaker / cloned voice model!")
        elif sim_to_ref < 0.50:
            print(" Result: MISMATCH -> DIFFERENT speaker identities.")
        else:
            print(" Result: BORDERLINE -> Inconclusive speaker match.")
    print("=" * 75)

if __name__ == '__main__':
    run_tests()
