package com.orbit.assistant.agent.evaluation

import com.orbit.assistant.agent.actions.ActionValidationResult
import com.orbit.assistant.agent.actions.AgentActionPlan
import com.orbit.assistant.agent.actions.AgentActionType
import com.orbit.assistant.agent.actions.AgentActionValidator
import com.orbit.assistant.agent.context.AgentContextManager
import com.orbit.assistant.agent.observation.RawUiNode
import com.orbit.assistant.agent.observation.RawUiTree
import com.orbit.assistant.agent.observation.ScreenObservationNormalizer
import com.orbit.assistant.agent.observation.UiBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentContextAndEvaluationTest {

    private val normalizer = ScreenObservationNormalizer()

    @Test
    fun `multi-app navigation maintains bounded context without raw UI trees`() {
        val ctxManager = AgentContextManager()
        ctxManager.initialize(
            goal = "Compare milk on Blinkit and Zepto",
            preferences = mapOf("apps" to "Blinkit,Zepto")
        )

        val apps = listOf("com.grofers.customerapp", "com.zeptoconsumerapp")
        repeat(8) { step ->
            val pkg = apps[step % 2]
            val obs = normalizer.normalize(
                RawUiTree(
                    packageName = pkg,
                    activityName = "Step${step}Activity",
                    nodes = listOf(RawUiNode(step, text = "Item $step", isClickable = true))
                )
            )
            val diff = normalizer.compare(ctxManager.previousObservation(), obs)
            ctxManager.updateObservation(obs, diff)
            ctxManager.recordActionOutcome("TAP(#$step) in $pkg", succeeded = true)
        }

        val state = ctxManager.currentState()
        assertEquals(AgentContextManager.MAX_RECENT_ACTIONS, state.relevantPreviousActions.size)
        assertTrue(state.completedStepSummaries.isNotEmpty())

        val prompt = ctxManager.toCompactContextPrompt("Blinkit=com.grofers.customerapp, Zepto=com.zeptoconsumerapp")
        assertFalse(prompt.contains("UiBounds"))
        assertTrue(prompt.contains("COMPLETED_SUMMARY:"))
    }

    @Test
    fun `failed action recovery increments recoveryAttempts and resets on success`() {
        val ctxManager = AgentContextManager()
        ctxManager.initialize("Order milk")

        ctxManager.recordActionOutcome("TAP(#4)", succeeded = false)
        assertEquals(1, ctxManager.currentState().recoveryAttempts)

        ctxManager.recordActionOutcome("SCROLL(DOWN)", succeeded = false)
        assertEquals(2, ctxManager.currentState().recoveryAttempts)

        ctxManager.recordActionOutcome("TAP(#9)", succeeded = true)
        assertEquals(0, ctxManager.currentState().recoveryAttempts)
    }

    @Test
    fun `evaluation benchmark compares token usage and task success before vs after optimization`() {
        val ctxManager = AgentContextManager()
        ctxManager.initialize("Buy milk on Blinkit and compare on Zepto")

        // Simulate a realistic 6-step multi-app flow with 120 raw nodes per screen
        val steps = listOf(
            "com.grofers.customerapp" to "HomeActivity",
            "com.grofers.customerapp" to "SearchActivity",
            "com.grofers.customerapp" to "SearchActivity", // repeated screen after typing
            "com.zeptoconsumerapp" to "HomeActivity",
            "com.zeptoconsumerapp" to "SearchActivity",
            "com.zeptoconsumerapp" to "CartActivity"
        )

        var rawTotalChars = 0
        var optimizedTotalChars = 0
        var validatedActions = 0

        val rawHistory = mutableListOf<String>()

        steps.forEachIndexed { idx, (pkg, act) ->
            val rawNodes = (0 until 120).map { n ->
                RawUiNode(
                    nodeId = n,
                    text = if (n % 5 == 0) "Product $n ₹${50 + n}" else "Decorative container $n",
                    className = if (n % 5 == 0) "TextView" else "FrameLayout",
                    bounds = UiBounds(0, n * 20, 1080, (n + 1) * 20),
                    isVisibleToUser = (n == 0 || n % 7 != 0),
                    isClickable = n % 10 == 0,
                    isEditable = n == 0,
                    isFocused = n == 0
                )
            }
            val rawTree = RawUiTree(packageName = pkg, activityName = act, nodes = rawNodes)

            // Baseline (before optimization): dump full raw nodes + accumulate entire history
            val rawDump = rawNodes.joinToString("\n") {
                "node=${it.nodeId} cls=${it.className} vis=${it.isVisibleToUser} click=${it.isClickable} text=${it.text} bounds=${it.bounds}"
            }
            rawHistory.add(rawDump)
            rawTotalChars += rawHistory.sumOf { it.length }

            // Optimized: ScreenObservationNormalizer + incremental diff + bounded AgentContextManager
            val normalized = normalizer.normalize(rawTree, maxTokenBudget = 900)
            val diff = normalizer.compare(ctxManager.previousObservation(), normalized)
            ctxManager.updateObservation(normalized, diff)

            val optPrompt = ctxManager.toCompactContextPrompt("Blinkit,Zepto") + "\n" +
                normalizer.toCompactPrompt(normalized, diff)
            optimizedTotalChars += optPrompt.length

            val candidateAction = AgentActionPlan(
                thought = "Select item",
                confidence = 0.9f,
                action = if (idx == steps.lastIndex) AgentActionType.COMPLETE else AgentActionType.TAP,
                nodeId = if (idx == steps.lastIndex) null else 0,
                summary = if (idx == steps.lastIndex) "Comparison ready" else null
            )
            val validation = AgentActionValidator.validate(candidateAction, normalized)
            if (validation is ActionValidationResult.Valid) {
                validatedActions++
                ctxManager.recordActionOutcome("${candidateAction.action}", succeeded = true)
            }
        }

        val rawTokens = rawTotalChars / 4
        val optimizedTokens = optimizedTotalChars / 4
        val savingsPct = ((rawTokens - optimizedTokens).toDouble() / rawTokens) * 100.0

        println("EVALUATION -> Raw tokens: $rawTokens | Optimized tokens: $optimizedTokens | Reduction: ${"%.1f".format(savingsPct)}% | Validated steps: $validatedActions/${steps.size}")
        assertTrue("Expected >70% token reduction, got $savingsPct%", savingsPct > 70.0)
        assertEquals(steps.size, validatedActions)
    }
}
