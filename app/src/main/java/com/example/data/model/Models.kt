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
    IOS("iPhone (iOS)", "iOS")
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

enum class LaunchMode(val label: String, val badge: String) {
    ISOLATED_CONTAINER("Kontainer Web Terisolasi", "SANDBOX"),
    NATIVE_BRIDGE("Ruang Virtual Perangkat", "NATIVE")
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
    val platform: DevicePlatform = DevicePlatform.ANDROID
)

private val defaultIosPresets: List<VirtualModelPreset> = listOf(
    VirtualModelPreset(
        brand = "Apple",
        manufacturer = "Apple",
        model = "iPhone 16 Pro Max",
        deviceCode = "iPhone17,2",
        board = "D94AP",
        hardware = "A18Pro",
        androidVersion = "18.3.1",
        sdkInt = 183,
        buildId = "22D72",
        screenWidth = 1320,
        screenHeight = 2868,
        densityDpi = 460,
        platform = DevicePlatform.IOS
    ),
    VirtualModelPreset(
        brand = "Apple",
        manufacturer = "Apple",
        model = "iPhone 15 Pro",
        deviceCode = "iPhone16,1",
        board = "D83AP",
        hardware = "A17Pro",
        androidVersion = "18.2.1",
        sdkInt = 182,
        buildId = "22C161",
        screenWidth = 1179,
        screenHeight = 2556,
        densityDpi = 460,
        platform = DevicePlatform.IOS
    ),
    VirtualModelPreset(
        brand = "Apple",
        manufacturer = "Apple",
        model = "iPhone 16",
        deviceCode = "iPhone17,3",
        board = "D47AP",
        hardware = "A18",
        androidVersion = "18.3.1",
        sdkInt = 183,
        buildId = "22D72",
        screenWidth = 1179,
        screenHeight = 2556,
        densityDpi = 460,
        platform = DevicePlatform.IOS
    )
)

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
            deviceModels = listOf(
                VirtualModelPreset("Google", "Google", "Pixel 9 Pro", "caiman", "caiman", "caiman", "15", 35, "AP3A.250205.002", 1280, 2856, 480),
                VirtualModelPreset("samsung", "Samsung", "SM-S938U", "pa3q", "sun", "qcom", "15", 35, "AP3A.250105.008", 1440, 3120, 560),
                VirtualModelPreset("motorola", "Motorola", "motorola edge 50 ultra", "eqe", "eqe", "qcom", "14", 34, "U2UX34.56-22", 1220, 2712, 440)
            ),
            iosModels = defaultIosPresets
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
            deviceModels = listOf(
                VirtualModelPreset("Google", "Google", "Pixel 9", "tokay", "tokay", "tokay", "15", 35, "AP3A.250205.002", 1080, 2424, 420),
                VirtualModelPreset("Nothing", "Nothing", "A142", "Pacman", "mt6886", "mt6886", "14", 34, "UP1A.240905.001", 1084, 2412, 400)
            ),
            iosModels = defaultIosPresets
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
            deviceModels = listOf(
                VirtualModelPreset("Sony", "Sony", "XQ-EC44", "pdx245", "pineapple", "qcom", "14", 34, "69.0.A.2.55", 1080, 2340, 420),
                VirtualModelPreset("SHARP", "SHARP", "SH-51E", "leitz", "pineapple", "qcom", "14", 34, "01.00.09", 1260, 2730, 480)
            ),
            iosModels = defaultIosPresets
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
            deviceModels = listOf(
                VirtualModelPreset("samsung", "Samsung", "SM-S926B", "e2s", "s5e9945", "exynos2400", "15", 35, "AP3A.250105.004", 1440, 3120, 560),
                VirtualModelPreset("OPPO", "OPPO", "CPH2659", "OP5D0DL1", "mt6991", "mt6991", "15", 35, "AP3A.241105.008", 1264, 2780, 480)
            ),
            iosModels = defaultIosPresets
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
            deviceModels = listOf(
                VirtualModelPreset("Xiaomi", "Xiaomi", "24129PN74G", "dada", "sun", "qcom", "15", 35, "AQ3A.240829.003", 1200, 2670, 460),
                VirtualModelPreset("vivo", "vivo", "V2408A", "PD2408", "mt6991", "mt6991", "15", 35, "AP3A.241005.015", 1260, 2800, 480)
            ),
            iosModels = defaultIosPresets
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
            deviceModels = listOf(
                VirtualModelPreset("samsung", "Samsung", "SM-A556B", "a55x", "s5e8845", "exynos1480", "14", 34, "UP1A.231005.007", 1080, 2340, 420),
                VirtualModelPreset("OnePlus", "OnePlus", "CPH2653", "OP5CF9L1", "sun", "qcom", "15", 35, "AP3A.241105.008", 1440, 3168, 560)
            ),
            iosModels = defaultIosPresets
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
            deviceModels = listOf(
                VirtualModelPreset("samsung", "Samsung", "SM-S938N", "pa3q", "sun", "qcom", "15", 35, "AP3A.250105.008", 1440, 3120, 560),
                VirtualModelPreset("samsung", "Samsung", "SM-F956N", "q6q", "pineapple", "qcom", "14", 34, "UP1A.231005.007", 1856, 2160, 420)
            ),
            iosModels = defaultIosPresets
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
            deviceModels = listOf(
                VirtualModelPreset("Google", "Google", "Pixel 9 Pro XL", "komodo", "komodo", "komodo", "15", 35, "AP3A.250205.002", 1344, 2992, 512),
                VirtualModelPreset("OPPO", "OPPO", "CPH2607", "OP5961L1", "pineapple", "qcom", "14", 34, "UKQ1.230924.001", 1080, 2412, 420)
            ),
            iosModels = defaultIosPresets
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
    val longitude: Double
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
