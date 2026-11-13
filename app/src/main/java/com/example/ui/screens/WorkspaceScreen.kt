package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ClonedAppEntity
import com.example.data.model.DevicePlatform
import com.example.ui.CloudSyncState
import com.example.ui.components.AppIconBadge
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.formatByteSize
import com.example.ui.components.formatTimestampShort
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.theme.ThemeMode

@Composable
fun WorkspaceScreen(
    clonedApps: List<ClonedAppEntity>,
    cloudSyncState: CloudSyncState,
    vaultSizeBytes: Long,
    themeMode: ThemeMode,
    isPinConfigured: Boolean,
    getIconBitmap: (String) -> Bitmap?,
    onCycleTheme: () -> Unit,
    onLockVault: () -> Unit,
    onNavigateToCatalog: () -> Unit,
    onLaunchClone: (ClonedAppEntity) -> Unit,
    onInspectClone: (ClonedAppEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LocalLiquidGlass.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        val columns = if (maxWidth >= 680.dp) 2 else 1

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 110.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 980.dp)
                .align(Alignment.TopCenter)
        ) {
            item(span = { GridItemSpan(columns) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.displaySmall,
                                color = scheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            StatusPillBadge(
                                text = "by sky",
                                color = glass.accentTeal
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.workspace_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isPinConfigured) {
                            LiquidGlassSurface(
                                shape = CircleShape,
                                onClick = onLockVault,
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("workspace_lock_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = "Kunci Brankas",
                                    tint = glass.accentTeal,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.Center)
                                )
                            }
                        }

                        LiquidGlassSurface(
                            shape = CircleShape,
                            onClick = onCycleTheme,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("theme_toggle_button")
                        ) {
                            val icon = when (themeMode) {
                                ThemeMode.DARK -> Icons.Filled.DarkMode
                                ThemeMode.LIGHT -> Icons.Filled.LightMode
                                ThemeMode.SYSTEM -> Icons.Outlined.AutoAwesome
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = "Ganti tema tampilan",
                                tint = scheme.onSurface,
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }
                }
            }

            item(span = { GridItemSpan(columns) }) {
                LiquidGlassSurface(
                    shape = RoundedCornerShape(24.dp),
                    elevated = true,
                    accentRimColor = glass.accentTeal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Shield,
                                    contentDescription = null,
                                    tint = glass.verifiedEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Ruang Ganda Aktif",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = scheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${clonedApps.size} Aplikasi  •  Penyimpanan ${formatByteSize(vaultSizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatusPillBadge(
                                    text = "Terenkripsi",
                                    color = glass.verifiedEmerald,
                                    icon = Icons.Filled.Security
                                )
                                val (cloudLabel, cloudColor) = when (cloudSyncState) {
                                    is CloudSyncState.Synced -> "Cloud (${cloudSyncState.records.size})" to glass.accentTeal
                                    is CloudSyncState.Loading -> "Menyinkronkan..." to glass.accentTeal
                                    is CloudSyncState.LocalOnly -> "Lokal" to glass.accentTeal
                                    is CloudSyncState.Error -> "Offline" to glass.warningAmber
                                }
                                StatusPillBadge(
                                    text = cloudLabel,
                                    color = cloudColor,
                                    icon = Icons.Filled.CloudDone
                                )
                            }
                        }

                        Button(
                            onClick = onNavigateToCatalog,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = scheme.primary,
                                contentColor = scheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier.testTag("hero_add_clone_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Kloning", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            if (clonedApps.isEmpty()) {
                item(span = { GridItemSpan(columns) }) {
                    LiquidGlassSurface(
                        shape = RoundedCornerShape(26.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_empty_sandbox),
                                contentDescription = stringResource(R.string.cd_empty_sandbox),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(136.dp)
                                    .clip(RoundedCornerShape(24.dp))
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                text = stringResource(R.string.empty_workspace_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = scheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.empty_workspace_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = onNavigateToCatalog,
                                shape = CircleShape,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("empty_select_app_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.empty_workspace_cta))
                            }
                        }
                    }
                }
            } else {
                items(clonedApps, key = { it.id }) { clone ->
                    ClonedAppGlassCard(
                        clone = clone,
                        iconBitmap = getIconBitmap(clone.packageName),
                        onLaunch = { onLaunchClone(clone) },
                        onInspect = { onInspectClone(clone) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClonedAppGlassCard(
    clone: ClonedAppEntity,
    iconBitmap: Bitmap?,
    onLaunch: () -> Unit,
    onInspect: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LocalLiquidGlass.current
    val isIos = clone.devicePlatform.equals(DevicePlatform.IOS.name, ignoreCase = true)
    val maskedId = if (clone.virtualAndroidId.length >= 8) {
        "${clone.virtualAndroidId.take(4)}•••${clone.virtualAndroidId.takeLast(4)}"
    } else clone.virtualAndroidId

    LiquidGlassSurface(
        shape = RoundedCornerShape(24.dp),
        elevated = false,
        onClick = onLaunch,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("clone_card_${clone.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconBadge(
                    appName = clone.originalAppName,
                    packageName = clone.packageName,
                    iconBitmap = iconBitmap,
                    regionCode = clone.regionCode,
                    size = 52.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = clone.cloneLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${clone.virtualManufacturer} ${clone.virtualModel}  •  ID $maskedId",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${clone.city} (${clone.timezoneId}) • ${formatByteSize(clone.sandboxSizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onInspect,
                    modifier = Modifier.testTag("inspect_clone_${clone.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Inspeksi perangkat virtual ${clone.cloneLabel}",
                        tint = scheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    StatusPillBadge(
                        text = if (isIos) "iOS" else "Android",
                        color = glass.accentTeal,
                        icon = if (isIos) Icons.Filled.PhoneIphone else Icons.Filled.PhoneAndroid
                    )
                    StatusPillBadge(
                        text = clone.regionCode,
                        color = scheme.onSurface,
                        icon = Icons.Filled.Public
                    )
                    if (clone.proxyEnabled) {
                        StatusPillBadge(
                            text = "Proxy",
                            color = glass.verifiedEmerald,
                            icon = Icons.Filled.VpnLock
                        )
                    } else {
                        StatusPillBadge(
                            text = "Privat",
                            color = glass.verifiedEmerald,
                            icon = Icons.Filled.Lock
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onLaunch,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("launch_clone_${clone.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (clone.lastLaunchedAt != null) "Lanjut" else "Buka",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (clone.lastLaunchedAt != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Terakhir aktif: ${formatTimestampShort(clone.lastLaunchedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}
