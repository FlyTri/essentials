package com.sameerasw.essentials.utils

import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

object VolumePanelDetector {
    private const val SYSTEM_UI = "com.android.systemui"
    private const val MAX_NODES = 80

    fun isVolumePanel(window: AccessibilityWindowInfo): Boolean {
        if (window.type != AccessibilityWindowInfo.TYPE_SYSTEM) return false
        if (window.title?.contains("Volume", ignoreCase = true) == true) return true
        val root = window.root ?: return false
        if (root.packageName?.toString() != SYSTEM_UI) return false
        return hasVolumeId(root)
    }

    private fun hasVolumeId(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (node.viewIdResourceName?.contains("volume", ignoreCase = true) == true) return true
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return false
    }
}
