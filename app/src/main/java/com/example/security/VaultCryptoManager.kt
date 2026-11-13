package com.example.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.data.model.DevicePlatform
import com.example.data.model.ProxyConfig
import com.example.data.model.ProxyType
import com.example.data.model.VirtualDeviceProfile
import com.example.data.model.VirtualModelPreset
import com.example.data.model.VirtualRegion
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class VaultWriteResult(
    val filePath: String,
    val sizeBytes: Long,
    val digestHex: String
)

data class NativePartitionResult(
    val containerPath: String,
    val apkDigestSha256: String,
    val partitionSizeBytes: Long
)

data class HostDeviceBaseline(
    val manufacturer: String,
    val model: String,
    val androidId: String,
    val fingerprint: String,
    val sdkInt: Int
)

data class HostIsolationCapabilities(
    val activeUserProfileCount: Int,
    val hasSecondaryProfile: Boolean,
    val supportsManagedProfileProvisioning: Boolean
)

class VaultCryptoManager(private val context: Context) {

    private val secureRandom = SecureRandom()
    private val vaultDir: File by lazy {
        File(context.filesDir, "clonix_vault").apply {
            if (!exists()) mkdirs()
        }
    }
    private val sandboxesRootDir: File by lazy {
        File(context.filesDir, "clonix_sandboxes").apply {
            if (!exists()) mkdirs()
        }
    }

    fun getHostDeviceBaseline(): HostDeviceBaseline {
        val rawAndroidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty().ifBlank { "host_restricted" }
        return HostDeviceBaseline(
            manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "Android" },
            model = Build.MODEL.orEmpty().ifBlank { "Device" },
            androidId = rawAndroidId,
            fingerprint = Build.FINGERPRINT.orEmpty().ifBlank { "android/generic" },
            sdkInt = Build.VERSION.SDK_INT
        )
    }

    fun inspectHostIsolationCapabilities(): HostIsolationCapabilities {
        val um = context.getSystemService(Context.USER_SERVICE) as? UserManager
        val profileCount = runCatching { um?.userProfiles?.size ?: 1 }.getOrDefault(1)
        val supportsManaged = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)
        return HostIsolationCapabilities(
            activeUserProfileCount = profileCount,
            hasSecondaryProfile = profileCount > 1,
            supportsManagedProfileProvisioning = supportsManaged
        )
    }

    fun isHardwareKeystoreReady(): Boolean {
        return runCatching {
            getOrCreateHardwareKey()
            true
        }.getOrDefault(false)
    }

    private fun resolveVaultKey(): SecretKey {
        return runCatching { getOrCreateHardwareKey() }.getOrElse {
            getOrCreateLocalFallbackKey()
        }
    }

    private fun getOrCreateHardwareKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun getOrCreateLocalFallbackKey(): SecretKey {
        val keyFile = File(vaultDir, ".clonix_local.key")
        val raw = if (keyFile.exists() && keyFile.length() == 32L) {
            keyFile.readBytes()
        } else {
            ByteArray(32).also {
                secureRandom.nextBytes(it)
                keyFile.writeBytes(it)
            }
        }
        return SecretKeySpec(raw, "AES")
    }

    fun encryptToVault(cloneId: String, fileSuffix: String, plainBytes: ByteArray): VaultWriteResult {
        val key = resolveVaultKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainBytes)

        val targetFile = File(vaultDir, "${cloneId}_${fileSuffix}.enc")
        targetFile.outputStream().use { out ->
            out.write(iv.size)
            out.write(iv)
            out.write(cipherBytes)
            out.flush()
        }
        val digest = sha256Hex(cipherBytes)
        return VaultWriteResult(
            filePath = targetFile.absolutePath,
            sizeBytes = targetFile.length(),
            digestHex = digest
        )
    }

    fun decryptFromVault(cloneId: String, fileSuffix: String): ByteArray? {
        val targetFile = File(vaultDir, "${cloneId}_${fileSuffix}.enc")
        if (!targetFile.exists()) return null
        return runCatching {
            val allBytes = targetFile.readBytes()
            if (allBytes.size < 14) return null
            val ivLen = allBytes[0].toInt() and 0xFF
            if (allBytes.size <= 1 + ivLen) return null
            val iv = allBytes.copyOfRange(1, 1 + ivLen)
            val cipherBytes = allBytes.copyOfRange(1 + ivLen, allBytes.size)
            val key = resolveVaultKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.doFinal(cipherBytes)
        }.getOrNull()
    }

    fun provisionNativeSandboxPartition(
        cloneId: String,
        packageName: String,
        sourceApkPath: String?,
        profile: VirtualDeviceProfile,
        region: VirtualRegion
    ): NativePartitionResult {
        val cloneSandboxDir = File(sandboxesRootDir, cloneId).apply { mkdirs() }
        val envDir = File(cloneSandboxDir, "env").apply { mkdirs() }
        val dataDir = File(cloneSandboxDir, "data").apply { mkdirs() }
        File(dataDir, "shared_prefs").mkdirs()
        File(dataDir, "databases").mkdirs()
        File(dataDir, "cache").mkdirs()

        val apkDigest = if (!sourceApkPath.isNullOrBlank()) {
            computeFileSha256(File(sourceApkPath)).ifBlank { sha256Hex("$packageName:${profile.virtualAndroidId}") }
        } else {
            sha256Hex("virtual:$packageName:${profile.virtualAndroidId}")
        }

        val buildProp = buildString {
            appendLine("# Clonix Isolated Virtual Device Environment")
            appendLine("ro.product.brand=${profile.brand}")
            appendLine("ro.product.manufacturer=${profile.manufacturer}")
            appendLine("ro.product.model=${profile.model}")
            appendLine("ro.product.device=${profile.deviceCode}")
            appendLine("ro.product.board=${profile.board}")
            appendLine("ro.hardware=${profile.hardware}")
            appendLine("ro.bootloader=${profile.bootloader}")
            appendLine("ro.serialno=${profile.serialNumber}")
            appendLine("ro.build.fingerprint=${profile.buildFingerprint}")
            appendLine("ro.build.version.release=${profile.androidVersion}")
            appendLine("ro.build.version.sdk=${profile.sdkInt}")
            appendLine("persist.sys.timezone=${region.timezoneId}")
            appendLine("persist.sys.locale=${region.localeTag}")
            appendLine("gsm.sim.operator.alpha=${region.carrierName}")
            appendLine("gsm.sim.operator.numeric=${region.mcc}${region.mnc}")
            appendLine("gsm.operator.iso-country=${region.code.lowercase()}")
            appendLine("clonix.virtual.android_id=${profile.virtualAndroidId}")
            appendLine("clonix.virtual.imei=${profile.virtualImei}")
            appendLine("clonix.virtual.imsi=${profile.virtualImsi}")
            appendLine("clonix.virtual.iccid=${profile.virtualIccid}")
            appendLine("clonix.virtual.gsf_id=${profile.virtualGsfId}")
            appendLine("clonix.virtual.widevine_id=${profile.virtualWidevineId}")
            appendLine("clonix.apk.sha256=$apkDigest")
        }

        val propWrite = encryptToVault(cloneId, "buildprop", buildProp.toByteArray(Charsets.UTF_8))

        val manifestFile = File(envDir, "container_manifest.json")
        val manifestJson = JSONObject().apply {
            put("cloneId", cloneId)
            put("packageName", packageName)
            put("platform", profile.devicePlatform.name)
            put("regionCode", region.code)
            put("apkSha256", apkDigest)
            put("activatedAt", profile.deviceActivatedAt)
            put("propVaultDigest", propWrite.digestHex)
        }.toString(2)
        manifestFile.writeText(manifestJson)

        return NativePartitionResult(
            containerPath = cloneSandboxDir.absolutePath,
            apkDigestSha256 = apkDigest,
            partitionSizeBytes = propWrite.sizeBytes + manifestFile.length()
        )
    }

    private fun computeFileSha256(file: File): String {
        if (!file.exists() || !file.canRead()) return ""
        return runCatching {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(16384)
            file.inputStream().use { input ->
                var read = input.read(buf)
                var total = 0L
                while (read > 0 && total < 24L * 1024L * 1024L) {
                    md.update(buf, 0, read)
                    total += read
                    read = input.read(buf)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }.getOrDefault("")
    }

    fun saveEncryptedProxy(cloneId: String, proxyConfig: ProxyConfig): VaultWriteResult {
        val json = JSONObject().apply {
            put("type", proxyConfig.type.name)
            put("host", proxyConfig.host.trim())
            put("port", proxyConfig.port)
            put("username", proxyConfig.username)
            put("password", proxyConfig.password)
        }.toString()
        return encryptToVault(cloneId, "proxy", json.toByteArray(Charsets.UTF_8))
    }

    fun loadEncryptedProxy(cloneId: String): ProxyConfig {
        val raw = decryptFromVault(cloneId, "proxy")?.toString(Charsets.UTF_8) ?: return ProxyConfig()
        return runCatching {
            val obj = JSONObject(raw)
            val type = runCatching { ProxyType.valueOf(obj.optString("type", "DIRECT")) }
                .getOrDefault(ProxyType.DIRECT)
            ProxyConfig(
                type = type,
                host = obj.optString("host", ""),
                port = obj.optInt("port", 0),
                username = obj.optString("username", ""),
                password = obj.optString("password", "")
            )
        }.getOrDefault(ProxyConfig())
    }

    fun secureWipeClone(cloneId: String) {
        val files = vaultDir.listFiles { _, name -> name.startsWith("${cloneId}_") }
        files?.forEach { file ->
            runCatching {
                val len = file.length().toInt().coerceAtMost(2 * 1024 * 1024)
                if (len > 0) file.writeBytes(ByteArray(len))
                file.delete()
            }
        }
        val sandboxDir = File(sandboxesRootDir, cloneId)
        if (sandboxDir.exists()) {
            sandboxDir.walkBottomUp().forEach { entry ->
                runCatching {
                    if (entry.isFile) {
                        val len = entry.length().toInt().coerceAtMost(512 * 1024)
                        if (len > 0) entry.writeBytes(ByteArray(len))
                    }
                    entry.delete()
                }
            }
        }
    }

    fun wipeAllVaultData() {
        vaultDir.listFiles()?.forEach { file ->
            if (!file.name.startsWith(".")) {
                runCatching {
                    val len = file.length().toInt().coerceAtMost(2 * 1024 * 1024)
                    if (len > 0) file.writeBytes(ByteArray(len))
                    file.delete()
                }
            }
        }
        sandboxesRootDir.walkBottomUp().forEach { entry ->
            if (entry != sandboxesRootDir) {
                runCatching { entry.delete() }
            }
        }
    }

    fun getTotalVaultBytes(): Long {
        val vaultBytes = vaultDir.listFiles()
            ?.filter { !it.name.startsWith(".") }
            ?.sumOf { it.length() } ?: 0L
        val sandboxBytes = sandboxesRootDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
        return vaultBytes + sandboxBytes
    }

    fun synthesizeVirtualIdentity(
        region: VirtualRegion,
        hostBaseline: HostDeviceBaseline,
        platform: DevicePlatform = DevicePlatform.ANDROID,
        preferredModelIndex: Int? = null
    ): VirtualDeviceProfile {
        val sourceList = if (platform == DevicePlatform.IOS) region.iosModels else region.deviceModels
        val candidates = sourceList.filterNot {
            it.model.equals(hostBaseline.model, ignoreCase = true)
        }.ifEmpty { sourceList }

        val preset: VirtualModelPreset = if (preferredModelIndex != null && preferredModelIndex in candidates.indices) {
            candidates[preferredModelIndex]
        } else {
            candidates[secureRandom.nextInt(candidates.size)]
        }

        var virtualDeviceId: String
        do {
            virtualDeviceId = if (platform == DevicePlatform.IOS) {
                UUID.randomUUID().toString().uppercase()
            } else {
                randomHex(16)
            }
        } while (virtualDeviceId.equals(hostBaseline.androidId, ignoreCase = true))

        val serial = if (platform == DevicePlatform.IOS) {
            "F2L" + randomAlphaNum(9)
        } else {
            "CX" + randomAlphaNum(10)
        }

        val bootloader = if (platform == DevicePlatform.IOS) {
            "iBoot-${10151 + secureRandom.nextInt(900)}.${1 + secureRandom.nextInt(90)}"
        } else {
            "${preset.deviceCode}-${randomAlphaNum(6)}-${preset.sdkInt}"
        }

        val buildFingerprint = if (platform == DevicePlatform.IOS) {
            "Apple/${preset.deviceCode}/${preset.board}:iOS/${preset.androidVersion}/${preset.buildId}:user/release"
        } else {
            "${preset.brand}/${preset.deviceCode}/${preset.deviceCode}:" +
                "${preset.androidVersion}/${preset.buildId}/${randomDigits(8)}:user/release-keys"
        }

        val macAddress = randomMacAddress()
        val wifiBssid = randomMacAddress()
        val wifiSsid = when (region.code) {
            "US" -> "Xfinity-${randomAlphaNum(4)}"
            "GB" -> "BT-Hub-${randomAlphaNum(4)}"
            "JP" -> "NTT-Flets-${randomAlphaNum(4)}"
            "SG" -> "Singtel-5G-${randomAlphaNum(4)}"
            "DE" -> "FRITZ!Box-${randomAlphaNum(4)}"
            "KR" -> "KT_GiGA_5G_${randomAlphaNum(4)}"
            "AU" -> "Telstra-${randomAlphaNum(4)}"
            else -> "Biznet-Home-${randomAlphaNum(4)}"
        }

        val adId = UUID.randomUUID().toString().let { if (platform == DevicePlatform.IOS) it.uppercase() else it }
        val imei = generateLuhnImei(if (platform == DevicePlatform.IOS) "35881209" else "35693811")
        val imsi = "${region.mcc}${region.mnc.padEnd(3, '0').take(3)}${randomDigits(9)}"
        val iccid = "89${region.mcc}${randomDigits(14)}"
        val gsfId = randomHex(16)
        val widevineId = randomHex(32)
        val activatedAt = System.currentTimeMillis()

        val userAgent = if (platform == DevicePlatform.IOS) {
            val iosUnderscore = preset.androidVersion.replace(".", "_")
            "Mozilla/5.0 (iPhone; CPU iPhone OS $iosUnderscore like Mac OS X) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.3 Mobile/15E148 Safari/604.1"
        } else {
            val chromeVersion = "133.0.${6900 + secureRandom.nextInt(90)}.${80 + secureRandom.nextInt(120)}"
            "Mozilla/5.0 (Linux; Android ${preset.androidVersion}; ${preset.model}) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chromeVersion Mobile Safari/537.36"
        }

        val desktopUserAgent = if (platform == DevicePlatform.IOS) {
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7_2) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/18.3 Safari/605.1.15"
        } else {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/133.0.6943.120 Safari/537.36"
        }

        val latJitter = (secureRandom.nextDouble() - 0.5) * 0.03
        val lonJitter = (secureRandom.nextDouble() - 0.5) * 0.03

        return VirtualDeviceProfile(
            devicePlatform = platform,
            virtualAndroidId = virtualDeviceId,
            manufacturer = preset.manufacturer,
            brand = preset.brand,
            model = preset.model,
            deviceCode = preset.deviceCode,
            board = preset.board,
            hardware = preset.hardware,
            bootloader = bootloader,
            serialNumber = serial,
            buildFingerprint = buildFingerprint,
            androidVersion = preset.androidVersion,
            sdkInt = preset.sdkInt,
            advertisingId = adId,
            macAddress = macAddress,
            virtualImei = imei,
            virtualImsi = imsi,
            virtualIccid = iccid,
            virtualGsfId = gsfId,
            virtualWidevineId = widevineId,
            virtualWifiSsid = wifiSsid,
            virtualWifiBssid = wifiBssid,
            deviceActivatedAt = activatedAt,
            userAgent = userAgent,
            desktopUserAgent = desktopUserAgent,
            webglVendor = if (platform == DevicePlatform.IOS) "Apple Inc." else "Qualcomm",
            webglRenderer = if (platform == DevicePlatform.IOS) "Apple GPU" else "Adreno (TM) 750",
            navigatorPlatform = if (platform == DevicePlatform.IOS) "iPhone" else "Linux armv8l",
            navigatorVendor = if (platform == DevicePlatform.IOS) "Apple Computer, Inc." else "Google Inc.",
            maxTouchPoints = 5,
            screenWidth = preset.screenWidth,
            screenHeight = preset.screenHeight,
            densityDpi = preset.densityDpi,
            regionCode = region.code,
            localeTag = region.localeTag,
            timezoneId = region.timezoneId,
            carrierName = region.carrierName,
            mccMnc = "${region.mcc}-${region.mnc}",
            latitude = region.latitude + latJitter,
            longitude = region.longitude + lonJitter
        )
    }

    /**
     * Minimal encrypted record for native (work profile) clones. It documents what the
     * clone actually is, instead of storing web-identity fields that no native app reads.
     */
    fun serializeNativeProfileMarker(packageName: String, host: HostDeviceBaseline): String =
        JSONObject().apply {
            put("mode", "WORK_PROFILE")
            put("packageName", packageName)
            put("hostManufacturer", host.manufacturer)
            put("hostModel", host.model)
            put("hostFingerprint", host.fingerprint)
            put("androidIdScope", "per work profile user")
            put("note", "Android ID, akun, dan storage mengikuti profil kerja; IMEI/Build milik perangkat.")
        }.toString(2)

    fun serializeProfileToJson(profile: VirtualDeviceProfile): String {
        return JSONObject().apply {
            put("devicePlatform", profile.devicePlatform.name)
            put("virtualAndroidId", profile.virtualAndroidId)
            put("manufacturer", profile.manufacturer)
            put("brand", profile.brand)
            put("model", profile.model)
            put("deviceCode", profile.deviceCode)
            put("board", profile.board)
            put("hardware", profile.hardware)
            put("bootloader", profile.bootloader)
            put("serialNumber", profile.serialNumber)
            put("buildFingerprint", profile.buildFingerprint)
            put("androidVersion", profile.androidVersion)
            put("sdkInt", profile.sdkInt)
            put("advertisingId", profile.advertisingId)
            put("macAddress", profile.macAddress)
            put("virtualImei", profile.virtualImei)
            put("virtualImsi", profile.virtualImsi)
            put("virtualIccid", profile.virtualIccid)
            put("virtualGsfId", profile.virtualGsfId)
            put("virtualWidevineId", profile.virtualWidevineId)
            put("virtualWifiSsid", profile.virtualWifiSsid)
            put("virtualWifiBssid", profile.virtualWifiBssid)
            put("deviceActivatedAt", profile.deviceActivatedAt)
            put("userAgent", profile.userAgent)
            put("desktopUserAgent", profile.desktopUserAgent)
            put("webglVendor", profile.webglVendor)
            put("webglRenderer", profile.webglRenderer)
            put("navigatorPlatform", profile.navigatorPlatform)
            put("navigatorVendor", profile.navigatorVendor)
            put("maxTouchPoints", profile.maxTouchPoints)
            put("regionCode", profile.regionCode)
            put("localeTag", profile.localeTag)
            put("timezoneId", profile.timezoneId)
            put("carrierName", profile.carrierName)
            put("mccMnc", profile.mccMnc)
            put("latitude", profile.latitude)
            put("longitude", profile.longitude)
        }.toString(2)
    }

    /**
     * Single source of truth for the in-page identity layer. The real implementation
     * lives in [IdentityShield]; this wrapper is kept so existing call sites and tests
     * keep working.
     */
    fun buildAntiDetectJsShim(
        profile: VirtualDeviceProfile,
        region: VirtualRegion,
        desktopMode: Boolean = false,
        isWhatsAppIosMode: Boolean = false,
        strictAntiDetect: Boolean = true
    ): String = IdentityShield.buildShim(
        profile = profile,
        region = region,
        desktopMode = desktopMode,
        isWhatsAppIosMode = isWhatsAppIosMode,
        strictAntiDetect = strictAntiDetect
    )

    private fun generateLuhnImei(tac8: String): String {
        val base14 = tac8.take(8) + randomDigits(6)
        var sum = 0
        for (i in base14.indices) {
            var digit = base14[i] - '0'
            if (i % 2 == 1) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
        }
        val checkDigit = (10 - (sum % 10)) % 10
        return "$base14$checkDigit"
    }

    private fun randomMacAddress(): String {
        val bytes = ByteArray(6).also { secureRandom.nextBytes(it) }
        bytes[0] = ((bytes[0].toInt() and 0xFC) or 0x02).toByte()
        return bytes.joinToString(":") { "%02X".format(it) }
    }

    fun generateSalt(): String = randomHex(16)

    fun hashPin(pin: String, salt: String): String {
        return sha256Hex("$salt:$pin:clonix_vault_v1".toByteArray(Charsets.UTF_8))
    }

    fun sha256Hex(input: String): String = sha256Hex(input.toByteArray(Charsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun randomHex(length: Int): String {
        val chars = "0123456789abcdef"
        return buildString(length) {
            repeat(length) { append(chars[secureRandom.nextInt(chars.length)]) }
        }
    }

    private fun randomAlphaNum(length: Int): String {
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        return buildString(length) {
            repeat(length) { append(chars[secureRandom.nextInt(chars.length)]) }
        }
    }

    private fun randomDigits(length: Int): String {
        val chars = "0123456789"
        return buildString(length) {
            repeat(length) { append(chars[secureRandom.nextInt(chars.length)]) }
        }
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "clonix_vault_master_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
