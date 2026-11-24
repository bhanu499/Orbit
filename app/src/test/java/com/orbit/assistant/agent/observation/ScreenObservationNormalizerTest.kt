package com.orbit.assistant.agent.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenObservationNormalizerTest {

    private val normalizer = ScreenObservationNormalizer()

    @Test
    fun `small UI tree prunes hidden and decorative nodes while preserving interactive elements`() {
        val raw = RawUiTree(
            packageName = "com.zeptoconsumerapp",
            activityName = "SearchActivity",
            nodes = listOf(
                RawUiNode(0, text = "", className = "android.widget.FrameLayout", isVisibleToUser = true),
                RawUiNode(1, text = "Hidden promo", isVisibleToUser = false, isClickable = true),
                RawUiNode(2, text = "Search for milk", isEditable = true, isFocused = true, isClickable = true),
                RawUiNode(3, text = "Amul Taaza 1L", isClickable = false),
                RawUiNode(4, text = "Amul Taaza 1L", isClickable = false), // duplicate decorative label
                RawUiNode(5, text = "Add", isClickable = true, parentContext = "Amul Taaza 1L")
            )
        )

        val normalized = normalizer.normalize(raw)
        assertEquals("com.zeptoconsumerapp", normalized.packageName)
        assertEquals("SearchActivity", normalized.activityName)
        assertFalse(normalized.elements.any { it.text == "Hidden promo" })
        assertEquals(1, normalized.elements.count { it.text == "Amul Taaza 1L" })
        assertTrue(normalized.elements.any { it.id == 2 && it.editable && it.focused })
        assertTrue(normalized.elements.any { it.id == 5 && it.clickable && it.parentContext == "Amul Taaza 1L" })
    }

    @Test
    fun `large UI tree enforces maximum observation token budget`() {
        val nodes = (0 until 200).map { i ->
            RawUiNode(
                nodeId = i,
                text = "Product item #$i with a moderately long description and price ₹${40 + i}",
                isClickable = i % 2 == 0,
                isVisibleToUser = true,
                bounds = UiBounds(0, i * 40, 1000, (i + 1) * 40)
            )
        }
        val raw = RawUiTree(packageName = "com.grofers.customerapp", activityName = "CatalogActivity", nodes = nodes)

        val normalized = normalizer.normalize(raw, maxTokenBudget = 350)
        assertTrue("Expected estimated tokens <= 350, got ${normalized.estimatedTokens}", normalized.estimatedTokens <= 350)
        assertTrue("Expected many nodes pruned", normalized.prunedNodes > 100)
        assertTrue(normalized.elements.isNotEmpty())
    }

    @Test
    fun `repeated screen observations produce UNCHANGED incremental diff`() {
        val raw = RawUiTree(
            packageName = "com.zeptoconsumerapp",
            activityName = "CartActivity",
            nodes = listOf(
                RawUiNode(1, text = "Cart (2 items)", isClickable = false),
                RawUiNode(2, text = "Proceed to Pay", isClickable = true, isFocused = true)
            )
        )
        val first = normalizer.normalize(raw)
        val second = normalizer.normalize(raw)
        val diff = normalizer.compare(first, second)

        assertEquals(ScreenChangeState.UNCHANGED, diff.screenState)
        assertTrue(diff.addedElements.isEmpty())
        assertTrue(diff.removedElements.isEmpty())
        assertTrue(diff.changedElements.isEmpty())
        assertEquals(2, diff.currentFocus?.id)

        val prompt = normalizer.toCompactPrompt(second, diff)
        assertTrue(prompt.contains("DELTA=UNCHANGED"))
    }

    @Test
    fun `changed screen after action detects added, removed, and changed elements`() {
        val before = normalizer.normalize(
            RawUiTree(
                packageName = "com.zeptoconsumerapp",
                activityName = "SearchActivity",
                nodes = listOf(
                    RawUiNode(1, text = "Search", isEditable = true, isFocused = true),
                    RawUiNode(2, text = "Add", isClickable = true),
                    RawUiNode(3, text = "Empty banner", isClickable = false)
                )
            )
        )
        val after = normalizer.normalize(
            RawUiTree(
                packageName = "com.zeptoconsumerapp",
                activityName = "SearchActivity",
                nodes = listOf(
                    RawUiNode(1, text = "Search", isEditable = true, isFocused = false),
                    RawUiNode(2, text = "Added (1)", isClickable = true, isSelected = true),
                    RawUiNode(4, text = "View Cart", isClickable = true, isFocused = true)
                )
            )
        )

        val diff = normalizer.compare(before, after)
        assertEquals(ScreenChangeState.UPDATED, diff.screenState)
        assertEquals(listOf(4), diff.addedElements.map { it.id })
        assertEquals(listOf(3), diff.removedElements.map { it.id })
        assertTrue(diff.changedElements.any { it.id == 2 })
        assertEquals(4, diff.currentFocus?.id)
    }
}
