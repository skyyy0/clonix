package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.DevicePlatform
import com.example.data.model.InstalledAppInfo
import com.example.data.model.LaunchMode
import com.example.data.model.NetworkExitReport
import com.example.data.model.ProxyConfig
import com.example.data.model.ProxyType
import com.example.data.model.VirtualRegion
import com.example.data.model.VirtualRegionCatalog
import com.example.ui.components.AppIconBadge
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.LocalLiquidGlass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigureCloneSheet(
    app: InstalledAppInfo,
    defaultRegionCode: String,
    exitIpReport: NetworkExitReport?,
    onProbeExitIp: (ProxyConfig?, String) -> Unit,
    onDismiss: () -> Unit,
    onConfirmClone: (
        region: VirtualRegion,
        platform: DevicePlatform,
        customLabel: String,
        preferredModelIndex: Int?,
        launchMode: LaunchMode,
        customPortalUrl: String,
        proxyConfig: ProxyConfig,
        preferDesktopWeb: Boolean
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme
    val glass = LocalLiquidGlass.current

    val isWhatsApp = remember(app.packageName) {
        app.packageName.contains("whatsapp", ignoreCase = true)
    }

    var selectedRegion by remember(defaultRegionCode) {
        mutableStateOf(VirtualRegionCatalog.findByCode(defaultRegionCode))
    }
    var selectedPlatform by remember { mutableStateOf(DevicePlatform.ANDROID) }
    var selectedModelIdx by remember(selectedRegion, selectedPlatform) { mutableIntStateOf(0) }
    var customLabel by remember(app, selectedRegion, selectedPlatform) {
        mutableStateOf("${app.appName} • ${selectedRegion.code} ${selectedPlatform.badge}")
    }
    var portalUrl by remember(app) { mutableStateOf(app.defaultWebPortalUrl) }
    var preferDesktopWeb by remember(app) { mutableStateOf(isWhatsApp) }

    var proxyType by remember { mutableStateOf(ProxyType.DIRECT) }
    var proxyHost by remember { mutableStateOf("") }
    var proxyPort by remember { mutableStateOf("") }
    var proxyUser by remember { mutableStateOf("") }
    var proxyPass by remember { mutableStateOf("") }

    fun currentProxyConfig(): ProxyConfig {
        val portNum = proxyPort.toIntOrNull() ?: 0
        return ProxyConfig(
            type = proxyType,
            host = proxyHost.trim(),
            port = portNum,
            username = proxyUser.trim(),
            password = proxyPass
        )
    }

    val modelList = if (selectedPlatform == DevicePlatform.IOS) {
        selectedRegion.iosModels
    } else {
        selectedRegion.deviceModels
    }

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
                    appName = app.appName,
                    packageName = app.packageName,
                    iconBitmap = app.iconBitmap,
                    regionCode = selectedRegion.code,
                    size = 54.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Konfigurasi Kloning",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${app.appName} (${app.packageName})",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Tutup konfigurasi")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = customLabel,
                onValueChange = { customLabel = it },
                label = { Text("Nama Kontainer Kloning") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("clone_label_input")
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Platform Picker: Android vs iPhone (iOS)
            Text(
                text = "Platform Perangkat Virtual",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DevicePlatform.entries.forEach { platform ->
                    val active = selectedPlatform == platform
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (active) scheme.primary.copy(alpha = 0.16f) else scheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(
                            width = if (active) 1.5.dp else 1.dp,
                            color = if (active) scheme.primary else scheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                selectedPlatform = platform
                                selectedModelIdx = 0
                                customLabel = "${app.appName} • ${selectedRegion.code} ${platform.badge}"
                            }
                            .testTag("platform_option_${platform.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (platform == DevicePlatform.IOS) Icons.Filled.PhoneIphone else Icons.Filled.PhoneAndroid,
                                contentDescription = null,
                                tint = if (active) scheme.primary else scheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = platform.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) scheme.primary else scheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Public,
                    contentDescription = null,
                    tint = glass.accentTeal,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Negara & Lokasi Virtual Perangkat",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Layanan akan membaca zona waktu, bahasa, geolokasi, dan IP (bila proxy/VPN aktif) sesuai negara pilihan.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(VirtualRegionCatalog.regions, key = { it.code }) { region ->
                    val isSelected = region.code == selectedRegion.code
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) scheme.primary.copy(alpha = 0.16f) else scheme.surfaceVariant.copy(alpha = 0.65f),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) scheme.primary else scheme.outline.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                selectedRegion = region
                                selectedModelIdx = 0
                                customLabel = "${app.appName} • ${region.code} ${selectedPlatform.badge}"
                            }
                            .testTag("region_option_${region.code}")
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = region.code,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) scheme.primary else scheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = scheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = region.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurface
                            )
                            Text(
                                text = region.city,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LiquidGlassSurface(
                shape = RoundedCornerShape(18.dp),
                elevated = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Profil Jaringan & Waktu (${selectedRegion.code})",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.primary
                        )
                        StatusPillBadge(
                            text = selectedRegion.currencyCode,
                            color = glass.verifiedEmerald
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Timezone: ${selectedRegion.timezoneId}  •  Locale: ${selectedRegion.localeTag}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurface
                    )
                    Text(
                        text = "Carrier: ${selectedRegion.carrierName} (MCC ${selectedRegion.mcc}-${selectedRegion.mnc})",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Model Perangkat ${selectedPlatform.label}",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                modelList.forEachIndexed { idx, preset ->
                    val isSelected = idx == selectedModelIdx
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) scheme.primaryContainer.copy(alpha = 0.45f) else scheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) scheme.primary else scheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selectedModelIdx = idx }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val osLabel = if (selectedPlatform == DevicePlatform.IOS) {
                                    "iOS ${preset.androidVersion} (${preset.deviceCode}) • Apple GPU"
                                } else {
                                    "Android ${preset.androidVersion} • ${preset.model} • API ${preset.sdkInt} • ${preset.gpuRenderer}"
                                }
                                Text(
                                    text = osLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Dipilih",
                                    tint = scheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Real Proxy & Exit-IP Anti-Detection Section (Critical for WhatsApp / Foreign Numbers)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.VpnLock,
                    contentDescription = null,
                    tint = glass.accentTeal,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isWhatsApp) "Penyesuaian Wilayah WhatsApp (Otomatis)" else "Rute Jaringan & Negara",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isWhatsApp) {
                    val iosExtra = if (selectedPlatform == DevicePlatform.IOS) " Tampilan juga disesuaikan ke WhatsApp versi iPhone (iOS)." else ""
                    "Clonix menyelaraskan zona waktu, bahasa, dan identitas browser ke ${selectedRegion.displayName}. Untuk IP keluar ${selectedRegion.code}, pakai proxy/VPN Anda sendiri lalu verifikasi dengan tombol di bawah.$iosExtra"
                } else {
                    "Zona waktu & bahasa mengikuti ${selectedRegion.code}. IP keluar hanya berubah bila Anda memakai proxy HTTP/SOCKS5 atau VPN."
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ProxyType.entries) { type ->
                    val selected = proxyType == type
                    Surface(
                        shape = CircleShape,
                        color = if (selected) scheme.primary.copy(alpha = 0.16f) else scheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, if (selected) scheme.primary else scheme.outline.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { proxyType = type }
                    ) {
                        Text(
                            text = type.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) scheme.primary else scheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            if (proxyType != ProxyType.DIRECT) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = proxyHost,
                        onValueChange = { proxyHost = it },
                        label = { Text("Host / IP Proxy") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = proxyPort,
                        onValueChange = { value -> if (value.all { it.isDigit() } && value.length <= 5) proxyPort = value },
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = proxyUser,
                        onValueChange = { proxyUser = it },
                        label = { Text("Username (Opsional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = proxyPass,
                        onValueChange = { proxyPass = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = { onProbeExitIp(currentProxyConfig(), selectedRegion.code) },
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("check_exit_ip_button")
            ) {
                if (exitIpReport?.isLoading == true) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Memeriksa IP Publik Nyata...")
                } else {
                    Icon(Icons.Filled.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Verifikasi IP & Negara Rute Saat Ini")
                }
            }

            if (exitIpReport != null && !exitIpReport.isLoading) {
                Spacer(modifier = Modifier.height(8.dp))
                val report = exitIpReport
                val badgeColor = when {
                    report.errorMessage != null -> scheme.error
                    report.matchesTargetRegion -> glass.verifiedEmerald
                    else -> glass.warningAmber
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = badgeColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (report.errorMessage != null) {
                            Text(
                                text = "Gagal verifikasi rute: ${report.errorMessage}",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.error
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (report.matchesTargetRegion) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                                    contentDescription = null,
                                    tint = badgeColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "IP Publik: ${report.ipAddress} (${report.countryCode} • ${report.city})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = badgeColor
                                )
                            }
                            Text(
                                text = "ISP: ${report.isp} • Latensi ${report.latencyMs} ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant
                            )
                            if (!report.matchesTargetRegion) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Peringatan: IP keluar berada di ${report.countryCode}, bukan ${selectedRegion.code}. Hubungkan VPN ${selectedRegion.code} atau masukkan Proxy ${selectedRegion.code} agar layanan seperti WhatsApp tidak mendeteksi lokasi asli Anda.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Honest scope: say exactly what this clone changes, and what Android does not
            // allow a normal app to change (IMEI/Build for another app's process).
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = glass.accentTeal.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, glass.accentTeal.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Yang benar-benar berubah di klon ini",
                        style = MaterialTheme.typography.labelLarge,
                        color = glass.accentTeal
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Penyimpanan, cookie, dan sesi login terpisah penuh dari aplikasi asli.\n" +
                            "• Identitas yang dibaca layanan: ${modelList.getOrNull(selectedModelIdx)?.displayName ?: "perangkat baru"}, GPU, resolusi, zona waktu ${selectedRegion.timezoneId}, bahasa ${selectedRegion.localeTag}, dan geolokasi ${selectedRegion.city}.\n" +
                            "• IP keluar mengikuti proxy/VPN Anda — verifikasi dulu di tombol atas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Batasan jujur: tanpa root, Android tidak mengizinkan aplikasi memalsukan IMEI atau Build perangkat untuk proses aplikasi lain. Karena itu klon berjalan di kontainer terisolasi Clonix, dan yang bisa dibaca layanan adalah identitas web + IP di atas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = portalUrl,
                onValueChange = { portalUrl = it },
                label = { Text("Alamat Portal Aplikasi") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tampilan Desktop / WhatsApp Web",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Disarankan untuk menautkan akun di web.whatsapp.com.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = preferDesktopWeb,
                    onCheckedChange = { preferDesktopWeb = it }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(glass.verifiedEmerald.copy(alpha = 0.1f))
                    .padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = glass.verifiedEmerald,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Data login, profil perangkat ${selectedPlatform.label}, dan riwayat sesi Anda dienkripsi penuh di perangkat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    onConfirmClone(
                        selectedRegion,
                        selectedPlatform,
                        customLabel,
                        selectedModelIdx,
                        LaunchMode.ISOLATED_CONTAINER,
                        portalUrl,
                        currentProxyConfig(),
                        preferDesktopWeb
                    )
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_clone_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Kloning ${selectedPlatform.badge} (${selectedRegion.code})",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
