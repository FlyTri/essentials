/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Background Services & Receivers
 * File: VolumeStepsHandler.kt
 * Description: Background service component for VolumeStepsHandler.kt.
 */

package com.sameerasw.essentials.services.handlers

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ViewConfiguration
import com.sameerasw.essentials.data.repository.SettingsRepository

class VolumeStepsHandler(
    context: Context,
) {
    private val settingsRepository = SettingsRepository(context)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var repeatRaise = true

    private val repeatRunnable =
        object : Runnable {
            override fun run() {
                stepMediaVolume(repeatRaise)
                handler.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }

    fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }
        if (!isActive()) {
            handler.removeCallbacks(repeatRunnable)
            return false
        }

        // Key repeats are generated after the accessibility filter, so a consumed press never repeats on its own
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            repeatRaise = keyCode == KeyEvent.KEYCODE_VOLUME_UP
            stepMediaVolume(repeatRaise)
            handler.removeCallbacks(repeatRunnable)
            handler.postDelayed(repeatRunnable, ViewConfiguration.getKeyRepeatTimeout().toLong())
        } else if (event.action == KeyEvent.ACTION_UP) {
            handler.removeCallbacks(repeatRunnable)
        }
        return true
    }

    fun adjustIfEnabled(raise: Boolean): Boolean {
        if (!isActive()) return false
        stepMediaVolume(raise)
        return true
    }

    // Calls and ringing keep the system's own volume handling
    private fun isActive(): Boolean =
        settingsRepository.isVolumeStepsEnabled() &&
            !audioManager.isVolumeFixed &&
            audioManager.mode == AudioManager.MODE_NORMAL

    private fun stepMediaVolume(raise: Boolean) {
        val steps = settingsRepository.getVolumeStepsPerPress()
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val min =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            } else {
                0
            }
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (current + if (raise) steps else -steps).coerceIn(min, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
    }

    private companion object {
        const val REPEAT_INTERVAL_MS = 150L
    }
}
