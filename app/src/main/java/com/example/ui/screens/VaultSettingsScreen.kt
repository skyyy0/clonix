package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.SandboxAuditLogEntity
import com.example.data.local.UserPreferences
import com.example.data.model.VirtualRegionCatalog
import com.example.security.HostDeviceBaseline
import com.example.ui.CloudSyncState
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.formatByteSize
import com.example.ui.components.formatTimestampShort
import com.example.ui.theme.LocalLiquidGlass
import com.example.ui.theme.ThemeMode

@Composable
fun VaultSettingsScreen(
    preferences: UserPreferences,
    hostBaseline: HostDeviceBaseline,
    isHardwareKeystoreReady: Boolean,
    vaultSizeBytes: Long,
    auditLogs: List<SandboxAuditLogEntity>,
    isSignedIn: Boolean,
    userDisplayName: String?,
    userEmail: String?,
    cloudSyncState: CloudSyncState,
    authConnectError: String?,
    onConnectGoogleAccount: () -> Unit,
    onSignOut: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onDefaultRegionChange: (String) -> Unit,
    onStrictAntiDetectChange: (Boolean) -> Unit,
    onConfigurePin: (String) -> Unit,
    onRemovePin: () -> Unit,
    onWipeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val glass = LocalLiquidGlass.current

    var showPinDialog by remember { mutableStateOf(false) }
    var showWipeConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
                .align(Alignment.TopCenter)
        ) {
            item {
                Text(
                    text = stringResource(R.string.vault_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = scheme.onBackground
                )
                Text(
                    text = stringResource(R.string.vault_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }

            // Hero Vault Shield Card
            item {
                LiquidGlassSurface(
                    shape = RoundedCornerShape(26.dp),
                    elevated = true,
                    accentRimColor = glass.accentTeal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_vault_shield),
                            contentDescription = stringResource(R.string.cd_vault_shield),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(168.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(168.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFF09090B).copy(alpha = 0.92f),
                                            Color(0xFF09090B).copy(alpha = 0.56f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(18.dp)
                        ) {
                            StatusPillBadge(
                                text = if (isHardwareKeystoreReady) "Keamanan Perangkat Keras Aktif" else "Enkripsi Penuh Aktif",
                                color = glass.verifiedEmerald,
                                icon = Icons.Filled.VerifiedUser
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Penyimpanan Terenkripsi",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Ukuran Data: ${formatByteSize(vaultSizeBytes)} • Terpisah dari Perangkat Asli",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFD4D4D8)
                            )
                        }
                    }
                }
            }

            // Google Account & Cloud Firestore Sync Card
            item {
                LiquidGlassSurface(
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccountCircle,
                                    contentDescription = null,
                                    tint = glass.accentTeal,
                                    modifier = Modifier.size(34.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isSignedIn) {
                                            userDisplayName?.takeIf { it.isNotBlank() } ?: "Pengguna Clonix"
                                        } else {
                                            "Penyimpanan Lokal"
                                        },
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = if (isSignedIn) {
                                            userEmail?.takeIf { it.isNotBlank() } ?: "Akun Google Terautentikasi"
                                        } else {
                                            "Masuk nanti untuk cadangan cloud • Data aman di perangkat"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isSignedIn) {
                                OutlinedButton(
                                    onClick = onSignOut,
                                    shape = CircleShape,
                                    modifier = Modifier.testTag("sign_out_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Logout,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Keluar")
                                }
                            } else {
                                Button(
                                    onClick = onConnectGoogleAccount,
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = scheme.primary,
                                        contentColor = scheme.onPrimary
                                    ),
                                    modifier = Modifier.testTag("connect_google_button")
                                ) {
                                    Text("Hubungkan Google")
                                }
                            }
                        }

                        if (!authConnectError.isNullOrBlank()) {
                            Text(
                                text = authConnectError,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.error
                            )
                        }

                        val (syncText, syncColor) = when (cloudSyncState) {
                            is CloudSyncState.Synced ->
                                "Cloud aktif • ${cloudSyncState.records.size} aplikasi tersinkron" to glass.verifiedEmerald
                            is CloudSyncState.Loading ->
                                "Menghubungkan ke cloud..." to glass.accentTeal
                            is CloudSyncState.LocalOnly ->
                                "Penyimpanan Lokal Aktif" to glass.accentTeal
                            is CloudSyncState.Error ->
                                "Sinkronisasi tertunda: ${cloudSyncState.message}" to glass.warningAmber
                        }
                        StatusPillBadge(
                            text = syncText,
                            color = syncColor,
                            icon = Icons.Filled.CloudDone
                        )
                    }
                }
            }

            // Appearance / Dark Mode Selector
            item {
                LiquidGlassSurface(
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Tampilan Liquid Glass",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val options = listOf(
                                ThemeMode.SYSTEM to "Otomatis",
                                ThemeMode.DARK to "Gelap",
                                ThemeMode.LIGHT to "Terang"
                            )
                            options.forEach { (mode, label) ->
                                val selected = preferences.themeMode == mode
                                Surface(
                                    shape = CircleShape,
                                    color = if (selected) scheme.primary.copy(alpha = 0.18f) else scheme.surfaceVariant.copy(alpha = 0.55f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) scheme.primary else scheme.outline.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(CircleShape)
                                        .clickable { onThemeChange(mode) }
                                        .testTag("theme_mode_${mode.name.lowercase()}")
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) scheme.primary else scheme.onSurface,
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Default Country & Privacy Controls
            item {
                LiquidGlassSurface(
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Negara Virtual Bawaan",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(VirtualRegionCatalog.regions, key = { it.code }) { region ->
                                val selected = region.code == preferences.defaultRegionCode
                                Surface(
                                    shape = CircleShape,
                                    color = if (selected) scheme.primary.copy(alpha = 0.16f) else scheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) scheme.primary else scheme.outline.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { onDefaultRegionChange(region.code) }
                                ) {
                                    Text(
                                        text = "${region.code} • ${region.displayName}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) scheme.primary else scheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Mode Anti-Deteksi Ketat",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Acak Android ID, User-Agent, & Timezone tiap kontainer.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = preferences.strictAntiDetect,
                                onCheckedChange = onStrictAntiDetectChange
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (preferences.isPinConfigured) "Kunci PIN Brankas Aktif" else "Aktifkan Kunci PIN Brankas",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Lindungi akses Clonix dengan PIN 4 digit ter-hash SHA-256.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                            if (preferences.isPinConfigured) {
                                OutlinedButton(
                                    onClick = onRemovePin,
                                    shape = CircleShape,
                                    modifier = Modifier.testTag("remove_pin_button")
                                ) {
                                    Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Nonaktifkan")
                                }
                            } else {
                                Button(
                                    onClick = { showPinDialog = true },
                                    shape = CircleShape,
                                    modifier = Modifier.testTag("setup_pin_button")
                                ) {
                                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Atur PIN")
                                }
                            }
                        }
                    }
                }
            }

            // Host Baseline Protection Card
            item {
                LiquidGlassSurface(
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Key,
                                contentDescription = null,
                                tint = glass.accentTeal,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Perangkat Asli (Terlindungi)",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Perangkat Anda: ${hostBaseline.manufacturer} ${hostBaseline.model}",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant
                        )
                        Text(
                            text = "ID Asli: ${hostBaseline.androidId.take(6)}•••••••• (Tersembunyi dari kloning)",
                            style = MaterialTheme.typography.labelSmall,
                            color = glass.verifiedEmerald
                        )
                    }
                }
            }

            // Audit Log Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Riwayat Aktivitas (${auditLogs.size})",
                        style = MaterialTheme.typography.titleSmall
                    )
                    TextButton(
                        onClick = { showWipeConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = scheme.error)
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bersihkan Semua")
                    }
                }
            }

            if (auditLogs.isEmpty()) {
                item {
                    LiquidGlassSurface(
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Belum ada aktivitas yang tercatat.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(auditLogs.take(15), key = { it.id }) { log ->
                    LiquidGlassSurface(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = log.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatTimestampShort(log.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = log.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Developer Brand Footer
            item {
                LiquidGlassSurface(
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = glass.accentTeal,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Clonix v1.0  •  Developer: sky  •  Privasi Terenkripsi",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Atur PIN Aplikasi (4 Digit)") },
            text = {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { value ->
                        if (value.length <= 4 && value.all { it.isDigit() }) {
                            pinInput = value
                        }
                    },
                    label = { Text("PIN 4 digit") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length == 4) {
                            onConfigurePin(pinInput)
                            showPinDialog = false
                        }
                    },
                    enabled = pinInput.length == 4
                ) {
                    Text("Simpan PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("Hapus Semua Aplikasi Kloning?") },
            text = {
                Text("Semua aplikasi kloning, sesi login, dan riwayat aktivitas akan dihapus permanen dari perangkat ini.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onWipeAll()
                        showWipeConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.error)
                ) {
                    Text("Hapus Permanen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
