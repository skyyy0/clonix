package com.example.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.cloud.ClonixCloudRepository
import com.example.data.local.ClonedAppEntity
import com.example.data.local.ClonixDao
import com.example.data.local.SandboxAuditLogEntity
import com.example.data.model.DevicePlatform
import com.example.data.model.InstalledAppInfo
import com.example.data.model.LaunchMode
import com.example.data.model.NetworkExitReport
import com.example.data.model.ProvisioningUiState
import com.example.data.model.ProxyConfig
import com.example.data.model.VirtualDeviceProfile
import com.example.data.model.VirtualRegion
import com.example.data.model.VirtualRegionCatalog
import com.example.network.NetworkRoutingEngine
import com.example.security.HostDeviceBaseline
import com.example.security.HostIsolationCapabilities
import com.example.security.VaultCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.UUID

class ClonixRepository(
    private val context: Context,
    private val dao: ClonixDao,
    val cryptoManager: VaultCryptoManager,
    private val cloudRepository: ClonixCloudRepository? = null,
    val networkRoutingEngine: NetworkRoutingEngine = NetworkRoutingEngine()
) {
    val clonedApps: Flow<List<ClonedAppEntity>> = dao.observeClonedApps()
    val auditLogs: Flow<List<SandboxAuditLogEntity>> = dao.observeAuditLogs()

    fun getHostBaseline(): HostDeviceBaseline = cryptoManager.getHostDeviceBaseline()

    fun getHostIsolationCapabilities(): HostIsolationCapabilities =
        cryptoManager.inspectHostIsolationCapabilities()

    fun provisionClone(
        app: InstalledAppInfo,
        region: VirtualRegion,
        platform: DevicePlatform,
        customLabel: String,
        preferredModelIndex: Int?,
        launchMode: LaunchMode,
        customPortalUrl: String,
        proxyConfig: ProxyConfig,
        preferDesktopWeb: Boolean
    ): Flow<ProvisioningUiState> = flow {
        val logs = mutableListOf<String>()
        val isNativeClone = launchMode.isNative
        val totalSteps = if (isNativeClone) 4 else 6

        fun emitStep(stepIndex: Int, title: String, detail: String, logLine: String): ProvisioningUiState.Running {
            logs.add(logLine)
            return ProvisioningUiState.Running(
                appName = app.appName,
                region = region,
                platform = platform,
                currentStepIndex = stepIndex,
                totalSteps = totalSteps,
                currentTitle = title,
                currentDetail = detail,
                progress = stepIndex.toFloat() / totalSteps.toFloat(),
                logs = logs.toList()
            )
        }

        try {
            val isWhatsApp = app.packageName.contains("whatsapp", ignoreCase = true)

            // Stage 1: Inspect app & host baseline
            emit(
                emitStep(
                    stepIndex = 1,
                    title = "Mempersiapkan Ruang ${app.appName}",
                    detail = if (launchMode.isNative) {
                        "Menyiapkan profil kerja terpisah untuk ${app.appName}."
                    } else {
                        "Memisahkan penyimpanan dan memutus tautan dengan perangkat asli Anda."
                    },
                    logLine = "Menyiapkan profil ${app.appName} (v${app.versionName})"
                )
            )
            val hostBaseline = cryptoManager.getHostDeviceBaseline()
            val appInfo = if (!app.isVirtualPreset) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.getApplicationInfo(app.packageName, PackageManager.ApplicationInfoFlags.of(0L))
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getApplicationInfo(app.packageName, 0)
                    }
                }.getOrNull()
            } else null
            delay(380)

            // Stage 2: Provision Vault Key
            val cloneId = UUID.randomUUID().toString()
            emit(
                emitStep(
                    stepIndex = 2,
                    title = "Mengamankan Brankas Penyimpanan",
                    detail = "Membuat kunci enkripsi pribadi khusus untuk akun ${app.appName} ini.",
                    logLine = "Kunci enkripsi perangkat keras siap"
                )
            )
            val keystoreReady = cryptoManager.isHardwareKeystoreReady()
            delay(380)

            if (isNativeClone) {
                // Native clone: a real second installation inside an Android work profile.
                // Only per-profile values really change (storage, accounts, ANDROID_ID,
                // advertising ID). Hardware identity stays the device's own, and the UI
                // says so instead of pretending otherwise.
                emit(
                    emitStep(
                        stepIndex = 3,
                        title = "Menyiapkan Instance Profil Kerja",
                        detail = "${app.appName} akan dipasang di profil kerja terpisah dengan storage, akun, dan Android ID profil sendiri.",
                        logLine = "Mode native: profil kerja (persetujuan Android diminta sekali saat pertama dibuka)"
                    )
                )
                val nativeId = "profil-kerja-${cloneId.take(8)}"
                val nativeMarker = cryptoManager.serializeNativeProfileMarker(app.packageName, hostBaseline)
                val profileWrite = cryptoManager.encryptToVault(
                    cloneId,
                    "profile",
                    nativeMarker.toByteArray(Charsets.UTF_8)
                )
                delay(320)

                val nativeInstanceNumber = dao.countInstancesForPackage(app.packageName) + 1
                val nativeLabel = customLabel.trim().ifBlank {
                    "${app.appName} • Profil Kerja #$nativeInstanceNumber"
                }
                emit(
                    emitStep(
                        stepIndex = 4,
                        title = "Menyimpan Klon",
                        detail = "Klon tersimpan di halaman klon Clonix. Tombol Buka akan menyiapkan profil kerja sekali, lalu langsung membuka aplikasinya.",
                        logLine = "Klon native siap dibuka dari daftar klon"
                    )
                )

                val entity = ClonedAppEntity(
                    id = cloneId,
                    packageName = app.packageName,
                    originalAppName = app.appName,
                    cloneLabel = nativeLabel,
                    instanceNumber = nativeInstanceNumber,
                    devicePlatform = DevicePlatform.ANDROID.name,
                    regionCode = region.code,
                    regionName = region.displayName,
                    city = region.city,
                    timezoneId = region.timezoneId,
                    localeTag = region.localeTag,
                    carrierName = region.carrierName,
                    mccMnc = "${region.mcc}-${region.mnc}",
                    virtualModel = hostBaseline.model,
                    virtualManufacturer = hostBaseline.manufacturer,
                    virtualAndroidId = nativeId,
                    virtualSerial = "mengikuti perangkat",
                    virtualFingerprint = hostBaseline.fingerprint,
                    virtualFingerprintHash = cryptoManager.sha256Hex(hostBaseline.fingerprint).take(16).uppercase(),
                    virtualMacAddress = "mengikuti perangkat",
                    virtualAdvertisingId = "per profil kerja",
                    apkDigestSha256 = "",
                    sandboxContainerPath = "",
                    encryptedVaultPath = profileWrite.filePath,
                    sandboxSizeBytes = profileWrite.sizeBytes,
                    launchMode = LaunchMode.WORK_PROFILE.name,
                    targetWebUrl = "",
                    proxyEnabled = proxyConfig.isConfigured,
                    proxyType = proxyConfig.type.name,
                    proxyHost = proxyConfig.host.trim(),
                    proxyPort = if (proxyConfig.isConfigured) proxyConfig.port else 0,
                    preferDesktopWeb = false,
                    isEncrypted = keystoreReady,
                    isAntiDetectActive = false,
                    isLocked = false,
                    createdAt = System.currentTimeMillis(),
                    lastLaunchedAt = null
                )

                dao.insertClone(entity)
                val auditTitle = "Klon Native Dibuat: $nativeLabel"
                val auditDetail = "Profil kerja | Android ID & akun terpisah | IMEI/Build mengikuti perangkat"
                dao.insertAuditLog(
                    SandboxAuditLogEntity(
                        cloneId = cloneId,
                        eventType = "PROVISION_NATIVE",
                        title = auditTitle,
                        detail = auditDetail
                    )
                )
                cloudRepository?.syncCloneToCloud(entity)
                cloudRepository?.pushAuditLog(cloneId, "PROVISION_NATIVE", auditTitle, auditDetail)
                delay(240)

                emit(
                    ProvisioningUiState.Success(
                        cloneId = cloneId,
                        cloneLabel = nativeLabel,
                        regionCode = region.code,
                        platform = DevicePlatform.ANDROID,
                        virtualModel = hostBaseline.model,
                        virtualAndroidId = nativeId
                    )
                )
            } else {
                // Stage 3: Synthesize a coherent, unlinkable device identity
                val iosSuffix = if (isWhatsApp && platform == DevicePlatform.IOS) " • Edisi iOS" else ""
                emit(
                    emitStep(
                        stepIndex = 3,
                        title = "Membentuk Perangkat Baru (${platform.label}$iosSuffix)",
                        detail = "Menyusun identitas baru: model, GPU, resolusi, zona waktu, bahasa, dan operator ${region.carrierName} yang saling konsisten.",
                        logLine = "Identitas baru disetel: ${platform.label} (${region.code})"
                    )
                )
                val virtualProfile = cryptoManager.synthesizeVirtualIdentity(
                    region = region,
                    hostBaseline = hostBaseline,
                    platform = platform,
                    preferredModelIndex = preferredModelIndex
                )
                val fpHash = cryptoManager.sha256Hex(virtualProfile.buildFingerprint).take(16).uppercase()
                delay(440)

                // Stage 4: Regional routing. The public exit IP is the signal a server actually
                // trusts for "where is this device", so verify the user's own proxy/VPN route and
                // report it honestly instead of gambling on free public relays.
                val effectiveProxy = proxyConfig
                emit(
                    emitStep(
                        stepIndex = 4,
                        title = "Menyelaraskan Wilayah & Jaringan ${region.code}",
                        detail = if (effectiveProxy.isConfigured) {
                            "Memverifikasi IP keluar lewat proxy ${effectiveProxy.summary()}."
                        } else {
                            "Menyelaraskan zona waktu ${region.timezoneId}, bahasa ${region.localeTag}, lokasi, lalu memeriksa IP keluar."
                        },
                        logLine = "Mengkalibrasi zona waktu ${region.timezoneId} & lokasi ${region.city}"
                    )
                )
                val exitReport = networkRoutingEngine.probeExitIp(
                    effectiveProxy.takeIf { it.isConfigured },
                    region.code
                )
                val routeSummary = when {
                    effectiveProxy.isConfigured && exitReport.matchesTargetRegion ->
                        "Proxy ${effectiveProxy.summary()} • IP ${exitReport.countryCode}"
                    effectiveProxy.isConfigured ->
                        "Proxy ${effectiveProxy.summary()} • IP ${exitReport.countryCode.ifBlank { "tidak diketahui" }} (belum ${region.code})"
                    exitReport.matchesTargetRegion ->
                        "IP publik ${exitReport.countryCode} sudah sesuai ${region.code}"
                    else ->
                        "IP publik ${exitReport.countryCode.ifBlank { "tidak diketahui" }} (bukan ${region.code})"
                }

                val partitionResult = cryptoManager.provisionNativeSandboxPartition(
                    cloneId = cloneId,
                    packageName = app.packageName,
                    sourceApkPath = appInfo?.sourceDir,
                    profile = virtualProfile,
                    region = region
                )
                delay(360)

                // Stage 5: Encrypt Virtual Profile, Proxy Credentials & Empty Cookie Vault
                val proxyLabel = if (effectiveProxy.isConfigured) effectiveProxy.summary() else "tanpa proxy"
                emit(
                    emitStep(
                        stepIndex = 5,
                        title = "Menyegel Profil & Rute Jaringan",
                        detail = "Menyimpan profil ${virtualProfile.model} ke brankas terenkripsi. Rute: $routeSummary.",
                        logLine = "Profil ${virtualProfile.model} & rute $proxyLabel tersimpan aman"
                    )
                )
                val profileJson = cryptoManager.serializeProfileToJson(virtualProfile)
                val profileWrite = cryptoManager.encryptToVault(cloneId, "profile", profileJson.toByteArray(Charsets.UTF_8))
                val cookieWrite = cryptoManager.encryptToVault(cloneId, "cookies", "".toByteArray(Charsets.UTF_8))
                val proxyWrite = cryptoManager.saveEncryptedProxy(cloneId, effectiveProxy)
                val totalVaultBytes = profileWrite.sizeBytes + cookieWrite.sizeBytes + proxyWrite.sizeBytes + partitionResult.partitionSizeBytes
                delay(360)

                // Stage 6: Persist locally & optionally sync to Cloud Firestore (if signed in)
                val syncMessage = if (cloudRepository != null) {
                    "Menyimpan aplikasi kloning dan menyinkronkan cadangan cloud."
                } else {
                    "Menyimpan ruang kloning terenkripsi penuh di perangkat."
                }
                emit(
                    emitStep(
                        stepIndex = 6,
                        title = "Menyelesaikan Pemasangan",
                        detail = syncMessage,
                        logLine = "Siap dijalankan terpisah sebagai ${app.appName} by Clonix"
                    )
                )

                val existingCount = dao.countInstancesForPackage(app.packageName)
                val instanceNumber = existingCount + 1
                val finalLabel = customLabel.trim().ifBlank {
                    "${app.appName} • ${region.code} ${platform.badge} #$instanceNumber"
                }
                val cleanUrl = customPortalUrl.trim().ifBlank { app.defaultWebPortalUrl }

                val entity = ClonedAppEntity(
                    id = cloneId,
                    packageName = app.packageName,
                    originalAppName = app.appName,
                    cloneLabel = finalLabel,
                    instanceNumber = instanceNumber,
                    devicePlatform = platform.name,
                    regionCode = region.code,
                    regionName = region.displayName,
                    city = region.city,
                    timezoneId = region.timezoneId,
                    localeTag = region.localeTag,
                    carrierName = region.carrierName,
                    mccMnc = virtualProfile.mccMnc,
                    virtualModel = virtualProfile.model,
                    virtualManufacturer = virtualProfile.manufacturer,
                    virtualAndroidId = virtualProfile.virtualAndroidId,
                    virtualSerial = virtualProfile.serialNumber,
                    virtualFingerprint = virtualProfile.buildFingerprint,
                    virtualFingerprintHash = fpHash,
                    virtualMacAddress = virtualProfile.macAddress,
                    virtualAdvertisingId = virtualProfile.advertisingId,
                    virtualImei = virtualProfile.virtualImei,
                    virtualImsi = virtualProfile.virtualImsi,
                    virtualGsfId = virtualProfile.virtualGsfId,
                    virtualWidevineId = virtualProfile.virtualWidevineId,
                    apkDigestSha256 = partitionResult.apkDigestSha256,
                    sandboxContainerPath = partitionResult.containerPath,
                    encryptedVaultPath = profileWrite.filePath,
                    sandboxSizeBytes = totalVaultBytes,
                    launchMode = launchMode.name,
                    targetWebUrl = cleanUrl,
                    proxyEnabled = effectiveProxy.isConfigured,
                    proxyType = effectiveProxy.type.name,
                    proxyHost = effectiveProxy.host.trim(),
                    proxyPort = if (effectiveProxy.isConfigured) effectiveProxy.port else 0,
                    preferDesktopWeb = preferDesktopWeb,
                    isEncrypted = keystoreReady,
                    isAntiDetectActive = true,
                    isLocked = false,
                    createdAt = System.currentTimeMillis(),
                    lastLaunchedAt = null
                )

                dao.insertClone(entity)
                val auditTitle = "Kloning Dibuat: $finalLabel"
                val auditDetail = "${platform.label} ${virtualProfile.model} | Negara ${region.code} | $routeSummary"
                dao.insertAuditLog(
                    SandboxAuditLogEntity(
                        cloneId = cloneId,
                        eventType = "PROVISION",
                        title = auditTitle,
                        detail = auditDetail
                    )
                )

                cloudRepository?.syncCloneToCloud(entity)
                cloudRepository?.pushAuditLog(cloneId, "PROVISION", auditTitle, auditDetail)

                delay(280)

                emit(
                    ProvisioningUiState.Success(
                        cloneId = cloneId,
                        cloneLabel = finalLabel,
                        regionCode = region.code,
                        platform = platform,
                        virtualModel = virtualProfile.model,
                        virtualAndroidId = virtualProfile.virtualAndroidId
                    )
                )
            }
        } catch (t: Throwable) {
            emit(ProvisioningUiState.Error(t.message ?: "Gagal mengonfigurasi kontainer virtual"))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun probeExitIp(proxyConfig: ProxyConfig?, targetRegionCode: String): NetworkExitReport =
        networkRoutingEngine.probeExitIp(proxyConfig, targetRegionCode)

    fun loadEncryptedProxy(cloneId: String): ProxyConfig = cryptoManager.loadEncryptedProxy(cloneId)

    suspend fun updateCloneProxy(cloneId: String, proxyConfig: ProxyConfig): ClonedAppEntity? =
        withContext(Dispatchers.IO) {
            val current = dao.getCloneById(cloneId) ?: return@withContext null
            cryptoManager.saveEncryptedProxy(cloneId, proxyConfig)
            val updated = current.copy(
                proxyEnabled = proxyConfig.isConfigured,
                proxyType = proxyConfig.type.name,
                proxyHost = proxyConfig.host.trim(),
                proxyPort = if (proxyConfig.isConfigured) proxyConfig.port else 0
            )
            dao.updateClone(updated)
            cloudRepository?.syncCloneToCloud(updated)
            updated
        }

    suspend fun updateCloneLaunchMode(cloneId: String, launchMode: LaunchMode): ClonedAppEntity? =
        withContext(Dispatchers.IO) {
            val current = dao.getCloneById(cloneId) ?: return@withContext null
            val updated = current.copy(launchMode = launchMode.name)
            dao.updateClone(updated)
            cloudRepository?.syncCloneToCloud(updated)
            updated
        }

    suspend fun loadDecryptedProfile(clone: ClonedAppEntity): VirtualDeviceProfile = withContext(Dispatchers.IO) {
        val region = VirtualRegionCatalog.findByCode(clone.regionCode)
        val platform = runCatching { DevicePlatform.valueOf(clone.devicePlatform) }
            .getOrDefault(DevicePlatform.ANDROID)
        val rawBytes = cryptoManager.decryptFromVault(clone.id, "profile")
        val sourceList = if (platform == DevicePlatform.IOS) region.iosModels else region.deviceModels
        val preset = sourceList.firstOrNull { it.model == clone.virtualModel } ?: sourceList.first()

        val jsonObj = rawBytes?.let {
            runCatching { org.json.JSONObject(it.toString(Charsets.UTF_8)) }.getOrNull()
        }

        val defaultUa = if (platform == DevicePlatform.IOS) {
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_3_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Mobile/15E148 Safari/604.1"
        } else {
            "Mozilla/5.0 (Linux; Android ${preset.androidVersion}; ${clone.virtualModel}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.6943.120 Mobile Safari/537.36"
        }
        val defaultDesktopUa = if (platform == DevicePlatform.IOS) {
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7_2) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Safari/605.1.15"
        } else {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.6943.120 Safari/537.36"
        }

        VirtualDeviceProfile(
            devicePlatform = platform,
            virtualAndroidId = clone.virtualAndroidId,
            manufacturer = clone.virtualManufacturer,
            brand = preset.brand,
            model = clone.virtualModel,
            deviceCode = preset.deviceCode,
            board = preset.board,
            hardware = preset.hardware,
            bootloader = jsonObj?.optString("bootloader", "${preset.deviceCode}-CX") ?: "${preset.deviceCode}-CX",
            serialNumber = clone.virtualSerial,
            buildFingerprint = clone.virtualFingerprint,
            androidVersion = preset.androidVersion,
            sdkInt = preset.sdkInt,
            advertisingId = clone.virtualAdvertisingId,
            macAddress = clone.virtualMacAddress,
            virtualImei = jsonObj?.optString("virtualImei", clone.virtualImei).orEmpty().ifBlank { clone.virtualImei },
            virtualImsi = jsonObj?.optString("virtualImsi", clone.virtualImsi).orEmpty().ifBlank { clone.virtualImsi },
            virtualIccid = jsonObj?.optString("virtualIccid", "").orEmpty(),
            virtualGsfId = jsonObj?.optString("virtualGsfId", clone.virtualGsfId).orEmpty().ifBlank { clone.virtualGsfId },
            virtualWidevineId = jsonObj?.optString("virtualWidevineId", clone.virtualWidevineId).orEmpty().ifBlank { clone.virtualWidevineId },
            virtualWifiSsid = jsonObj?.optString("virtualWifiSsid", "${region.code}-Private-5G").orEmpty(),
            virtualWifiBssid = jsonObj?.optString("virtualWifiBssid", clone.virtualMacAddress).orEmpty(),
            deviceActivatedAt = jsonObj?.optLong("deviceActivatedAt", clone.createdAt) ?: clone.createdAt,
            userAgent = jsonObj?.optString("userAgent", defaultUa) ?: defaultUa,
            desktopUserAgent = jsonObj?.optString("desktopUserAgent", defaultDesktopUa) ?: defaultDesktopUa,
            webglVendor = jsonObj?.optString("webglVendor", if (platform == DevicePlatform.IOS) "Apple Inc." else "Qualcomm") ?: "Qualcomm",
            webglRenderer = jsonObj?.optString("webglRenderer", if (platform == DevicePlatform.IOS) "Apple GPU" else "Adreno (TM) 750") ?: "Adreno (TM) 750",
            navigatorPlatform = jsonObj?.optString("navigatorPlatform", if (platform == DevicePlatform.IOS) "iPhone" else "Linux armv8l") ?: "Linux armv8l",
            navigatorVendor = jsonObj?.optString("navigatorVendor", if (platform == DevicePlatform.IOS) "Apple Computer, Inc." else "Google Inc.") ?: "Google Inc.",
            maxTouchPoints = jsonObj?.optInt("maxTouchPoints", 5) ?: 5,
            screenWidth = preset.screenWidth,
            screenHeight = preset.screenHeight,
            densityDpi = preset.densityDpi,
            regionCode = region.code,
            localeTag = clone.localeTag,
            timezoneId = clone.timezoneId,
            carrierName = clone.carrierName,
            mccMnc = clone.mccMnc,
            latitude = region.latitude,
            longitude = region.longitude
        )
    }

    suspend fun saveEncryptedSessionCookies(cloneId: String, cookieData: String) = withContext(Dispatchers.IO) {
        val cookieRes = cryptoManager.encryptToVault(cloneId, "cookies", cookieData.toByteArray(Charsets.UTF_8))
        val profileSize = cryptoManager.decryptFromVault(cloneId, "profile")?.size?.toLong() ?: 512L
        val proxySize = cryptoManager.decryptFromVault(cloneId, "proxy")?.size?.toLong() ?: 128L
        dao.updateLaunchStats(cloneId, System.currentTimeMillis(), cookieRes.sizeBytes + profileSize + proxySize)
    }

    suspend fun loadEncryptedSessionCookies(cloneId: String): String = withContext(Dispatchers.IO) {
        cryptoManager.decryptFromVault(cloneId, "cookies")?.toString(Charsets.UTF_8).orEmpty()
    }

    suspend fun markCloneLaunched(cloneId: String) = withContext(Dispatchers.IO) {
        val existing = dao.getCloneById(cloneId) ?: return@withContext
        dao.updateLaunchStats(cloneId, System.currentTimeMillis(), existing.sandboxSizeBytes)
        val detail = "${existing.devicePlatform} ${existing.virtualModel} | Region ${existing.regionCode} | ID ${existing.virtualAndroidId}"
        dao.insertAuditLog(
            SandboxAuditLogEntity(
                cloneId = cloneId,
                eventType = "LAUNCH",
                title = "Sesi Dibuka: ${existing.cloneLabel}",
                detail = detail
            )
        )
        cloudRepository?.pushAuditLog(cloneId, "LAUNCH", "Sesi Dibuka: ${existing.cloneLabel}", detail)
    }

    suspend fun regenerateVirtualIdentity(
        cloneId: String,
        newRegionCode: String? = null,
        newPlatform: DevicePlatform? = null
    ): ClonedAppEntity? = withContext(Dispatchers.IO) {
        val current = dao.getCloneById(cloneId) ?: return@withContext null
        // Native clones have no web identity to rotate: their separation comes from the work
        // profile, and IMEI/Build cannot be changed without root.
        if (LaunchMode.fromStored(current.launchMode).isNative) return@withContext current
        val region = VirtualRegionCatalog.findByCode(newRegionCode ?: current.regionCode)
        val platform = newPlatform ?: runCatching { DevicePlatform.valueOf(current.devicePlatform) }
            .getOrDefault(DevicePlatform.ANDROID)
        val newProfile = cryptoManager.synthesizeVirtualIdentity(
            region = region,
            hostBaseline = cryptoManager.getHostDeviceBaseline(),
            platform = platform
        )
        val sourceApkPath = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(current.packageName, PackageManager.ApplicationInfoFlags.of(0L)).sourceDir
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(current.packageName, 0).sourceDir
            }
        }.getOrNull()
        val partitionRes = cryptoManager.provisionNativeSandboxPartition(
            cloneId = cloneId,
            packageName = current.packageName,
            sourceApkPath = sourceApkPath,
            profile = newProfile,
            region = region
        )

        val profileJson = cryptoManager.serializeProfileToJson(newProfile)
        val writeRes = cryptoManager.encryptToVault(cloneId, "profile", profileJson.toByteArray(Charsets.UTF_8))
        val fpHash = cryptoManager.sha256Hex(newProfile.buildFingerprint).take(16).uppercase()

        val updated = current.copy(
            devicePlatform = platform.name,
            regionCode = region.code,
            regionName = region.displayName,
            city = region.city,
            timezoneId = region.timezoneId,
            localeTag = region.localeTag,
            carrierName = region.carrierName,
            mccMnc = newProfile.mccMnc,
            virtualModel = newProfile.model,
            virtualManufacturer = newProfile.manufacturer,
            virtualAndroidId = newProfile.virtualAndroidId,
            virtualSerial = newProfile.serialNumber,
            virtualFingerprint = newProfile.buildFingerprint,
            virtualFingerprintHash = fpHash,
            virtualMacAddress = newProfile.macAddress,
            virtualAdvertisingId = newProfile.advertisingId,
            virtualImei = newProfile.virtualImei,
            virtualImsi = newProfile.virtualImsi,
            virtualGsfId = newProfile.virtualGsfId,
            virtualWidevineId = newProfile.virtualWidevineId,
            apkDigestSha256 = partitionRes.apkDigestSha256,
            sandboxContainerPath = partitionRes.containerPath,
            encryptedVaultPath = writeRes.filePath,
            sandboxSizeBytes = writeRes.sizeBytes + partitionRes.partitionSizeBytes
        )
        dao.updateClone(updated)
        val detail = "${platform.label} ${newProfile.model} | IMEI ${newProfile.virtualImei} | Region ${region.code}"
        dao.insertAuditLog(
            SandboxAuditLogEntity(
                cloneId = cloneId,
                eventType = "ROTATE_ID",
                title = "Perangkat Direset Baru: ${updated.cloneLabel}",
                detail = detail
            )
        )
        cloudRepository?.syncCloneToCloud(updated)
        cloudRepository?.pushAuditLog(cloneId, "ROTATE_ID", "Perangkat Direset Baru: ${updated.cloneLabel}", detail)
        updated
    }

    suspend fun deleteClone(clone: ClonedAppEntity) = withContext(Dispatchers.IO) {
        cryptoManager.secureWipeClone(clone.id)
        dao.deleteCloneById(clone.id)
        dao.insertAuditLog(
            SandboxAuditLogEntity(
                cloneId = clone.id,
                eventType = "WIPE",
                title = "Kontainer Dihapus: ${clone.cloneLabel}",
                detail = "Data vault & partisi sandbox ditimpa nol-bit & dihapus permanen."
            )
        )
        cloudRepository?.deleteCloudClone(clone.id)
        cloudRepository?.pushAuditLog(clone.id, "WIPE", "Kontainer Dihapus: ${clone.cloneLabel}", "Vault & record dihapus.")
    }

    suspend fun wipeAllSandboxes() = withContext(Dispatchers.IO) {
        cryptoManager.wipeAllVaultData()
        dao.deleteAllClones()
        dao.clearAuditLogs()
        dao.insertAuditLog(
            SandboxAuditLogEntity(
                cloneId = "SYSTEM",
                eventType = "WIPE_ALL",
                title = "Pembersihan Total Brankas",
                detail = "Semua kontainer terenkripsi dan log sesi dihapus."
            )
        )
        cloudRepository?.pushAuditLog("SYSTEM", "WIPE_ALL", "Pembersihan Total Brankas", "Brankas lokal dibersihkan.")
    }
}
