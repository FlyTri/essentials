package com.sameerasw.essentials.utils

import android.content.Context
import com.sameerasw.essentials.data.repository.SettingsRepository

object BluetoothDeviceOrder {
    const val SEPARATOR = "|"
    private const val KEY = "bluetooth_device_order"

    private fun saved(context: Context): List<String> =
        SettingsRepository(context).getString(KEY, null)?.split(",")?.filter { it.isNotBlank() }.orEmpty()

    fun load(context: Context, addresses: List<String>): List<String> {
        val saved = saved(context)
        val known = saved.filter { it == SEPARATOR || it in addresses }.distinct()
        val withSeparator = if (SEPARATOR in known) known else known + SEPARATOR
        val separatorAt = withSeparator.indexOf(SEPARATOR)
        val fresh = addresses.filter { it !in withSeparator }
        return withSeparator.take(separatorAt) + fresh + withSeparator.drop(separatorAt)
    }

    fun save(context: Context, order: List<String>) {
        SettingsRepository(context).putString(KEY, order.joinToString(","))
    }

    fun <T> apply(context: Context, items: List<T>, address: (T) -> String): List<T> {
        val order = saved(context)
        if (order.isEmpty()) return items
        val separatorAt = order.indexOf(SEPARATOR).let { if (it < 0) order.size else it }
        val visible = order.take(separatorAt)
        val hidden = order.drop(separatorAt + 1).toSet()
        val ranked = items.filter { address(it) !in hidden }
        return ranked.sortedBy { item -> visible.indexOf(address(item)).let { if (it < 0) Int.MAX_VALUE else it } }
    }
}
