package com.vocis.speech.llm

import com.vocis.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cloud LLM client interfacing with Groq API (groq/compound-mini).
 *
 * Multilingual-aware: handles Hindi, Hinglish, Tamil, Telugu, and other Indian
 * languages that appear in real-world scam calls. Language detection is handled
 * upstream by Whisper (auto-detect mode) and passed in via [detectedLanguage].
 */
class GroqLlmClient(
    private val apiKey: String? = try {
        BuildConfig.GROQ_API_KEY.ifBlank { System.getenv("GROQ_API_KEY") ?: System.getProperty("GROQ_API_KEY") }
    } catch (e: Throwable) {
        System.getenv("GROQ_API_KEY") ?: System.getProperty("GROQ_API_KEY")
    },
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /**
         * English-only languages where AASIST (trained on ASVspoof-2019) is reliable.
         * For all other languages, the synthetic score is flagged as unreliable.
         */
        private val AASIST_RELIABLE_LANGUAGES = setOf("en", "english")

        private const val SYSTEM_PROMPT = """
            You are a real-time telecommunication scam analysis system.
            Analyze the provided conversational speech transcript.
            The transcript may be in English, Hindi, Hinglish, or any Indian regional language.
            Output ONLY valid JSON with this exact schema:
            {
              "isScam": boolean,
              "scamCategory": "NONE" | "DIGITAL_ARREST" | "OTP_THEFT" | "BANKING_IMPERSONATION" | "REMOTE_ACCESS" | "COURIER_FRAUD" | "COERCIVE_AUTHORITY",
              "urgencyLevel": "LOW" | "MEDIUM" | "HIGH" | "EXTREME",
              "coercionTactics": ["string"],
              "confidence": float between 0.0 and 1.0,
              "explanation": "concise rationale in English"
            }
        """
    }

    /**
     * Sends transcript to Groq for semantic scam analysis.
     *
     * @param transcript  Speech transcript text (any language).
     * @param detectedLanguage  BCP-47 language code from Whisper auto-detect (e.g. "en", "hi").
     *                          Null when language is unknown (offline Vosk path).
     * @return [SemanticAnalysisResult] with [SemanticAnalysisResult.isAasistReliable] set
     *         based on whether AASIST is trustworthy for this language.
     *         Returns null on missing API key, network timeout, or parse failure.
     */
    fun analyzeTranscript(
        transcript: String,
        detectedLanguage: String? = null
    ): SemanticAnalysisResult? {
        val effectiveApiKey = apiKey?.ifBlank { null }
            ?: try { BuildConfig.GROQ_API_KEY.ifBlank { null } } catch (_: Throwable) { null }
            ?: System.getenv("GROQ_API_KEY")
            ?: System.getProperty("GROQ_API_KEY")

        if (effectiveApiKey.isNullOrBlank()) {
            return null
        }

        // Determine AASIST reliability from detected language
        val aasistReliable = detectedLanguage == null ||
            detectedLanguage.lowercase().take(2) in AASIST_RELIABLE_LANGUAGES ||
            detectedLanguage.lowercase() in AASIST_RELIABLE_LANGUAGES

        return try {
            val payload = buildJsonObject {
                put("model", "groq/compound-mini")
                putJsonObject("response_format") {
                    put("type", "json_object")
                }
                putJsonArray("messages") {
                    add(buildJsonObject {
                        put("role", "system")
                        put("content", SYSTEM_PROMPT.trimIndent())
                    })
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", transcript)
                    })
                }
                put("temperature", 0.1)
            }.toString()

            val request = Request.Builder()
                .url(GROQ_URL)
                .addHeader("Authorization", "Bearer $effectiveApiKey")
                .addHeader("Content-Type", "application/json")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                val bodyStr = response.body?.string() ?: return null
                val rootJson = JSONObject(bodyStr)
                val choices = rootJson.optJSONArray("choices") ?: return null
                if (choices.length() == 0) return null
                val messageObj = choices.getJSONObject(0).getJSONObject("message")
                val contentStr = messageObj.getString("content")

                parseAnalysisResponse(contentStr, detectedLanguage, aasistReliable)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseAnalysisResponse(
        jsonContent: String,
        detectedLanguage: String?,
        aasistReliable: Boolean
    ): SemanticAnalysisResult? {
        return try {
            val obj = JSONObject(jsonContent)
            val isScam = obj.optBoolean("isScam", false)
            val catStr = obj.optString("scamCategory", "NONE")
            val cat = try { ScamCategory.valueOf(catStr) } catch (_: Exception) { ScamCategory.NONE }
            val urgStr = obj.optString("urgencyLevel", "LOW")
            val urg = try { UrgencyLevel.valueOf(urgStr) } catch (_: Exception) { UrgencyLevel.LOW }
            val conf = obj.optDouble("confidence", 0.5).toFloat()
            val expl = obj.optString("explanation", "")

            val tacticsList = mutableListOf<String>()
            val tacticsArr = obj.optJSONArray("coercionTactics")
            if (tacticsArr != null) {
                for (i in 0 until tacticsArr.length()) {
                    tacticsList.add(tacticsArr.getString(i))
                }
            }

            SemanticAnalysisResult(
                isScam = isScam,
                scamCategory = cat,
                urgencyLevel = urg,
                coercionTactics = tacticsList,
                confidence = conf,
                rawExplanation = expl,
                isFromFallback = false,
                detectedLanguage = detectedLanguage,
                isAasistReliable = aasistReliable
            )
        } catch (_: Exception) {
            null
        }
    }
}
