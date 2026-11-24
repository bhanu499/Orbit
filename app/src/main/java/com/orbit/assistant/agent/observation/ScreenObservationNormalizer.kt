package com.orbit.assistant.agent.observation

import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class UiBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)

    fun toCompactString(): String = "[$left,$top,$right,$bottom]"
}

data class RawUiNode(
    val nodeId: Int,
    val text: String = "",
    val contentDescription: String = "",
    val viewId: String = "",
    val className: String = "",
    val bounds: UiBounds = UiBounds(0, 0, 100, 50),
    val isVisibleToUser: Boolean = true,
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val isSelected: Boolean = false,
    val isEnabled: Boolean = true,
    val isFocused: Boolean = false,
    val parentContext: String? = null
)

data class RawUiTree(
    val packageName: String,
    val activityName: String = "",
    val nodes: List<RawUiNode>
)

data class NormalizedElement(
    val id: Int,
    val text: String,
    val bounds: UiBounds,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val selected: Boolean,
    val enabled: Boolean,
    val focused: Boolean,
    val parentContext: String? = null
) {
    val signature: String
        get() = "$id|$text|$clickable|$editable|$scrollable|$selected|$enabled|$focused"

    fun toCompactLine(): String {
        val flags = buildList {
            if (clickable) add("tap")
            if (editable) add("edit")
            if (scrollable) add("scroll")
            if (selected) add("sel")
            if (!enabled) add("dis")
            if (focused) add("focus")
        }.joinToString(",")
        val ctx = parentContext?.takeIf { it.isNotBlank() }?.let { " {$it}" }.orEmpty()
        return "[$id] \"$text\" ($flags) ${bounds.toCompactString()}$ctx"
    }
}

data class NormalizedScreenObservation(
    val packageName: String,
    val activityName: String,
    val screenId: String,
    val elements: List<NormalizedElement>,
    val totalRawNodes: Int,
    val prunedNodes: Int,
    val estimatedTokens: Int
)

enum class ScreenChangeState {
    NEW_SCREEN,
    UPDATED,
    UNCHANGED
}

data class IncrementalScreenDiff(
    val screenState: ScreenChangeState,
    val addedElements: List<NormalizedElement>,
    val removedElements: List<NormalizedElement>,
    val changedElements: List<NormalizedElement>,
    val currentFocus: NormalizedElement?
)

@Singleton
class ScreenObservationNormalizer @Inject constructor() {

    companion object {
        const val DEFAULT_MAX_TOKEN_BUDGET = 900
        private val DECORATIVE_CLASSES = setOf(
            "View", "Space", "ImageView", "Divider", "FrameLayout", "LinearLayout", "RelativeLayout"
        )
    }

    fun normalize(
        rawTree: RawUiTree,
        maxTokenBudget: Int = DEFAULT_MAX_TOKEN_BUDGET
    ): NormalizedScreenObservation {
        val seenDecorativeLabels = mutableSetOf<String>()
        val filtered = mutableListOf<NormalizedElement>()

        for (node in rawTree.nodes) {
            if (!node.isVisibleToUser) continue
            if (node.bounds.width <= 0 || node.bounds.height <= 0) continue

            val label = node.text.trim()
                .ifBlank { node.contentDescription.trim() }
                .ifBlank { node.viewId.substringAfterLast('/').trim() }
                .take(72)

            val isInteractive = node.isClickable || node.isEditable || node.isScrollable || node.isFocused
            val shortClass = node.className.substringAfterLast('.')

            // Exclude irrelevant decorative elements & empty non-interactive containers
            if (!isInteractive && label.isBlank()) continue
            if (!isInteractive && shortClass in DECORATIVE_CLASSES && label.length < 2) continue

            // Exclude duplicate non-interactive labels
            if (!isInteractive && !seenDecorativeLabels.add(label.lowercase())) continue

            filtered.add(
                NormalizedElement(
                    id = node.nodeId,
                    text = label.ifBlank { shortClass.ifBlank { "element" } },
                    bounds = node.bounds,
                    clickable = node.isClickable,
                    editable = node.isEditable,
                    scrollable = node.isScrollable,
                    selected = node.isSelected,
                    enabled = node.isEnabled,
                    focused = node.isFocused,
                    parentContext = node.parentContext?.take(36)
                )
            )
        }

        // Prioritize focused, editable, clickable, then visible text within token budget
        val prioritized = filtered.sortedWith(
            compareByDescending<NormalizedElement> { it.focused }
                .thenByDescending { it.editable }
                .thenByDescending { it.clickable }
                .thenByDescending { it.scrollable }
                .thenBy { it.id }
        )

        val charBudget = (maxTokenBudget.coerceAtLeast(150)) * 4
        val headerOverhead = 80
        var usedChars = headerOverhead
        val budgeted = mutableListOf<NormalizedElement>()

        for (el in prioritized) {
            val lineLen = el.toCompactLine().length + 1
            if (usedChars + lineLen > charBudget && budgeted.isNotEmpty()) break
            budgeted.add(el)
            usedChars += lineLen
        }

        val finalElements = budgeted.sortedBy { it.id }
        val screenId = computeScreenId(rawTree.packageName, rawTree.activityName, finalElements)

        return NormalizedScreenObservation(
            packageName = rawTree.packageName,
            activityName = rawTree.activityName.ifBlank { "unknown" },
            screenId = screenId,
            elements = finalElements,
            totalRawNodes = rawTree.nodes.size,
            prunedNodes = (rawTree.nodes.size - finalElements.size).coerceAtLeast(0),
            estimatedTokens = ((usedChars + 3) / 4).coerceAtLeast(1)
        )
    }

    fun compare(
        previous: NormalizedScreenObservation?,
        current: NormalizedScreenObservation
    ): IncrementalScreenDiff {
        val currentFocus = current.elements.firstOrNull { it.focused }
        if (previous == null || previous.packageName != current.packageName || previous.activityName != current.activityName) {
            return IncrementalScreenDiff(
                screenState = ScreenChangeState.NEW_SCREEN,
                addedElements = current.elements,
                removedElements = emptyList(),
                changedElements = emptyList(),
                currentFocus = currentFocus
            )
        }

        val prevById = previous.elements.associateBy { it.id }
        val currById = current.elements.associateBy { it.id }

        val added = current.elements.filter { it.id !in prevById }
        val removed = previous.elements.filter { it.id !in currById }
        val changed = current.elements.filter { el ->
            val old = prevById[el.id]
            old != null && old.signature != el.signature
        }

        val state = when {
            added.isEmpty() && removed.isEmpty() && changed.isEmpty() -> ScreenChangeState.UNCHANGED
            added.size + removed.size > (current.elements.size / 2).coerceAtLeast(3) -> ScreenChangeState.NEW_SCREEN
            else -> ScreenChangeState.UPDATED
        }

        return IncrementalScreenDiff(
            screenState = state,
            addedElements = added,
            removedElements = removed,
            changedElements = changed,
            currentFocus = currentFocus
        )
    }

    fun toCompactPrompt(
        observation: NormalizedScreenObservation,
        diff: IncrementalScreenDiff
    ): String = buildString {
        appendLine("APP=${observation.packageName} ACT=${observation.activityName} SCR=${observation.screenId} STATE=${diff.screenState.name}")
        diff.currentFocus?.let {
            appendLine("FOCUS=${it.toCompactLine()}")
        }
        when (diff.screenState) {
            ScreenChangeState.UNCHANGED -> {
                appendLine("DELTA=UNCHANGED (${observation.elements.size} elements active; same screen as previous step)")
                // Include top 8 interactive elements as compact anchors
                observation.elements.filter { it.clickable || it.editable }.take(8).forEach {
                    appendLine(it.toCompactLine())
                }
            }
            ScreenChangeState.UPDATED -> {
                if (diff.addedElements.isNotEmpty()) {
                    appendLine("ADDED:")
                    diff.addedElements.take(12).forEach { appendLine("+ ${it.toCompactLine()}") }
                }
                if (diff.changedElements.isNotEmpty()) {
                    appendLine("CHANGED:")
                    diff.changedElements.take(10).forEach { appendLine("~ ${it.toCompactLine()}") }
                }
                if (diff.removedElements.isNotEmpty()) {
                    appendLine("REMOVED_IDS=${diff.removedElements.joinToString(",") { it.id.toString() }}")
                }
                appendLine("INTERACTIVE:")
                observation.elements.filter { it.clickable || it.editable || it.scrollable }.forEach {
                    appendLine(it.toCompactLine())
                }
            }
            ScreenChangeState.NEW_SCREEN -> {
                observation.elements.forEach { appendLine(it.toCompactLine()) }
            }
        }
    }.trim()

    private fun computeScreenId(
        pkg: String,
        activity: String,
        elements: List<NormalizedElement>
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val basis = "$pkg|$activity|${elements.take(10).joinToString(";") { "${it.id}:${it.text.take(16)}" }}"
        val hash = digest.digest(basis.toByteArray())
        return hash.take(4).joinToString("") { "%02x".format(it) }
    }
}
