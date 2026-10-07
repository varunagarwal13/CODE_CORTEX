package com.vocis.digitalarrest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DigitalArrestControllerTest {

    private lateinit var controller: DigitalArrestController

    @Before
    fun setUp() {
        controller = DigitalArrestController(familyAlertDispatcher = null)
    }

    @Test
    fun `test initial state starts at Phase 1 ALERT_DETECTED`() {
        val state = controller.state.value
        assertEquals(DigitalArrestPhase.ALERT_DETECTED, state.currentPhase)
        assertEquals(1, state.phaseIndex)
    }

    @Test
    fun `test complete 10-phase progression through state machine`() {
        val expectedPhases = listOf(
            DigitalArrestPhase.ALERT_DETECTED,
            DigitalArrestPhase.CALL_ISOLATION_ASSESSMENT,
            DigitalArrestPhase.AUTHORITY_CLAIM_VERIFICATION,
            DigitalArrestPhase.LEGAL_PROCEDURE_EDUCATION,
            DigitalArrestPhase.FINANCIAL_COERCION_CHECK,
            DigitalArrestPhase.EVIDENCE_COLLECTION,
            DigitalArrestPhase.RULE_EVALUATION,
            DigitalArrestPhase.FAMILY_SOS_CONFIRMATION,
            DigitalArrestPhase.FORENSIC_REPORT_GENERATION,
            DigitalArrestPhase.DEFENSE_COMPLETED
        )

        assertEquals(expectedPhases[0], controller.state.value.currentPhase)

        for (i in 1 until expectedPhases.size) {
            val next = controller.nextPhase()
            assertEquals("Phase index must increment sequentially", i + 1, controller.state.value.phaseIndex)
            assertEquals(expectedPhases[i], next)
        }

        // Boundary check: nextPhase beyond 10 stays at Phase 10
        val capped = controller.nextPhase()
        assertEquals(DigitalArrestPhase.DEFENSE_COMPLETED, capped)
        assertEquals(10, controller.state.value.phaseIndex)
    }

    @Test
    fun `test step rollback with previousPhase`() {
        controller.nextPhase() // Phase 2
        controller.nextPhase() // Phase 3
        assertEquals(DigitalArrestPhase.AUTHORITY_CLAIM_VERIFICATION, controller.state.value.currentPhase)
        assertEquals(3, controller.state.value.phaseIndex)

        val prev = controller.previousPhase() // Back to Phase 2
        assertEquals(DigitalArrestPhase.CALL_ISOLATION_ASSESSMENT, prev)
        assertEquals(2, controller.state.value.phaseIndex)

        controller.previousPhase() // Back to Phase 1
        assertEquals(DigitalArrestPhase.ALERT_DETECTED, controller.state.value.currentPhase)
        assertEquals(1, controller.state.value.phaseIndex)

        // Boundary check: previousPhase at Phase 1 stays at Phase 1
        controller.previousPhase()
        assertEquals(DigitalArrestPhase.ALERT_DETECTED, controller.state.value.currentPhase)
        assertEquals(1, controller.state.value.phaseIndex)
    }

    @Test
    fun `test adding claim updates collected claims and re-evaluates rules`() {
        controller.addClaim("DCP cyber cell Mumbai calling")
        assertEquals(1, controller.state.value.collectedClaims.size)
        assertNotNull(controller.state.value.ruleReport)

        val rule1 = controller.state.value.ruleReport!!.rules.first { it.ruleId == "RULE_01" }
        assertTrue("Rule 1 should trigger on DCP cyber cell claim", rule1.isViolated)

        controller.addClaim("Lock the door and stay on video call")
        assertEquals(2, controller.state.value.collectedClaims.size)
        val rule2 = controller.state.value.ruleReport!!.rules.first { it.ruleId == "RULE_02" }
        assertTrue("Rule 2 should trigger on video confinement claim", rule2.isViolated)
    }

    @Test
    fun `test reset restores clean state`() {
        controller.addClaim("Test extortion claim")
        controller.nextPhase()
        controller.confirmSosDispatched()

        assertTrue(controller.state.value.isSosDispatched)
        assertEquals(2, controller.state.value.phaseIndex)

        controller.reset()

        val resetState = controller.state.value
        assertEquals(DigitalArrestPhase.ALERT_DETECTED, resetState.currentPhase)
        assertEquals(1, resetState.phaseIndex)
        assertTrue(resetState.collectedClaims.isEmpty())
        assertTrue(!resetState.isSosDispatched)
    }
}
