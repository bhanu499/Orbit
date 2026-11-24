package com.orbit.assistant.agent.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.orbit.assistant.agent.observation.RawUiNode
import com.orbit.assistant.agent.observation.RawUiTree
import com.orbit.assistant.agent.observation.UiBounds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UiNodeSnapshot(
    val index: Int,
    val text: String,
    val contentDescription: String,
    val viewId: String,
    val className: String,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean
) {
    fun toCompactLine(): String {
        val flags = buildList {
            if (isClickable) add("click")
            if (isEditable) add("edit")
            if (isScrollable) add("scroll")
        }.joinToString(",")
        val label = text.ifBlank { contentDescription }.ifBlank { viewId.substringAfterLast('/') }
        return "[$index] \"$label\" ($flags) @(${bounds.centerX()},${bounds.centerY()})"
    }
}

class OrbitAccessibilityService : AccessibilityService() {

    private var cachedNodes: MutableMap<Int, AccessibilityNodeInfo> = mutableMapOf()
    @Volatile private var lastActivityName: String = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        _instance.value = this
        OrbitKeepAliveService.start(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.className?.toString()?.takeIf { it.contains('.') }?.let {
                lastActivityName = it.substringAfterLast('.')
            }
        }
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (_instance.value === this) {
            _instance.value = null
        }
        clearCachedNodes()
        OrbitKeepAliveService.stop(applicationContext)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (_instance.value === this) {
            _instance.value = null
        }
        clearCachedNodes()
        OrbitKeepAliveService.stop(applicationContext)
        super.onDestroy()
    }

    fun captureRawUiTree(maxNodes: Int = 140): RawUiTree {
        val root = rootInActiveWindow
            ?: windows?.firstOrNull { it.isActive }?.root
            ?: return RawUiTree(packageName = "", activityName = lastActivityName, nodes = emptyList())

        val pkg = root.packageName?.toString().orEmpty()
        clearCachedNodes()
        val rawNodes = mutableListOf<RawUiNode>()

        fun traverse(node: AccessibilityNodeInfo?, depth: Int, parentLabel: String?) {
            if (node == null || depth > 20 || rawNodes.size >= maxNodes) return
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val viewId = node.viewIdResourceName.orEmpty()
            val rect = Rect()
            node.getBoundsInScreen(rect)

            val id = rawNodes.size
            cachedNodes[id] = node
            rawNodes.add(
                RawUiNode(
                    nodeId = id,
                    text = text,
                    contentDescription = desc,
                    viewId = viewId,
                    className = node.className?.toString().orEmpty(),
                    bounds = UiBounds(rect.left, rect.top, rect.right, rect.bottom),
                    isVisibleToUser = node.isVisibleToUser,
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    isScrollable = node.isScrollable,
                    isSelected = node.isSelected,
                    isEnabled = node.isEnabled,
                    isFocused = node.isFocused,
                    parentContext = parentLabel
                )
            )

            val nextParentLabel = text.ifBlank { desc }.take(28).ifBlank { parentLabel }
            for (i in 0 until node.childCount) {
                traverse(node.getChild(i), depth + 1, nextParentLabel)
            }
        }

        traverse(root, 0, null)
        return RawUiTree(
            packageName = pkg,
            activityName = lastActivityName,
            nodes = rawNodes
        )
    }

    fun captureUiTree(maxNodes: Int = 75): Pair<String, List<UiNodeSnapshot>> {
        val raw = captureRawUiTree(maxNodes)
        val snapshots = raw.nodes
            .filter { it.isVisibleToUser && it.bounds.width > 0 && it.bounds.height > 0 }
            .map {
                UiNodeSnapshot(
                    index = it.nodeId,
                    text = it.text.take(80),
                    contentDescription = it.contentDescription.take(80),
                    viewId = it.viewId,
                    className = it.className.substringAfterLast('.'),
                    bounds = Rect(it.bounds.left, it.bounds.top, it.bounds.right, it.bounds.bottom),
                    isClickable = it.isClickable,
                    isEditable = it.isEditable,
                    isScrollable = it.isScrollable
                )
            }
        return raw.packageName to snapshots
    }

    fun clickNode(nodeId: Int): Boolean {
        val node = cachedNodes[nodeId] ?: return false
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return tapAt(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    fun tapAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    fun typeIntoNode(nodeId: Int?, text: String): Boolean {
        val target = if (nodeId != null) {
            cachedNodes[nodeId]
        } else {
            cachedNodes.values.firstOrNull { it.isEditable }
        } ?: return false

        target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scroll(direction: ScrollDirection): Boolean {
        val scrollable = cachedNodes.values.firstOrNull { it.isScrollable }
        if (scrollable != null) {
            val action = when (direction) {
                ScrollDirection.DOWN, ScrollDirection.RIGHT -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                ScrollDirection.UP, ScrollDirection.LEFT -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            if (scrollable.performAction(action)) return true
        }
        val path = Path().apply {
            when (direction) {
                ScrollDirection.DOWN -> { moveTo(540f, 1600f); lineTo(540f, 600f) }
                ScrollDirection.UP -> { moveTo(540f, 600f); lineTo(540f, 1600f) }
                ScrollDirection.LEFT -> { moveTo(900f, 1100f); lineTo(200f, 1100f) }
                ScrollDirection.RIGHT -> { moveTo(200f, 1100f); lineTo(900f, 1100f) }
            }
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 280L)
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    private fun clearCachedNodes() {
        cachedNodes.clear()
    }

    companion object {
        private val _instance = MutableStateFlow<OrbitAccessibilityService?>(null)
        val instance: StateFlow<OrbitAccessibilityService?> = _instance.asStateFlow()
    }
}
