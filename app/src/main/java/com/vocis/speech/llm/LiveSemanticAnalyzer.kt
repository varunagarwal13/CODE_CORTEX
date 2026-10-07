package com.vocis.speech.llm

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * Batches speech transcript chunks over a rolling window and coordinates
 * semantic scam analysis with automatic local heuristic fallback.
 *
 * Multilingual support: Whisper auto-detects the language of the call and
 * passes the BCP-47 code here. This is forwarded to [GroqLlmClient] which
 * sets [SemanticAnalysisResult.isAasistReliable] = false for non-English
 * languages, suppressing the unreliable AASIST score in the fusion engine.
 */
class LiveSemanticAnalyzer(
    private val groqClient: GroqLlmClient = GroqLlmClient(apiKey = null),
    private val localClassifier: LocalHeuristicScamClassifier = LocalHeuristicScamClassifier(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val transcriptBuffer = StringBuilder()

    /** BCP-47 language code detected by Whisper (e.g. "en", "hi"). Null until first detection. */
    @Volatile private var detectedLanguage: String? = null

    private val _analysisResults = MutableSharedFlow<SemanticAnalysisResult>(replay = 1, extraBufferCapacity = 16)
    val analysisResults: SharedFlow<SemanticAnalysisResult> = _analysisResults.asSharedFlow()

    /**
     * Appends newly transcribed speech tokens.
     */
    fun appendTranscript(token: String) {
        synchronized(transcriptBuffer) {
            transcriptBuffer.append(" ").append(token)
        }
    }

    /**
     * Updates the detected language from Whisper auto-detection.
     * Call this each time Whisper returns a language code for the current session.
     *
     * @param language BCP-47 language code (e.g. "en", "hi", "ta"). Null to clear.
     */
    fun updateDetectedLanguage(language: String?) {
        detectedLanguage = language
    }

    /**
     * Executes analysis on the accumulated transcript window.
     * Tries Groq Cloud LLM first (with detected language for AASIST reliability);
     * gracefully falls back to local regex classifier on failure or missing API key.
     */
    suspend fun analyzeCurrentWindow(): SemanticAnalysisResult = withContext(ioDispatcher) {
        val currentText = synchronized(transcriptBuffer) {
            transcriptBuffer.toString().trim()
        }
        val lang = detectedLanguage

        if (currentText.isBlank()) {
            return@withContext SemanticAnalysisResult(
                isScam = false,
                scamCategory = ScamCategory.NONE,
                urgencyLevel = UrgencyLevel.LOW,
                confidence = 1.0f,
                rawExplanation = "No speech detected",
                detectedLanguage = lang,
                // AASIST is unreliable if we know the language is non-English
                isAasistReliable = lang == null || lang.lowercase().take(2) == "en"
            )
        }

        // 1. Try Groq Cloud LLM (passes language for AASIST reliability gating)
        val groqResult = groqClient.analyzeTranscript(currentText, lang)

        // 2. Fall back to local pattern heuristic if Groq fails or API key is absent
        val finalResult = groqResult ?: localClassifier.classify(currentText).copy(
            detectedLanguage = lang,
            isAasistReliable = lang == null || lang.lowercase().take(2) == "en"
        )

        _analysisResults.tryEmit(finalResult)
        finalResult
    }

    /**
     * Clears transcript buffer and language state for a new call session.
     */
    fun reset() {
        synchronized(transcriptBuffer) {
            transcriptBuffer.clear()
        }
        detectedLanguage = null
    }
}
