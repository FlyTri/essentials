/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: Utilities
 * File: AppIconUtil.kt
 * Description: Utility to safely switch app launcher icon aliases.
 */

package com.sameerasw.essentials.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.domain.model.AppIcon

object AppIconUtil {
    fun mainIntent(context: Context): android.content.Intent =
        context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: android.content.Intent(context, com.sameerasw.essentials.MainActivity::class.java)

    fun setAppIcon(context: Context, targetIcon: AppIcon) {
        val packageManager = context.packageManager

        val targetComponent = ComponentName(context.packageName, targetIcon.aliasName)
        packageManager.setComponentEnabledSetting(
            targetComponent,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )

        for (icon in AppIcon.entries) {
            if (icon != targetIcon) {
                val component = ComponentName(context.packageName, icon.aliasName)
                packageManager.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }

    fun islandAppIcon(context: Context, packageName: String): Drawable {
        val icon = context.packageManager.getApplicationIcon(packageName)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return icon
        if (!SettingsRepository(context).getBoolean(SettingsRepository.KEY_ISLAND_THEMED_APP_ICONS, false)) return icon
        return themed(context, icon) ?: icon
    }

    private fun themed(context: Context, icon: Drawable): Drawable? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val monochrome = (icon as? AdaptiveIconDrawable)?.monochrome?.mutate() ?: return null
        monochrome.setTint(context.getColor(android.R.color.system_accent1_200))
        val canvasSize = 432
        val full = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
        monochrome.setBounds(0, 0, canvasSize, canvasSize)
        monochrome.draw(Canvas(full))
        val start = canvasSize * SAFE_ZONE_START / ADAPTIVE_SIZE
        val side = canvasSize * (SAFE_ZONE_END - SAFE_ZONE_START) / ADAPTIVE_SIZE
        return BitmapDrawable(context.resources, Bitmap.createBitmap(full, start, start, side, side))
    }

    private const val ADAPTIVE_SIZE = 108
    private const val SAFE_ZONE_START = 21
    private const val SAFE_ZONE_END = 87
}
