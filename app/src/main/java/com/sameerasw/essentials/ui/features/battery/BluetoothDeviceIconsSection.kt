package com.sameerasw.essentials.ui.features.battery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.IconButton
import androidx.compose.runtime.key
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import com.sameerasw.essentials.utils.BluetoothDeviceOrder
import com.sameerasw.essentials.utils.HapticUtil
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sameerasw.essentials.R
import com.sameerasw.essentials.services.widgets.BatteriesWidgetReceiver
import com.sameerasw.essentials.ui.core.sheets.EssentialsBottomSheet
import com.sameerasw.essentials.utils.BluetoothDeviceIcon
import com.sameerasw.essentials.utils.BluetoothDeviceIcons

private data class PairedDevice(val name: String, val address: String, val iconRes: Int? = null)

@SuppressLint("MissingPermission")
private fun pairedDevices(context: Context): List<PairedDevice> =
    try {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
            ?.adapter
            ?.bondedDevices
            ?.map { PairedDevice(it.alias ?: it.name ?: it.address, it.address) }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    } catch (_: SecurityException) {
        emptyList()
    }

@Composable
fun BluetoothDeviceIconsSection() {
    val context = LocalContext.current
    val view = LocalView.current
    val devices =
        remember {
            listOf(
                PairedDevice("Phone", BluetoothDeviceOrder.PHONE, R.drawable.rounded_mobile_24),
                PairedDevice("Mac", BluetoothDeviceOrder.MAC, R.drawable.rounded_laptop_mac_24),
            ) + pairedDevices(context)
        }
    var version by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<PairedDevice?>(null) }
    var order by remember { mutableStateOf(BluetoothDeviceOrder.load(context, devices.map { it.address })) }
    val byAddress = remember(devices) { devices.associateBy { it.address } }
    val separatorAt = order.indexOf(BluetoothDeviceOrder.SEPARATOR)

    ReorderableColumn(
        list = order,
        onSettle = { from, to ->
            order = order.toMutableList().apply { add(to, removeAt(from)) }
            BluetoothDeviceOrder.save(context, order)
            context.sendBroadcast(
                Intent(context, BatteriesWidgetReceiver::class.java).apply {
                    action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                },
            )
        },
        verticalArrangement = Arrangement.spacedBy(2.dp),
        onMove = { HapticUtil.performVirtualKeyHaptic(view) },
    ) { index, entry, _ ->
        key(entry) {
            ReorderableItem {
                val top = if (index == 0) 24.dp else 4.dp
                val bottom = if (index == order.size - 1) 24.dp else 4.dp
                val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
                val hidden = separatorAt in 0 until index
                val handle: @Composable () -> Unit = {
                    IconButton(modifier = Modifier.draggableHandle(), onClick = {}) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_drag_handle_24),
                            contentDescription = stringResource(R.string.content_desc_drag_reorder),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                if (entry == BluetoothDeviceOrder.SEPARATOR) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.secondaryContainer, shape)
                                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.bt_order_separator),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        handle()
                    }
                } else {
                    val device = byAddress[entry] ?: return@ReorderableItem
                    val iconRes = device.iconRes ?: remember(version, device.address) { BluetoothDeviceIcons.resolve(context, device.address, device.name) }
                    val custom = device.iconRes == null && remember(version, device.address) { BluetoothDeviceIcons.getOverrideId(context, device.address) != null }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .alpha(if (hidden) 0.4f else 1f)
                                .background(MaterialTheme.colorScheme.surfaceBright, shape)
                                .clickable(enabled = device.iconRes == null) { editing = device }
                                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(iconRes), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f).padding(start = 16.dp),
                        )
                        if (device.iconRes == null) {
                            Text(
                                text = if (custom) stringResource(R.string.bt_icon_custom) else stringResource(R.string.bt_icon_auto),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        handle()
                    }
                }
            }
        }
    }

    editing?.let { device ->
        BluetoothDeviceIconSheet(
            device = device,
            onDismiss = { editing = null },
            onPick = { id ->
                BluetoothDeviceIcons.setOverride(context, device.address, id)
                version++
                editing = null
                context.sendBroadcast(
                    Intent(context, BatteriesWidgetReceiver::class.java).apply {
                        action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    },
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BluetoothDeviceIconSheet(
    device: PairedDevice,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selected = remember { BluetoothDeviceIcons.getOverrideId(context, device.address) }

    EssentialsBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = device.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconTile(
                    icon = BluetoothDeviceIcon("", stringResource(R.string.bt_icon_auto), BluetoothDeviceIcons.auto(device.name)),
                    selected = selected == null,
                    wide = true,
                    onClick = { onPick(null) },
                )
                BluetoothDeviceIcons.groups.forEach { group ->
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        group.icons.forEach { icon ->
                            IconTile(icon = icon, selected = selected == icon.id, wide = false, onClick = { onPick(icon.id) })
                        }
                    }
                }
                Box(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun IconTile(
    icon: BluetoothDeviceIcon,
    selected: Boolean,
    wide: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceBright
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Column(
        modifier =
            Modifier
                .then(if (wide) Modifier.fillMaxWidth() else Modifier.width(76.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(container)
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(painterResource(icon.res), contentDescription = null, tint = content, modifier = Modifier.size(28.dp))
        Text(
            text = icon.label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
