package com.example.data.model

import android.graphics.Bitmap

enum class AppCategory(val labelId: String) {
    ALL("Semua"),
    SOCIAL("Sosial"),
    MESSAGING("Pesan"),
    BROWSER("Browser"),
    OTHER("Lainnya")
}

enum class DevicePlatform(val label: String, val badge: String) {
    ANDROID("Android", "Android"),
    IOS("iPhone (emulasi web)", "iOS")
}

enum class ProxyType(val label: String) {
    DIRECT("Langsung (Jaringan / VPN Perangkat)"),
    HTTP("Proxy HTTP / HTTPS"),
    SOCKS5("Proxy SOCKS5")
}

data class ProxyConfig(
    val type: ProxyType = ProxyType.DIRECT,
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = ""
) {
    val isConfigured: Boolean
        get() = type != ProxyType.DIRECT && host.isNotBlank() && port in 1..65535

    fun summary(): String = if (!isConfigured) "Direct" else "${type.name} ${host.trim()}:$port"
}

data class NetworkExitReport(
    val isLoading: Boolean = false,
    val ipAddress: String = "",
    val countryCode: String = "",
    val countryName: String = "",
    val city: String = "",
    val isp: String = "",
    val latencyMs: Long = 0L,
    val viaProxy: Boolean = false,
    val matchesTargetRegion: Boolean = false,
    val errorMessage: String? = null
)

/**
 * How a clone is run.
 *
 * [WORK_PROFILE] gives a real second installation of the app inside an Android work
 * profile (separate user, storage, account and per-profile IDs such as ANDROID_ID).
 * Hardware identity (IMEI, Build, SoC) stays the same, because Android does not let a
 * normal app rewrite those for another app's process.
 *
 * [ISOLATED_CONTAINER] runs the app's web client inside a Clonix container where the
 * browser-level identity (UA, GPU, screen, timezone, locale, geolocation) and the exit
 * route can actually be changed.
 */
enum class LaunchMode(val label: String, val badge: String, val description: String) {
    WORK_PROFILE(
        label = "Profil Kerja (aplikasi asli)",
        badge = "NATIVE",
        description = "Instance aplikasi asli (APK) di profil kerja: satu klon tambahan per aplikasi, dibuka di luar Clonix lewat tab Kerja. IMEI & Build tetap milik perangkat."
    ),
    ISOLATED_CONTAINER(
        label = "Kontainer Clonix (identitas web)",
        badge = "WEB",
        description = "Klon berjalan di dalam Clonix dengan identitas browser & rute negara sendiri. Bisa berkali-kali untuk aplikasi yang sama; butuh proxy/VPN untuk IP negara lain."
    );

    val isNative: Boolean get() = this == WORK_PROFILE

    companion object {
        fun fromStored(raw: String?): LaunchMode =
            if (raw == WORK_PROFILE.name) WORK_PROFILE else ISOLATED_CONTAINER
    }
}

data class VirtualRegion(
    val code: String,
    val displayName: String,
    val localizedName: String,
    val city: String,
    val localeTag: String,
    val acceptLanguage: String,
    val timezoneId: String,
    val timezoneOffsetMinutes: Int,
    val mcc: String,
    val mnc: String,
    val carrierName: String,
    val currencyCode: String,
    val latitude: Double,
    val longitude: Double,
    val deviceModels: List<VirtualModelPreset>,
    val iosModels: List<VirtualModelPreset>
)

data class VirtualModelPreset(
    val brand: String,
    val manufacturer: String,
    val model: String,
    val deviceCode: String,
    val board: String,
    val hardware: String,
    val androidVersion: String,
    val sdkInt: Int,
    val buildId: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val densityDpi: Int,
    val platform: DevicePlatform = DevicePlatform.ANDROID,
    val soc: String = "Snapdragon 8 Elite",
    val gpuVendor: String = "Qualcomm",
    val gpuRenderer: String = "Adreno (TM) 830",
    val cpuCores: Int = 8,
    val ramGb: Int = 12,
    val imeiTac: String = "35182910"
) {
    /** CSS pixels reported by the browser at this density. */
    val cssWidth: Int get() = (screenWidth * 160.0 / densityDpi).toInt()
    val cssHeight: Int get() = (screenHeight * 160.0 / densityDpi).toInt()

    /** Marketing name for the picker; [model] is what the user agent reports. */
    val displayName: String get() = deviceMarketingNames[model] ?: model
}

/** Marketing names only — never used for fingerprint values, which come from [model]. */
private val deviceMarketingNames: Map<String, String> = mapOf(
    "SM-S938B" to "Samsung Galaxy S25 Ultra",
    "SM-S936B" to "Samsung Galaxy S25+",
    "SM-S931B" to "Samsung Galaxy S25",
    "SM-A566B" to "Samsung Galaxy A56 5G",
    "Pixel 9 Pro" to "Google Pixel 9 Pro",
    "Pixel 9a" to "Google Pixel 9a",
    "25010PN30G" to "Xiaomi 15 Ultra",
    "CPH2653" to "OnePlus 13",
    "CPH2659" to "OPPO Find X8 Pro",
    "V2413" to "vivo X200 Pro",
    "XQ-EC44" to "Sony Xperia 1 VI",
    "motorola edge 50 ultra" to "Motorola Edge 50 Ultra",
    "iPhone 16 Pro Max" to "Apple iPhone 16 Pro Max",
    "iPhone 16 Pro" to "Apple iPhone 16 Pro",
    "iPhone 16 Plus" to "Apple iPhone 16 Plus",
    "iPhone 16" to "Apple iPhone 16",
    "iPhone 15 Pro" to "Apple iPhone 15 Pro"
)

/**
 * Shared, coherent device catalog. Every preset carries the SoC/GPU/RAM/IMEI TAC used
 * by the web identity shim, so the reported device is internally consistent instead of
 * a random mix of impossible values.
 */
object VirtualModelCatalog {

    val android: List<VirtualModelPreset> = listOf(
        VirtualModelPreset(
            brand = "samsung", manufacturer = "Samsung", model = "SM-S938B",
            deviceCode = "pa3q", board = "sun", hardware = "qcom",
            androidVersion = "15", sdkInt = 35, buildId = "AP3A.250105.008",
            screenWidth = 1440, screenHeight = 3120, densityDpi = 500,
            soc = "Snapdragon 8 Elite", gpuVendor = "Qualcomm",
            gpuRenderer = "Adreno (TM) 830", cpuCores = 8, ramGb = 12,
            imeiTac = "35182910"
        ),
        VirtualModelPreset(
            brand = "samsung", manufacturer = "Samsung", model = "SM-S936B",
            deviceCode = "e2q", board = "s5e9955", hardware = "exynos",
            androidVersion = "15", sdkInt = 35, buildId = "AP3A.250105.006",
            screenWidth = 1440, screenHeight = 3120, densityDpi = 490,
            soc = "Exynos 2500", gpuVendor = "ARM",
            gpuRenderer = "Xclipse 950", cpuCores = 10, ramGb = 12,
            imeiTac = "35182920"
        ),
        VirtualModelPreset(
            brand = "samsung", manufacturer = "Samsung", model = "SM-S931B",
            deviceCode = "e1q", board = "s5e9955", hardware = "exynos",
            androidVersion = "15", sdkInt = 35, buildId = "AP3A.250105.004",
            screenWidth = 1080, screenHeight = 2340, densityDpi = 425,
            soc = "Exynos 2500", gpuVendor = "ARM",
            gpuRenderer = "Xclipse 950", cpuCores = 10, ramGb = 12,
            imeiTac = "35182930"
        ),
        VirtualModelPreset(
            brand = "samsung", manufacturer = "Samsung", model = "SM-A566B",
            deviceCode = "a56x", board = "s5e8855", hardware = "exynos",
            androidVersion = "15", sdkInt = 35, buildId = "AP3A.250204.006",
            screenWidth = 1080, screenHeight = 2340, densityDpi = 390,
            soc = "Exynos 1580", gpuVendor = "ARM",
            gpuRenderer = "Xclipse 540", cpuCores = 8, ramGb = 8,
            imeiTac = "35261911"
        ),
        VirtualModelPreset(
            brand = "google", manufacturer = "Google", model = "Pixel 9 Pro",
            deviceCode = "caiman", board = "caiman", hardware = "caiman",
            androidVersion = "15", sdkInt = 35, buildId = "AP3A.250205.002",
            screenWidth = 1280, screenHeight = 2856, densityDpi = 495,
            soc = "Google Tensor G4", gpuVendor = "ARM",
            gpuRenderer = "Mali-G715", cpuCores = 8, ramGb = 16,
            imeiTac = "35151234"
        ),
        VirtualModelPreset(
            brand = "google", manufacturer = "Google", model = "Pixel 9a",
            deviceCode = "tegu", board = "tegu", hardware = "tegu",
            androidVersion = "15", sdkInt = 35, buildId = "AP3A.250205.003",
            screenWidth = 1080, screenHeight = 2424, densityDpi = 422,
            soc = "Google Tensor G4", gpuVendor = "ARM",
            gpuRenderer = "Mali-G715", cpuCores = 8, ramGb = 8,
            imeiTac = "35151250"
        ),
        VirtualModelPreset(
            brand = "Xiaomi", manufacturer = "Xiaomi", model = "25010PN30G",
            deviceCode = "xuanyuan", board = "sun", hardware = "qcom",
            androidVersion = "15", sdkInt = 35, buildId = "OS2.0.13.0.VOAEUXM",
            screenWidth = 1440, screenHeight = 3200, densityDpi = 522,
            soc = "Snapdragon 8 Elite", gpuVendor = "Qualcomm",
            gpuRenderer = "Adreno (TM) 830", cpuCores = 8, ramGb = 16,
            imeiTac = "86918203"
        ),
        VirtualModelPreset(
            brand = "OnePlus", manufacturer = "OnePlus", model = "CPH2653",
            deviceCode = "OP5D2FL1", board = "sun", hardware = "qcom",
            androidVersion = "15", sdkInt = 35, buildId = "AP3A.241105.008",
            screenWidth = 1440, screenHeight = 3168, densityDpi = 510,
            soc = "Snapdragon 8 Elite", gpuVendor = "Qualcomm",
            gpuRenderer = "Adreno (TM) 830", cpuCores = 8, ramGb = 16,
            imeiTac = "86451203"
        ),
        VirtualModelPreset(
            brand = "OPPO", manufacturer = "OPPO", model = "CPH2659",
            deviceCode = "OP5D3BL1", board = "mt6991", hardware = "mt6991",
            androidVersion = "15", sdkInt = 35, buildId = "AP3A.241105.008",
            screenWidth = 1264, screenHeight = 2780, densityDpi = 450,
            soc = "MediaTek Dimensity 9400", gpuVendor = "ARM",
            gpuRenderer = "Immortalis-G925", cpuCores = 8, ramGb = 12,
            imeiTac = "86754321"
        ),
        VirtualModelPreset(
            brand = "vivo", manufacturer = "vivo", model = "V2413",
            deviceCode = "PD2413", board = "mt6991", hardware = "mt6991",
            androidVersion = "15", sdkInt = 35, buildId = "AP3A.250102.002",
            screenWidth = 1260, screenHeight = 2800, densityDpi = 452,
            soc = "MediaTek Dimensity 9400", gpuVendor = "ARM",
            gpuRenderer = "Immortalis-G925", cpuCores = 8, ramGb = 16,
            imeiTac = "86123456"
        ),
        VirtualModelPreset(
            brand = "Sony", manufacturer = "Sony", model = "XQ-EC44",
            deviceCode = "pdx245", board = "pineapple", hardware = "qcom",
            androidVersion = "14", sdkInt = 34, buildId = "69.0.A.2.55",
            screenWidth = 1080, screenHeight = 2340, densityDpi = 396,
            soc = "Snapdragon 8 Gen 3", gpuVendor = "Qualcomm",
            gpuRenderer = "Adreno (TM) 750", cpuCores = 8, ramGb = 12,
            imeiTac = "35987102"
        ),
        VirtualModelPreset(
            brand = "motorola", manufacturer = "Motorola", model = "motorola edge 50 ultra",
            deviceCode = "eqe", board = "eqe", hardware = "qcom",
            androidVersion = "14", sdkInt = 34, buildId = "U2UX34.56-22",
            screenWidth = 1220, screenHeight = 2712, densityDpi = 446,
            soc = "Snapdragon 8s Gen 3", gpuVendor = "Qualcomm",
            gpuRenderer = "Adreno (TM) 735", cpuCores = 8, ramGb = 12,
            imeiTac = "35322211"
        )
    )

    val ios: List<VirtualModelPreset> = listOf(
        VirtualModelPreset(
            brand = "Apple", manufacturer = "Apple", model = "iPhone 16 Pro Max",
            deviceCode = "iPhone17,2", board = "D94AP", hardware = "A18Pro",
            androidVersion = "18.3.1", sdkInt = 183, buildId = "22D72",
            screenWidth = 1320, screenHeight = 2868, densityDpi = 460,
            platform = DevicePlatform.IOS, soc = "Apple A18 Pro",
            gpuVendor = "Apple Inc.", gpuRenderer = "Apple GPU",
            cpuCores = 6, ramGb = 8, imeiTac = "35881209"
        ),
        VirtualModelPreset(
            brand = "Apple", manufacturer = "Apple", model = "iPhone 16 Pro",
            deviceCode = "iPhone17,1", board = "D93AP", hardware = "A18Pro",
            androidVersion = "18.3.1", sdkInt = 183, buildId = "22D72",
            screenWidth = 1206, screenHeight = 2622, densityDpi = 460,
            platform = DevicePlatform.IOS, soc = "Apple A18 Pro",
            gpuVendor = "Apple Inc.", gpuRenderer = "Apple GPU",
            cpuCores = 6, ramGb = 8, imeiTac = "35881217"
        ),
        VirtualModelPreset(
            brand = "Apple", manufacturer = "Apple", model = "iPhone 16 Plus",
            deviceCode = "iPhone17,4", board = "D48AP", hardware = "A18",
            androidVersion = "18.3.1", sdkInt = 183, buildId = "22D72",
            screenWidth = 1290, screenHeight = 2796, densityDpi = 460,
            platform = DevicePlatform.IOS, soc = "Apple A18",
            gpuVendor = "Apple Inc.", gpuRenderer = "Apple GPU",
            cpuCores = 6, ramGb = 8, imeiTac = "35881225"
        ),
        VirtualModelPreset(
            brand = "Apple", manufacturer = "Apple", model = "iPhone 16",
            deviceCode = "iPhone17,3", board = "D47AP", hardware = "A18",
            androidVersion = "18.3.1", sdkInt = 183, buildId = "22D72",
            screenWidth = 1179, screenHeight = 2556, densityDpi = 460,
            platform = DevicePlatform.IOS, soc = "Apple A18",
            gpuVendor = "Apple Inc.", gpuRenderer = "Apple GPU",
            cpuCores = 6, ramGb = 8, imeiTac = "35881233"
        ),
        VirtualModelPreset(
            brand = "Apple", manufacturer = "Apple", model = "iPhone 15 Pro",
            deviceCode = "iPhone16,1", board = "D83AP", hardware = "A17Pro",
            androidVersion = "18.2.1", sdkInt = 182, buildId = "22C161",
            screenWidth = 1179, screenHeight = 2556, densityDpi = 460,
            platform = DevicePlatform.IOS, soc = "Apple A17 Pro",
            gpuVendor = "Apple Inc.", gpuRenderer = "Apple GPU",
            cpuCores = 6, ramGb = 8, imeiTac = "35881241"
        )
    )

    fun forPlatform(platform: DevicePlatform): List<VirtualModelPreset> =
        if (platform == DevicePlatform.IOS) ios else android

    fun findByModel(model: String): VirtualModelPreset? =
        (android + ios).firstOrNull { it.model.equals(model, ignoreCase = true) }
}

object VirtualRegionCatalog {
    val regions: List<VirtualRegion> = listOf(
        VirtualRegion(
            code = "US",
            displayName = "United States",
            localizedName = "Amerika Serikat (US)",
            city = "New York, NY",
            localeTag = "en-US",
            acceptLanguage = "en-US,en;q=0.9",
            timezoneId = "America/New_York",
            timezoneOffsetMinutes = 300,
            mcc = "310",
            mnc = "260",
            carrierName = "T-Mobile US",
            currencyCode = "USD",
            latitude = 40.7128,
            longitude = -74.0060,
            deviceModels = VirtualModelCatalog.android,
            iosModels = VirtualModelCatalog.ios
        ),
        VirtualRegion(
            code = "GB",
            displayName = "United Kingdom",
            localizedName = "Inggris (UK)",
            city = "London",
            localeTag = "en-GB",
            acceptLanguage = "en-GB,en;q=0.9",
            timezoneId = "Europe/London",
            timezoneOffsetMinutes = 0,
            mcc = "234",
            mnc = "15",
            carrierName = "Vodafone UK",
            currencyCode = "GBP",
            latitude = 51.5074,
            longitude = -0.1278,
            deviceModels = VirtualModelCatalog.android,
            iosModels = VirtualModelCatalog.ios
        ),
        VirtualRegion(
            code = "JP",
            displayName = "Japan",
            localizedName = "Jepang (JP)",
            city = "Tokyo",
            localeTag = "ja-JP",
            acceptLanguage = "ja-JP,ja;q=0.9,en-US;q=0.7",
            timezoneId = "Asia/Tokyo",
            timezoneOffsetMinutes = -540,
            mcc = "440",
            mnc = "10",
            carrierName = "NTT DOCOMO",
            currencyCode = "JPY",
            latitude = 35.6762,
            longitude = 139.6503,
            deviceModels = VirtualModelCatalog.android,
            iosModels = VirtualModelCatalog.ios
        ),
        VirtualRegion(
            code = "SG",
            displayName = "Singapore",
            localizedName = "Singapura (SG)",
            city = "Singapore",
            localeTag = "en-SG",
            acceptLanguage = "en-SG,en;q=0.9",
            timezoneId = "Asia/Singapore",
            timezoneOffsetMinutes = -480,
            mcc = "525",
            mnc = "01",
            carrierName = "Singtel",
            currencyCode = "SGD",
            latitude = 1.3521,
            longitude = 103.8198,
            deviceModels = VirtualModelCatalog.android,
            iosModels = VirtualModelCatalog.ios
        ),
        VirtualRegion(
            code = "ID",
            displayName = "Indonesia",
            localizedName = "Indonesia (ID)",
            city = "Jakarta",
            localeTag = "id-ID",
            acceptLanguage = "id-ID,id;q=0.9,en-US;q=0.7",
            timezoneId = "Asia/Jakarta",
            timezoneOffsetMinutes = -420,
            mcc = "510",
            mnc = "10",
            carrierName = "Telkomsel",
            currencyCode = "IDR",
            latitude = -6.2088,
            longitude = 106.8456,
            deviceModels = VirtualModelCatalog.android,
            iosModels = VirtualModelCatalog.ios
        ),
        VirtualRegion(
            code = "DE",
            displayName = "Germany",
            localizedName = "Jerman (DE)",
            city = "Berlin",
            localeTag = "de-DE",
            acceptLanguage = "de-DE,de;q=0.9,en;q=0.8",
            timezoneId = "Europe/Berlin",
            timezoneOffsetMinutes = -60,
            mcc = "262",
            mnc = "01",
            carrierName = "Telekom.de",
            currencyCode = "EUR",
            latitude = 52.5200,
            longitude = 13.4050,
            deviceModels = VirtualModelCatalog.android,
            iosModels = VirtualModelCatalog.ios
        ),
        VirtualRegion(
            code = "KR",
            displayName = "South Korea",
            localizedName = "Korea Selatan (KR)",
            city = "Seoul",
            localeTag = "ko-KR",
            acceptLanguage = "ko-KR,ko;q=0.9,en-US;q=0.7",
            timezoneId = "Asia/Seoul",
            timezoneOffsetMinutes = -540,
            mcc = "450",
            mnc = "05",
            carrierName = "SK Telecom",
            currencyCode = "KRW",
            latitude = 37.5665,
            longitude = 126.9780,
            deviceModels = VirtualModelCatalog.android,
            iosModels = VirtualModelCatalog.ios
        ),
        VirtualRegion(
            code = "AU",
            displayName = "Australia",
            localizedName = "Australia (AU)",
            city = "Sydney",
            localeTag = "en-AU",
            acceptLanguage = "en-AU,en;q=0.9",
            timezoneId = "Australia/Sydney",
            timezoneOffsetMinutes = -660,
            mcc = "505",
            mnc = "01",
            carrierName = "Telstra",
            currencyCode = "AUD",
            latitude = -33.8688,
            longitude = 151.2093,
            deviceModels = VirtualModelCatalog.android,
            iosModels = VirtualModelCatalog.ios
        )
    )

    fun findByCode(code: String): VirtualRegion =
        regions.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: regions.first()
}

data class VirtualDeviceProfile(
    val devicePlatform: DevicePlatform,
    val virtualAndroidId: String,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val deviceCode: String,
    val board: String,
    val hardware: String,
    val bootloader: String,
    val serialNumber: String,
    val buildFingerprint: String,
    val androidVersion: String,
    val sdkInt: Int,
    val advertisingId: String,
    val macAddress: String,
    val virtualImei: String,
    val virtualImsi: String,
    val virtualIccid: String,
    val virtualGsfId: String,
    val virtualWidevineId: String,
    val virtualWifiSsid: String,
    val virtualWifiBssid: String,
    val deviceActivatedAt: Long,
    val userAgent: String,
    val desktopUserAgent: String,
    val webglVendor: String,
    val webglRenderer: String,
    val navigatorPlatform: String,
    val navigatorVendor: String,
    val maxTouchPoints: Int,
    val screenWidth: Int,
    val screenHeight: Int,
    val densityDpi: Int,
    val regionCode: String,
    val localeTag: String,
    val timezoneId: String,
    val carrierName: String,
    val mccMnc: String,
    val latitude: Double,
    val longitude: Double,
    val deviceMemoryGb: Int = 8,
    val cpuCores: Int = 8
)

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val apkSizeBytes: Long,
    val category: AppCategory,
    val iconBitmap: Bitmap?,
    val recommendedLaunchMode: LaunchMode,
    val defaultWebPortalUrl: String,
    val isVirtualPreset: Boolean = false
)

sealed interface ProvisioningUiState {
    data object Idle : ProvisioningUiState
    data class Running(
        val appName: String,
        val region: VirtualRegion,
        val platform: DevicePlatform,
        val currentStepIndex: Int,
        val totalSteps: Int,
        val currentTitle: String,
        val currentDetail: String,
        val progress: Float,
        val logs: List<String>
    ) : ProvisioningUiState

    data class Success(
        val cloneId: String,
        val cloneLabel: String,
        val regionCode: String,
        val platform: DevicePlatform,
        val virtualModel: String,
        val virtualAndroidId: String
    ) : ProvisioningUiState

    data class Error(val message: String) : ProvisioningUiState
}
