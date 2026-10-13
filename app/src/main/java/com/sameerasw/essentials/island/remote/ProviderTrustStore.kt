package com.sameerasw.essentials.island.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.island.sdk.IslandIdentity

class ProviderTrustStore(private val settings: SettingsRepository) {
    private val gson = Gson()
    private val type = object : TypeToken<Map<String, String>>() {}.type

    fun trusted(): Map<String, String> =
        settings.getString(SettingsRepository.KEY_ISLAND_PROVIDERS)
            ?.let { runCatching { gson.fromJson<Map<String, String>>(it, type) }.getOrNull() }
            .orEmpty()

    fun isEnabled(packageName: String): Boolean = packageName in trusted()

    fun isVerified(context: Context, packageName: String): Boolean {
        val pinned = trusted()[packageName] ?: return false
        return fingerprint(context, packageName) == pinned
    }

    fun enable(context: Context, packageName: String): Boolean {
        val fingerprint = fingerprint(context, packageName) ?: return false
        save(trusted() + (packageName to fingerprint))
        return true
    }

    fun disable(packageName: String) = save(trusted() - packageName)

    fun hiddenFromIsland(): Set<String> =
        settings.getString(SettingsRepository.KEY_ISLAND_PROVIDERS_HIDDEN)
            ?.let { runCatching { gson.fromJson<List<String>>(it, object : TypeToken<List<String>>() {}.type) }.getOrNull() }
            .orEmpty()
            .toSet()

    fun isHiddenFromIsland(packageName: String): Boolean = packageName in hiddenFromIsland()

    fun setHiddenFromIsland(packageName: String, hidden: Boolean) {
        val next = if (hidden) hiddenFromIsland() + packageName else hiddenFromIsland() - packageName
        settings.putString(SettingsRepository.KEY_ISLAND_PROVIDERS_HIDDEN, next.takeIf { it.isNotEmpty() }?.let(gson::toJson))
    }

    private fun save(map: Map<String, String>) =
        settings.putString(SettingsRepository.KEY_ISLAND_PROVIDERS, map.takeIf { it.isNotEmpty() }?.let(gson::toJson))

    private fun fingerprint(context: Context, packageName: String): String? = IslandIdentity.fingerprint(context, packageName)
}
