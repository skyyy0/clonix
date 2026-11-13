package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AppCategory
import com.example.data.model.InstalledAppInfo
import com.example.data.model.LaunchMode
import com.example.ui.CatalogUiState
import com.example.ui.components.AppIconBadge
import com.example.ui.components.GlassActionChip
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.formatByteSize
import com.example.ui.theme.LocalLiquidGlass

@Composable
fun AppCatalogScreen(
    catalogState: CatalogUiState,
    defaultRegionCode: String,
    onSearchChange: (String) -> Unit,
    onCategorySelect: (AppCategory) -> Unit,
    onRefreshApps: () -> Unit,
    onSelectAppToClone: (InstalledAppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LocalLiquidGlass.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
                .align(Alignment.TopCenter)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.catalog_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = scheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.catalog_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                    }
                    LiquidGlassSurface(
                        shape = CircleShape,
                        onClick = onRefreshApps,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("refresh_apps_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Muat ulang daftar aplikasi perangkat",
                            tint = scheme.onSurface,
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.Center)
                        )
                    }
                }
            }

            item {
                LiquidGlassSurface(
                    shape = CircleShape,
                    elevated = true,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (catalogState.searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_apps_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                            BasicTextField(
                                value = catalogState.searchQuery,
                                onValueChange = onSearchChange,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
                                cursorBrush = SolidColor(scheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("catalog_search_input")
                            )
                        }
                        if (catalogState.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSearchChange("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Bersihkan pencarian",
                                    tint = scheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AppCategory.entries) { category ->
                        GlassActionChip(
                            label = category.labelId,
                            selected = catalogState.selectedCategory == category,
                            onClick = { onCategorySelect(category) },
                            modifier = Modifier.testTag("cat_${category.name.lowercase()}")
                        )
                    }
                }
            }

            // Section 1: Instant Isolated Social Containers
            if (catalogState.filteredPresets.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Aplikasi Populer (Siap Pakai)",
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onBackground
                        )
                        StatusPillBadge(
                            text = "Default $defaultRegionCode",
                            color = glass.accentTeal,
                            icon = Icons.Filled.Public
                        )
                    }
                }

                items(catalogState.filteredPresets, key = { "preset_${it.packageName}" }) { app ->
                    CatalogAppRow(
                        app = app,
                        badgeLabel = "Siap Pakai",
                        badgeColor = glass.verifiedEmerald,
                        onCloneClick = { onSelectAppToClone(app) }
                    )
                }
            }

            // Section 2: Real Installed Apps discovered via PackageManager
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Terpasang di Perangkat (${catalogState.filteredDeviceApps.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = scheme.onBackground
                    )
                    Text(
                        text = "Pilih bebas + negara",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            if (catalogState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = glass.accentTeal)
                    }
                }
            } else if (catalogState.filteredDeviceApps.isEmpty()) {
                item {
                    LiquidGlassSurface(
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Tidak ada aplikasi perangkat yang cocok dengan pencarian.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                items(catalogState.filteredDeviceApps, key = { "device_${it.packageName}" }) { app ->
                    val isNative = app.recommendedLaunchMode.isNative
                    val modeColor = if (isNative) glass.verifiedEmerald else glass.accentTeal
                    val sizeText = if (app.apkSizeBytes > 0L) formatByteSize(app.apkSizeBytes) else "Terpasang"

                    CatalogAppRow(
                        app = app,
                        badgeLabel = if (isNative) "Native • $sizeText" else sizeText,
                        badgeColor = modeColor,
                        onCloneClick = { onSelectAppToClone(app) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogAppRow(
    app: InstalledAppInfo,
    badgeLabel: String,
    badgeColor: androidx.compose.ui.graphics.Color,
    onCloneClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    LiquidGlassSurface(
        shape = RoundedCornerShape(22.dp),
        onClick = onCloneClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("catalog_app_${app.packageName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconBadge(
                appName = app.appName,
                packageName = app.packageName,
                iconBitmap = app.iconBitmap,
                size = 48.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${app.packageName} • v${app.versionName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                StatusPillBadge(
                    text = badgeLabel,
                    color = badgeColor
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onCloneClick,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AddCircleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Kloning",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
