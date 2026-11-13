package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LocalLiquidGlass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppIconBadge(
    appName: String,
    packageName: String,
    iconBitmap: Bitmap?,
    regionCode: String? = null,
    size: Dp = 52.dp,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val imageBitmap = remember(iconBitmap) { iconBitmap?.asImageBitmap() }
    val (fallbackColor, fallbackIcon) = remember(packageName) { resolveBrandVisual(packageName) }

    Box(modifier = modifier.size(size + 4.dp)) {
        Box(
            modifier = Modifier
                .size(size)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (imageBitmap != null) {
                        Brush.verticalGradient(listOf(scheme.surfaceVariant, scheme.surface))
                    } else {
                        Brush.linearGradient(
                            listOf(fallbackColor.copy(alpha = 0.92f), fallbackColor.copy(alpha = 0.65f))
                        )
                    }
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = stringResource(R.string.cd_app_icon, appName),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = stringResource(R.string.cd_app_icon, appName),
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.52f)
                )
            }
        }

        if (!regionCode.isNullOrBlank()) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd),
                shape = RoundedCornerShape(8.dp),
                color = scheme.primary,
                border = BorderStroke(1.5.dp, scheme.background)
            ) {
                Text(
                    text = regionCode,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}

private fun resolveBrandVisual(packageName: String): Pair<Color, ImageVector> {
    return when {
        packageName.contains("whatsapp") -> Color(0xFF15803D) to Icons.Filled.ChatBubble
        packageName.contains("instagram") -> Color(0xFFBE185D) to Icons.Filled.PhotoCamera
        packageName.contains("musically") || packageName.contains("trill") -> Color(0xFF18181B) to Icons.Filled.PlayCircle
        packageName.contains("facebook") -> Color(0xFF1D4ED8) to Icons.Filled.Public
        packageName.contains("twitter") -> Color(0xFF18181B) to Icons.Filled.Forum
        packageName.contains("telegram") -> Color(0xFF0F766E) to Icons.AutoMirrored.Filled.Send
        packageName.contains("discord") -> Color(0xFF4338CA) to Icons.Filled.Forum
        packageName.contains("reddit") -> Color(0xFFC2410C) to Icons.Filled.Forum
        else -> Color(0xFF27272A) to Icons.Filled.Code
    }
}

enum class ClonixTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    WORKSPACE("workspace", R.string.nav_workspace, Icons.Filled.Layers, Icons.Outlined.Layers, "tab_workspace"),
    CATALOG("catalog", R.string.nav_apps, Icons.Filled.Apps, Icons.Outlined.Apps, "tab_catalog"),
    VAULT("vault", R.string.nav_vault, Icons.Filled.Shield, Icons.Outlined.Shield, "tab_vault")
}

@Composable
fun LiquidGlassBottomBar(
    currentRoute: String,
    onNavigate: (ClonixTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val glass = LocalLiquidGlass.current
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(),
            shape = CircleShape,
            color = glass.glassNavBarFill,
            tonalElevation = 4.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(
                width = 0.75.dp,
                brush = Brush.linearGradient(
                    listOf(glass.glassSpecularTop, glass.glassSpecularBottom, glass.glassSpecularTop.copy(0.22f))
                )
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClonixTab.entries.forEach { tab ->
                    val selected = currentRoute == tab.route
                    val pillColor = if (selected) scheme.primary else Color.Transparent
                    val tint = if (selected) scheme.onPrimary else scheme.onSurfaceVariant

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(pillColor)
                            .clickable { onNavigate(tab) }
                            .minimumInteractiveComponentSize()
                            .testTag(tab.testTag)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = stringResource(tab.labelRes),
                            tint = tint,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            color = tint,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PinLockGateOverlay(
    pinError: String?,
    onVerifyPin: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val digitsState = remember { androidx.compose.runtime.mutableStateOf("") }

    LiquidGlassBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LiquidGlassSurface(
                shape = CircleShape,
                elevated = true,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.pin_lock_title),
                    tint = scheme.primary,
                    modifier = Modifier
                        .size(34.dp)
                        .align(Alignment.Center)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.pin_lock_title),
                style = MaterialTheme.typography.headlineSmall,
                color = scheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.pin_lock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(4) { index ->
                    val filled = index < digitsState.value.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (filled) scheme.primary else scheme.outline.copy(alpha = 0.4f))
                    )
                }
            }

            if (pinError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = pinError,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "DEL")
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { key ->
                            when (key) {
                                "" -> Spacer(modifier = Modifier.size(68.dp))
                                "DEL" -> {
                                    LiquidGlassSurface(
                                        shape = CircleShape,
                                        onClick = {
                                            if (digitsState.value.isNotEmpty()) {
                                                digitsState.value = digitsState.value.dropLast(1)
                                            }
                                        },
                                        modifier = Modifier.size(68.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                                            contentDescription = "Hapus digit",
                                            tint = scheme.onSurface,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                                else -> {
                                    LiquidGlassSurface(
                                        shape = CircleShape,
                                        elevated = true,
                                        onClick = {
                                            if (digitsState.value.length < 4) {
                                                val updated = digitsState.value + key
                                                digitsState.value = updated
                                                if (updated.length == 4) {
                                                    onVerifyPin(updated)
                                                    digitsState.value = ""
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(68.dp)
                                    ) {
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = scheme.onSurface,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatByteSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

fun formatTimestampShort(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
