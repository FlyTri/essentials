/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Background Services & Receivers
 * File: AppVolumeOverlayHandler.kt
 * Description: Shows a button under the system volume panel that opens volume controls for the apps playing now.
 */

package com.sameerasw.essentials.services.handlers

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sameerasw.essentials.R
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.island.service.OverlayLifecycleOwner
import com.sameerasw.essentials.ui.activities.AppVolumeControlActivity
import com.sameerasw.essentials.utils.AppUtil
import com.sameerasw.essentials.utils.HapticUtil
import com.sameerasw.essentials.utils.media.AppVolumeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class AppVolumeOverlayHandler(
    private val service: AccessibilityService,
) {
    private val settings = SettingsRepository(service)
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density

    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()
    private var overlayView: View? = null
    private var owner: OverlayLifecycleOwner? = null
    private var params: WindowManager.LayoutParams? = null
    private var isShown by mutableStateOf(false)
    private var isPressed = false
    private var hidePending = false
    private var lastTouchAt = 0L

    private var isPolling = false
    private val pollRunnable =
        Runnable {
            isPolling = false
            if (isShown) onWindowsChanged()
        }
    private val parkRunnable = Runnable { park() }
    private val hideRunnable =
        object : Runnable {
            override fun run() {
                if (isPressed) {
                    handler.postDelayed(this, HIDE_GRACE_MS)
                    return
                }
                hidePending = false
                isShown = false
                handler.removeCallbacks(parkRunnable)
                handler.postDelayed(parkRunnable, EXIT_ANIMATION_MS)
            }
        }

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == SettingsRepository.KEY_APP_VOLUME_OVERLAY_ENABLED || key == SettingsRepository.KEY_APP_VOLUME_ENABLED) {
                handler.post { onWindowsChanged() }
            }
        }

    init {
        settings.registerOnSharedPreferenceChangeListener(prefsListener)
        scope.launch {
            AppVolumeController.playingPackages.collect { packages ->
                withContext(Dispatchers.IO) { packages.forEach { loadIcon(it) } }
                onWindowsChanged()
            }
        }
    }

    fun onWindowsChanged() {
        if (!isFeatureOn()) {
            handler.removeCallbacksAndMessages(null)
            hidePending = false
            removeView()
            return
        }
        if (overlayView == null) create()
        val bounds = if (isEnabled()) findVolumePanelBounds() else null
        if (bounds == null) {
            scheduleHide()
            return
        }
        handler.removeCallbacks(hideRunnable)
        hidePending = false
        handler.removeCallbacks(parkRunnable)
        show(bounds)
        if (!isPolling) {
            isPolling = true
            handler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
        }
    }

    fun onScreenOff() = hideNow()

    fun onDestroy() {
        settings.unregisterOnSharedPreferenceChangeListener(prefsListener)
        handler.removeCallbacksAndMessages(null)
        removeView()
        scope.cancel()
    }

    private fun isFeatureOn() = settings.isAppVolumeOverlayEnabled() && settings.isAppVolumeEnabled()

    private fun isEnabled() = isFeatureOn() && AppVolumeController.playingPackages.value.isNotEmpty()

    private fun findVolumePanelBounds(): Rect? =
        try {
            service.windows
                ?.firstOrNull { window ->
                    window.type == AccessibilityWindowInfo.TYPE_SYSTEM &&
                        window.title?.contains("Volume", ignoreCase = true) == true
                }?.let { window -> Rect().also(window::getBoundsInScreen).takeUnless { it.isEmpty } }
        } catch (_: Exception) {
            null
        }

    private fun scheduleHide() {
        if (hidePending || !isShown) return
        hidePending = true
        val remaining = TOUCH_GRACE_MS - (SystemClock.uptimeMillis() - lastTouchAt)
        if (isPressed || remaining > 0) handler.postDelayed(hideRunnable, remaining.coerceAtLeast(HIDE_GRACE_MS)) else hideRunnable.run()
    }

    private fun hideNow() {
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(pollRunnable)
        handler.removeCallbacks(parkRunnable)
        isPolling = false
        hidePending = false
        isPressed = false
        isShown = false
        park()
    }

    private fun park() {
        val current = params ?: return
        current.x = OFFSCREEN_PX
        current.y = OFFSCREEN_PX
        current.flags = current.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        runCatching { windowManager?.updateViewLayout(overlayView, current) }
    }

    private fun show(panel: Rect) {
        val windowWidth = (WINDOW_WIDTH_DP * density).toInt()
        val x = (panel.centerX() - windowWidth / 2).coerceAtLeast(0)
        val y = panel.bottom + (PANEL_GAP_DP * density).toInt()
        val existing = params ?: return
        val flags = existing.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        if (existing.x != x || existing.y != y || existing.flags != flags) {
            existing.x = x
            existing.y = y
            existing.flags = flags
            runCatching { windowManager?.updateViewLayout(overlayView, existing) }
        }
        isShown = true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun create() {
        val lifecycleOwner = OverlayLifecycleOwner().also { it.onCreate() }
        val container =
            FrameLayout(service).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                addView(
                    ComposeView(service).apply {
                        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                        setContent {
                            OverlayButton(
                                visible = isShown,
                                onPressChanged = {
                                    isPressed = it
                                    lastTouchAt = SystemClock.uptimeMillis()
                                },
                                onTap = ::openControls,
                            )
                        }
                    },
                )
            }
        val layoutParams = layoutParams(OFFSCREEN_PX, OFFSCREEN_PX)
        try {
            windowManager?.addView(container, layoutParams)
            overlayView = container
            owner = lifecycleOwner
            params = layoutParams
            isShown = false
        } catch (e: Exception) {
            e.printStackTrace()
            lifecycleOwner.onDestroy()
        }
    }

    private fun removeView() {
        isShown = false
        isPolling = false
        handler.removeCallbacks(pollRunnable)
        overlayView?.let { runCatching { windowManager?.removeView(it) } }
        overlayView = null
        params = null
        owner?.onDestroy()
        owner = null
    }

    private fun openControls() {
        val intent =
            Intent(service, AppVolumeControlActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        service.startActivity(intent)
    }

    private fun layoutParams(
        x: Int,
        y: Int,
    ) = WindowManager.LayoutParams(
        (WINDOW_WIDTH_DP * density).toInt(),
        (WINDOW_HEIGHT_DP * density).toInt(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun colorScheme(): ColorScheme {
        val isDark =
            (service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isDark -> dynamicDarkColorScheme(service)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(service)
            isDark -> darkColorScheme()
            else -> lightColorScheme()
        }
    }

    private class StackEntry(
        val packageName: String,
        depth: Int,
    ) {
        var depth by mutableIntStateOf(depth)
        var isPresent by mutableStateOf(true)
    }

    @Composable
    private fun OverlayButton(
        visible: Boolean,
        onPressChanged: (Boolean) -> Unit,
        onTap: () -> Unit,
    ) {
        val view = LocalView.current
        val colors = remember { colorScheme() }
        val label = service.getString(R.string.app_volume_overlay_button_desc)
        var pressed by remember { mutableStateOf(false) }
        val pressScale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, label = "overlayPressScale")

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            AnimatedVisibility(
                visible = visible,
                enter =
                    fadeIn(tween(ENTER_FADE_MS)) +
                        scaleIn(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow), initialScale = 0.5f),
                exit = fadeOut(tween(EXIT_MS)) + scaleOut(tween(EXIT_MS), targetScale = 0.6f),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(BUTTON_SIZE_DP.dp)
                            .graphicsLayer {
                                scaleX = pressScale
                                scaleY = pressScale
                            }.clip(CircleShape)
                            .background(colors.surfaceContainerHigh)
                            .semantics { contentDescription = label }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        pressed = true
                                        onPressChanged(true)
                                        tryAwaitRelease()
                                        pressed = false
                                        onPressChanged(false)
                                    },
                                    onTap = {
                                        HapticUtil.performVirtualKeyHaptic(view)
                                        onTap()
                                    },
                                )
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    IconStack()
                }
            }
        }
    }

    @Composable
    private fun IconStack() {
        val playing by AppVolumeController.playingPackages.collectAsState()
        val top = playing.take(MAX_STACKED)
        val entries = remember { mutableStateListOf<StackEntry>() }

        LaunchedEffect(top) {
            entries.forEach { entry ->
                val depth = top.indexOf(entry.packageName)
                entry.isPresent = depth >= 0
                if (depth >= 0) entry.depth = depth
            }
            top.forEachIndexed { depth, packageName ->
                if (entries.none { it.packageName == packageName && it.isPresent }) entries.add(StackEntry(packageName, depth))
            }
            delay(EXIT_ANIMATION_MS)
            entries.removeAll { !it.isPresent }
        }

        entries.sortedByDescending { it.depth }.forEach { entry ->
            key(entry) { StackedIcon(entry) }
        }
    }

    @Composable
    private fun StackedIcon(entry: StackEntry) {
        val icon = remember(entry.packageName) { loadIcon(entry.packageName) } ?: return
        var appeared by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { appeared = true }
        val visible = appeared && entry.isPresent
        val motion = if (visible) spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow) else tween(EXIT_MS)
        val offsetY by animateDpAsState(
            (FRONT_SHIFT_DP - STACK_STEP_DP * entry.depth).dp,
            spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow),
            label = "stackOffset",
        )
        val scale by animateFloatAsState(
            if (visible) 1f - DEPTH_SCALE_STEP * entry.depth else 0.5f,
            motion,
            label = "stackScale",
        )
        val alpha by animateFloatAsState(
            if (visible) 1f - DEPTH_FADE_STEP * entry.depth else 0f,
            motion,
            label = "stackAlpha",
        )
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier =
                Modifier
                    .offset(y = offsetY)
                    .size(ICON_SIZE_DP.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }.clip(CircleShape),
        )
    }

    private fun loadIcon(packageName: String): ImageBitmap? =
        iconCache[packageName]
            ?: runCatching {
                AppUtil.drawableToBitmap(service.packageManager.getApplicationIcon(packageName)).asImageBitmap()
            }.getOrNull()?.also { iconCache[packageName] = it }

    private companion object {
        const val WINDOW_WIDTH_DP = 72
        const val WINDOW_HEIGHT_DP = 64
        const val BUTTON_SIZE_DP = 52
        const val ICON_SIZE_DP = 28
        const val PANEL_GAP_DP = 14
        const val FRONT_SHIFT_DP = 4
        const val STACK_STEP_DP = 8
        const val MAX_STACKED = 3
        const val DEPTH_SCALE_STEP = 0.2f
        const val DEPTH_FADE_STEP = 0.25f
        const val PRESSED_SCALE = 0.9f
        const val HIDE_GRACE_MS = 150L
        const val TOUCH_GRACE_MS = 800L
        const val EXIT_ANIMATION_MS = 200L
        const val EXIT_MS = 160
        const val ENTER_FADE_MS = 220
        const val POLL_INTERVAL_MS = 250L
        const val OFFSCREEN_PX = -10000
    }
}
