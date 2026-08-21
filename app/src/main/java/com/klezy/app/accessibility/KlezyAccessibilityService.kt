package com.klezy.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * KlezyAccessibilityService
 *
 * This is what lets Klezy see what's on screen and act on it —
 * tap buttons, type into fields, scroll, go back/home.
 *
 * The user must turn this on manually in
 * Settings > Accessibility > Klezy (see PermissionHelper.openAccessibilitySettings).
 * Android will not let an app enable this for itself.
 */
class KlezyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "KlezyAccessibility"

        // Lets the rest of the app (chat, automation engine) reach the
        // running service instance without binding — simplest option for P1.
        var instance: KlezyAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Klezy accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let { pkg ->
                com.klezy.app.automation.AutomationEngine.onAppOpened(pkg)
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Klezy accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    // ---------- Reading the screen ----------

    /** Flattens all visible text on screen into one string, top to bottom. */
    fun getScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val out = StringBuilder()
        collectText(root, out)
        return out.toString().trim()
    }

    private fun collectText(node: AccessibilityNodeInfo, out: StringBuilder) {
        if (!node.text.isNullOrBlank()) {
            out.append(node.text).append("\n")
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                collectText(child, out)
                child.recycle()
            }
        }
    }

    // ---------- Acting on the screen ----------

    /** Finds the first node whose text or content description contains [label] and taps it. */
    fun tapByText(label: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val match = findNode(root) { node ->
            node.text?.contains(label, ignoreCase = true) == true ||
                node.contentDescription?.contains(label, ignoreCase = true) == true
        }
        return match?.let { performClick(it) } ?: false
    }

    private fun findNode(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNode(child, predicate)
            if (result != null) return result
            child.recycle()
        }
        return null
    }

    /** Clicks a node directly, or falls back to a tap gesture at its center if not clickable. */
    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) {
            target = target.parent
        }
        if (target != null) {
            return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return tapAt(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    /** Raw gesture tap at screen coordinates — for elements accessibility can't resolve by text. */
    fun tapAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    /** Types [text] into whichever field is currently focused. */
    fun typeIntoFocusedField(text: String): Boolean {
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val arguments = android.os.Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    fun goBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun goHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun openRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotificationShade() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    /** Scrolls the first scrollable container found, forward or backward. */
    fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findNode(root) { it.isScrollable } ?: return false
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return scrollable.performAction(action)
    }
}
