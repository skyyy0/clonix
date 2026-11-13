package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ClonedAppEntity
import com.example.data.model.DevicePlatform
import com.example.data.model.VirtualRegionCatalog
import com.example.security.HostDeviceBaseline
import com.example.ui.components.AppIconBadge
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.formatByteSize
import com.example.ui.theme.LocalLiquidGlass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloneInspectorSheet(
    clone: ClonedAppEntity,
    hostBaseline: HostDeviceBaseline,
    iconBitmap: Bitmap?,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onRegenerateIdentity: (newRegionCode: String?, newPlatform: DevicePlatform?) -> Unit,
    onDeleteClone: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    val glass = LocalLiquidGlass.current
    val currentPlatform = runCatching { DevicePlatform.valueOf(clone.devicePlatform) }
        .getOrDefault(DevicePlatform.ANDROID)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surface,
        contentColor = scheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconBadge(
                    appName = clone.originalAppName,
                    packageName = clone.packageName,
                    iconBitmap = iconBitmap,
                    regionCode = clone.regionCode,
                    size = 54.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(clone.cloneLabel, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "${clone.packageName} • ${formatByteSize(clone.sandboxSizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Tutup inspektor")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPillBadge(
                    text = "Anti-Deteksi Aktif",
                    color = glass.verifiedEmerald,
                    icon = Icons.Filled.VerifiedUser
                )
                StatusPillBadge(
                    text = "${currentPlatform.badge} • ${clone.regionCode}",
                    color = glass.accentTeal
                )
                if (clone.proxyEnabled) {
                    StatusPillBadge(
                        text = "${clone.proxyType} Proxy",
                        color = glass.verifiedEmerald,
                        icon = Icons.Filled.VpnLock
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Perbandingan Perangkat Asli vs Virtual",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            LiquidGlassSurface(
                shape = RoundedCornerShape(18.dp),
                elevated = true,
                accentRimColor = glass.accentTeal,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ComparisonRow(
                        field = "Platform & Model",
                        hostValue = "Android ${hostBaseline.manufacturer} ${hostBaseline.model}",
                        virtualValue = "${currentPlatform.badge}: ${clone.virtualManufacturer} ${clone.virtualModel}"
                    )
                    ComparisonRow(
                        field = if (currentPlatform == DevicePlatform.IOS) "Device ID iPhone Baru" else "Android ID Baru",
                        hostValue = hostBaseline.androidId.take(12) + "…",
                        virtualValue = clone.virtualAndroidId
                    )
                    ComparisonRow(
                        field = "IMEI / Build Perangkat",
                        hostValue = "Milik perangkat asli",
                        virtualValue = "Tidak dipalsukan (butuh root)"
                    )
                    if (clone.virtualGsfId.isNotBlank() || clone.virtualWidevineId.isNotBlank()) {
                        ComparisonRow(
                            field = "ID Layanan & Keamanan",
                            hostValue = "Tersembunyi",
                            virtualValue = "${clone.virtualGsfId.take(10)}… • ${clone.virtualWidevineId.take(12)}…"
                        )
                    }
                    ComparisonRow(
                        field = "Metadata Sandbox (internal)",
                        hostValue = "Tidak terlihat situs",
                        virtualValue = "${clone.virtualSerial.take(8)}… • ${clone.virtualMacAddress}"
                    )
                    ComparisonRow(
                        field = "Timezone, Bahasa & Geo",
                        hostValue = "Asli Perangkat",
                        virtualValue = "${clone.timezoneId} • ${clone.localeTag} • ${clone.city}"
                    )
                    if (clone.apkDigestSha256.isNotBlank()) {
                        ComparisonRow(
                            field = "Integritas Penyimpanan",
                            hostValue = "Terpisah",
                            virtualValue = "Terverifikasi (${clone.apkDigestSha256.take(12)}…)"
                        )
                    }
                    ComparisonRow(
                        field = "Rute Jaringan",
                        hostValue = "Koneksi Utama",
                        virtualValue = if (clone.proxyEnabled) "${clone.proxyType} ${clone.proxyHost}:${clone.proxyPort}" else "Tanpa proxy (IP perangkat asli)"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Ganti Platform Virtual (Android / iPhone)",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DevicePlatform.entries.forEach { plat ->
                    val selected = plat == currentPlatform
                    Surface(
                        shape = CircleShape,
                        color = if (selected) scheme.primary.copy(alpha = 0.16f) else scheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .clickable {
                                if (!selected) onRegenerateIdentity(null, plat)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (plat == DevicePlatform.IOS) Icons.Filled.PhoneIphone else Icons.Filled.PhoneAndroid,
                                contentDescription = null,
                                tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = plat.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) scheme.primary else scheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Pindah Negara Virtual Instan",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(VirtualRegionCatalog.regions, key = { it.code }) { region ->
                    val selected = region.code == clone.regionCode
                    Surface(
                        shape = CircleShape,
                        color = if (selected) scheme.primary.copy(alpha = 0.16f) else scheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(
                            1.dp,
                            if (selected) scheme.primary else scheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                if (!selected) onRegenerateIdentity(region.code, null)
                            }
                    ) {
                        Text(
                            text = "${region.code} • ${region.city}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) scheme.primary else scheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { onRegenerateIdentity(null, null) },
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("inspector_rotate_id_button")
                ) {
                    Icon(Icons.Filled.Autorenew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Acak ID Baru")
                }

                Button(
                    onClick = onLaunch,
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("inspector_launch_button")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Buka Aplikasi")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onDeleteClone,
                shape = CircleShape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error),
                border = BorderStroke(1.dp, scheme.error.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("inspector_delete_button")
            ) {
                Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Hapus Permanen Kloning")
            }
        }
    }
}

@Composable
private fun ComparisonRow(
    field: String,
    hostValue: String,
    virtualValue: String
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LocalLiquidGlass.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = field,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Host: $hostValue",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.78f),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = virtualValue,
                style = MaterialTheme.typography.labelSmall,
                color = glass.verifiedEmerald,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1.2f)
            )
        }
    }
}
