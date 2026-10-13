package com.sameerasw.essentials.island.plugins.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.sameerasw.essentials.R
import com.sameerasw.essentials.data.repository.SettingsRepository
import com.sameerasw.essentials.domain.model.NavigationNotificationData
import com.sameerasw.essentials.domain.model.NavigationParser
import com.sameerasw.essentials.island.model.CompactCell
import com.sameerasw.essentials.island.model.CompactPlacement
import com.sameerasw.essentials.island.model.ExpandedContent
import com.sameerasw.essentials.island.model.IslandItem
import com.sameerasw.essentials.island.model.IslandPluginContext
import com.sameerasw.essentials.island.model.IslandPriority
import com.sameerasw.essentials.island.model.IslandStage
import com.sameerasw.essentials.island.model.LineContent
import com.sameerasw.essentials.island.model.PluginRequest
import com.sameerasw.essentials.island.plugins.BaseIslandPlugin
import com.sameerasw.essentials.island.plugins.launchPackage
import com.sameerasw.essentials.island.plugins.sendPendingIntent
import com.sameerasw.essentials.island.ui.components.IslandBitmap
import com.sameerasw.essentials.island.ui.components.IslandIcon
import com.sameerasw.essentials.island.ui.components.RollingText
import com.sameerasw.essentials.services.NotificationListener

class NavigationPlugin : BaseIslandPlugin() {
    override val id = "navigation"

    override val settingKeys = setOf(
        SettingsRepository.KEY_ISLAND_SHOW_NAVIGATION,
        SettingsRepository.KEY_ISLAND_NAVIGATION_ON_NEW,
        SettingsRepository.KEY_ISLAND_NAVIGATION_PEEK_AFTER_EXPAND,
        SettingsRepository.KEY_ISLAND_NAVIGATION_EXPAND_DISTANCE,
    )

    private var data: NavigationNotificationData? = null

    // Instruction the island already expanded for, so each turn expands once
    private var expandedFor: String? = null

    private val endExpand = Runnable {
        val c = ctx ?: return@Runnable
        // The user closed it or switched to something else
        if (c.focusedKey() != ITEM_KEY || c.currentStage() != IslandStage.Expanded) return@Runnable
        c.request(PluginRequest.Collapse(ITEM_KEY))
        if (settings.isIslandNavigationPeekAfterExpandEnabled() && settings.isIslandLineStageEnabled()) {
            c.mainHandler.postDelayed(peekAfterCollapse, COLLAPSE_ANIM_MS)
        }
    }

    private val peekAfterCollapse = Runnable {
        ctx?.request?.invoke(PluginRequest.Peek(ITEM_KEY, settings.getIslandPeekDurationMs()))
    }

    private val listener = object : NotificationListener.NavigationNotificationListener {
        override fun onNavigationNotificationUpdated(data: NavigationNotificationData?) {
            ctx?.mainHandler?.post { update(data) }
        }
    }

    override fun onStart() {
        NotificationListener.addNavigationNotificationListener(listener)
    }

    override fun onStop() {
        NotificationListener.removeNavigationNotificationListener(listener)
        ctx?.mainHandler?.removeCallbacks(endExpand)
        ctx?.mainHandler?.removeCallbacks(peekAfterCollapse)
        data = null
        expandedFor = null
    }

    override fun refresh() {
        ctx ?: return
        data = NotificationListener.getLatestNavigationNotification()
        render()
    }

    private fun update(next: NavigationNotificationData?) {
        val c = ctx ?: return
        val turned = next?.instruction != null && next.instruction != data?.instruction
        data = next
        render()
        if (next == null) {
            expandedFor = null
            c.mainHandler.removeCallbacks(endExpand)
            c.mainHandler.removeCallbacks(peekAfterCollapse)
            return
        }
        if (!enabled()) return
        val instruction = next.instruction.orEmpty()
        val threshold = settings.getIslandNavigationExpandDistance()
        val meters = NavigationParser.meters(next.distance)
        // Distance jumped back up: a new maneuver with the same text ("Turn right" twice in a row)
        if (meters != null && meters > threshold * REARM_FACTOR) expandedFor = null
        val approaching = threshold > 0 && meters != null && meters <= threshold && instruction != expandedFor
        when {
            approaching && expand(c) -> expandedFor = instruction
            !turned -> {}
            settings.getIslandNavigationOnNew() == SettingsRepository.ISLAND_NAVIGATION_ON_NEW_EXPAND -> expand(c)
            settings.getIslandNavigationOnNew() == SettingsRepository.ISLAND_NAVIGATION_ON_NEW_PEEK && settings.isIslandLineStageEnabled() ->
                c.request(PluginRequest.Peek(ITEM_KEY, settings.getIslandPeekDurationMs()))
        }
    }

    // Expands for a while, then collapses to a line peek or compact
    private fun expand(c: IslandPluginContext): Boolean {
        // Don't take over something the user is looking at; retried on the next distance update
        val focused = c.focusedKey()
        if (focused != null && focused != ITEM_KEY) return false
        c.mainHandler.removeCallbacks(peekAfterCollapse)
        c.request(PluginRequest.Expand(ITEM_KEY))
        c.mainHandler.removeCallbacks(endExpand)
        c.mainHandler.postDelayed(endExpand, EXPANDED_MS)
        return true
    }

    private fun enabled() = settings.isIslandShowNavigationEnabled()

    private fun render() {
        val d = data
        if (ctx == null || d == null || !enabled()) {
            publish(null)
            return
        }
        publish(
            IslandItem(
                key = ITEM_KEY,
                priority = IslandPriority.NAVIGATION,
                placement = CompactPlacement.Dynamic,
                compact = listOfNotNull(
                    d.distance?.let { CompactCell("navigation.distance") { RollingText(it) } },
                    CompactCell("navigation.maneuver") { Maneuver(d, 18) },
                ),
                line = LineContent(
                    icon = { Maneuver(d, 20) },
                    start = d.instruction.orEmpty(),
                    end = d.distance.orEmpty(),
                ),
                expanded = ExpandedContent { scope ->
                    NavigationExpanded(
                        data = d,
                        accent = MaterialTheme.colorScheme.primary,
                        onOpen = { open(d) },
                        onExit = d.exitIntent?.let { intent -> { sendPendingIntent(context, intent) } },
                        scope = scope,
                    )
                },
                onOpen = { open(d) },
                sourcePackage = d.packageName,
            ),
        )
    }

    private fun open(d: NavigationNotificationData) {
        if (!sendPendingIntent(context, d.contentIntent)) launchPackage(context, d.packageName)
    }

    companion object {
        const val ITEM_KEY = "navigation"

        // ponytail: fixed timings; make them settings if people ask
        private const val EXPANDED_MS = 6_000L
        private const val COLLAPSE_ANIM_MS = 600L
        private const val REARM_FACTOR = 1.5
    }
}

@Composable
internal fun Maneuver(d: NavigationNotificationData, size: Int) {
    if (d.maneuver != null) {
        IslandBitmap(d.maneuver, size.dp, fallbackRes = R.drawable.round_navigation_24)
    } else {
        IslandIcon(R.drawable.round_navigation_24, size = size.dp, tint = MaterialTheme.colorScheme.primary)
    }
}
