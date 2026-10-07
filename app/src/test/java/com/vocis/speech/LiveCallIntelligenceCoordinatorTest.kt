package com.vocis.speech

import com.vocis.speech.asr.MockSpeechRecognizerBridge
import com.vocis.speech.llm.GroqLlmClient
import com.vocis.speech.llm.LiveSemanticAnalyzer
import com.vocis.speech.llm.LocalHeuristicScamClassifier
import com.vocis.speech.llm.ScamCategory
import com.vocis.vcd.domain.VcdVerdict
import com.vocis.vcd.domain.VcdVerificationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveCallIntelligenceCoordinatorTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun testCoordinatorFusesSpeechAndVcd() = testScope.runTest {
        val asrBridge = MockSpeechRecognizerBridge()
        val semanticAnalyzer = LiveSemanticAnalyzer(
            groqClient = GroqLlmClient(apiKey = null),
            localClassifier = LocalHeuristicScamClassifier(),
            ioDispatcher = testDispatcher
        )
        val coordinator = LiveCallIntelligenceCoordinator(
            asrBridge = asrBridge,
            semanticAnalyzer = semanticAnalyzer,
            scope = this
        )

        coordinator.startSession()
        advanceUntilIdle()

        // 1. Emit ASR transcripts
        asrBridge.emitMockText("This is CBI Mumbai police department.")
        advanceUntilIdle()

        assertTrue(coordinator.state.value.fullTranscript.contains("CBI Mumbai police"))

        // Run semantic analysis
        semanticAnalyzer.analyzeCurrentWindow()
        advanceUntilIdle()

        val stateAfterSpeech = coordinator.state.value
        assertTrue(stateAfterSpeech.isScamLureDetected)
        assertEquals(ScamCategory.DIGITAL_ARREST, stateAfterSpeech.latestSemanticAnalysis?.scamCategory)

        // 2. Ingest VCD Voice Clone verdict
        val cloneResult = VcdVerificationResult(
            verdict = VcdVerdict.CRITICAL_CLONE_DETECTED,
            similarity = 0.88f,
            syntheticProbability = 0.82f,
            dynamicThreshold = 0.55f,
            baselineSynthetic = 0.15f,
            isReliable = true
        )
        coordinator.onVcdResult(cloneResult)

        val finalState = coordinator.state.value
        assertTrue(finalState.isCriticalVoiceClone)
        assertEquals(VcdVerdict.CRITICAL_CLONE_DETECTED, finalState.latestVcdResult?.verdict)

        coordinator.reset()
        assertEquals("", coordinator.state.value.fullTranscript)
    }
}
