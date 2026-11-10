package com.example.voicecontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ControlService : AccessibilityService() {

    companion object {
        var instance: ControlService? = null
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    private fun swipe(fromY: Float, toY: Float, duration: Long) {
        val dm = resources.displayMetrics
        val x = dm.widthPixels * 0.5f
        val path = Path().apply {
            moveTo(x, dm.heightPixels * fromY)
            lineTo(x, dm.heightPixels * toY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        dispatchGesture(gesture, null, null)
    }

    // الفيديو اللي بعده: سحب سريع لفوق
    fun nextVideo() = swipe(0.80f, 0.25f, 250)

    // سكرول بالراحة: سحب قصير وبطيء
    fun slowScroll() = swipe(0.70f, 0.45f, 1200)

    // نزّل التعليقات: سحب لفوق في الجزء السفلي
    fun scrollComments() = swipe(0.85f, 0.50f, 400)

    // افتح التعليقات: يدور على زر فيه كلمة comment أو تعليق
    fun openComments(): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findComment(root) ?: return false
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
        }
        return false
    }

    private fun findComment(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val text = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase()
        if (text.contains("comment") || text.contains("تعليق")) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findComment(child)
            if (found != null) return found
        }
        return null
    }
}
