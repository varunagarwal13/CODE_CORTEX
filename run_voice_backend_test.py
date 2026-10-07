import os
import sys
import wave
import numpy as np
import onnxruntime as ort

def softmax(x):
    e_x = np.exp(x - np.max(x))
    return e_x / e_x.sum()

def embed_utterance(samples, encoder_session, in_name):
    width = 25600
    hop = 12800
    if len(samples) < width:
        padded = np.zeros(width, dtype=np.float32)
        padded[:len(samples)] = samples
        inp = np.expand_dims(padded, axis=0)
        out = encoder_session.run(None, {in_name: inp})[0][0]
        norm = np.linalg.norm(out)
        return out / (norm if norm > 0 else 1.0)
    
    partials = []
    start = 0
    while start + width <= len(samples):
        chunk = samples[start:start + width]
        inp = np.expand_dims(chunk, axis=0)
        out = encoder_session.run(None, {in_name: inp})[0][0]
        partials.append(out)
        start += hop
    
    if start - hop + width < len(samples):
        chunk = samples[len(samples) - width:]
        inp = np.expand_dims(chunk, axis=0)
        out = encoder_session.run(None, {in_name: inp})[0][0]
        partials.append(out)
        
    mean_vec = np.mean(partials, axis=0)
    norm = np.linalg.norm(mean_vec)
    return mean_vec / (norm if norm > 0 else 1.0)

def run_test():
    audio_path = r"C:\Users\varun\OneDrive\Desktop\VOCIS\app\src\main\assets\test_sample_16k.wav"
    spoof_model_path = r"C:\Users\varun\OneDrive\Desktop\VOCIS\app\src\main\assets\models\spoof_detector.onnx"
    encoder_model_path = r"C:\Users\varun\OneDrive\Desktop\VOCIS\app\src\main\assets\models\speaker_encoder.onnx"

    print("=" * 65)
    print(" VOCIS LIVE NEURAL ANTI-SPOOF & BIOMETRIC VERIFICATION TEST")
    print("=" * 65)
    print(f"Audio File     : {audio_path}")
    
    with wave.open(audio_path, 'rb') as wf:
        n_channels = wf.getnchannels()
        sample_width = wf.getsampwidth()
        framerate = wf.getframerate()
        n_frames = wf.getnframes()
        raw_data = wf.readframes(n_frames)
    
    duration = n_frames / float(framerate)
    print(f"Sampling Rate  : {framerate} Hz ({sample_width * 8}-bit PCM)")
    print(f"Total Duration : {duration:.2f} seconds ({n_frames} samples)")
    print("-" * 65)

    int16_samples = np.frombuffer(raw_data, dtype=np.int16)
    float_samples = int16_samples.astype(np.float32) / 32768.0

    print("[1] Initializing AASIST Anti-Spoof ONNX Engine...")
    spoof_session = ort.InferenceSession(spoof_model_path)
    spoof_in_name = spoof_session.get_inputs()[0].name

    print("[2] Initializing Resemblyzer GE2E Speaker Encoder...")
    encoder_session = ort.InferenceSession(encoder_model_path)
    encoder_in_name = encoder_session.get_inputs()[0].name
    print("-" * 65)

    # Window slicing: 64600 samples (~4.0375s) with 48000 sample hop (3.0s)
    window_size = 64600
    hop_size = 48000

    windows = []
    start = 0
    while start + window_size <= len(float_samples):
        window = float_samples[start:start + window_size]
        rms = np.sqrt(np.mean(window ** 2))
        windows.append((start, window, rms))
        start += hop_size

    # Handle tail if needed
    if not windows and len(float_samples) >= 25600:
        pad = np.zeros(64600, dtype=np.float32)
        pad[:len(float_samples)] = float_samples
        windows.append((0, pad, np.sqrt(np.mean(float_samples ** 2))))

    print(f"[3] Processing {len(windows)} Acoustic Windows:")

    synthetic_probs = []
    embeddings = []

    for idx, (offset, window, rms) in enumerate(windows):
        sec_start = offset / 16000.0
        sec_end = (offset + window_size) / 16000.0
        
        # 1. AASIST anti-spoof inference [1, 64600]
        input_tensor = np.expand_dims(window, axis=0)
        spoof_out = spoof_session.run(None, {spoof_in_name: input_tensor})[0]
        logits = spoof_out[0]
        probs = softmax(logits)
        prob_synthetic = float(probs[0])
        prob_bonafide = float(probs[1])
        synthetic_probs.append(prob_synthetic)

        # 2. Resemblyzer partial utterance embedding
        emb = embed_utterance(window, encoder_session, encoder_in_name)
        embeddings.append(emb)

        print(f"\n   --- Window #{idx+1} ({sec_start:.2f}s -> {sec_end:.2f}s) ---")
        print(f"   RMS Acoustic Energy : {rms:.5f}")
        print(f"   AASIST Raw Logits   : [{logits[0]:.4f}, {logits[1]:.4f}]")
        print(f"   Bonafide Human Prob : {prob_bonafide * 100:.2f}%")
        print(f"   Synthetic AI Prob   : {prob_synthetic * 100:.2f}%")

    print("\n" + "=" * 65)
    print(" FORENSIC VERDICT & BIOMETRIC ASSESSMENT")
    print("=" * 65)
    
    median_synthetic = float(np.median(synthetic_probs))
    peak_synthetic = float(np.max(synthetic_probs))
    threat_score = int(round(median_synthetic * 100))

    if median_synthetic >= 0.70:
        verdict = "CRITICAL_CLONE_DETECTED (Synthetic / AI Voice)"
        status_label = "RED ALERT - HIGH RISK CLONE"
    elif median_synthetic > 0.35:
        verdict = "UNCERTAIN / ELEVATED RISK"
        status_label = "AMBER - UNCERTAIN RISK"
    else:
        verdict = "SAFE_VERIFIED_AUTHENTIC (Natural Human Speech)"
        status_label = "GREEN - SAFE HUMAN VOICE"

    print(f" Evaluated Threat Score     : {threat_score}%")
    print(f" Peak Synthetic Risk        : {peak_synthetic * 100:.2f}%")
    print(f" Median Synthetic Risk      : {median_synthetic * 100:.2f}%")
    print(f" Human Voice Authenticity   : {(1.0 - median_synthetic) * 100:.2f}%")
    print(f" Biometric Security Status  : [{status_label}]")
    print(f" System Decision Verdict    : {verdict}")
    print(f" Speaker Embedding Vector   : 256-dim unit vector generated")
    print("=" * 65)

if __name__ == '__main__':
    run_test()
