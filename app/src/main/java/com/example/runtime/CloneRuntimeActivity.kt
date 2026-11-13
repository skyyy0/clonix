package com.example.runtime

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.example.data.local.ClonedAppEntity
import com.example.data.local.ClonixDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.local.UserPreferences
import com.example.data.model.DevicePlatform
import com.example.data.model.NetworkExitReport
import com.example.data.model.ProxyConfig
import com.example.data.model.ProxyType
import com.example.data.model.VirtualDeviceProfile
import com.example.data.model.VirtualRegionCatalog
import com.example.data.repository.ClonixRepository
import com.example.data.repository.InstalledAppsRepository
import com.example.security.VaultCryptoManager
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.ClonixTheme
import com.example.ui.theme.LocalLiquidGlass
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class CloneRuntimeActivity : ComponentActivity() {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeCloneId: String? = null
    private var activeTargetUrl: String = ""
    private var activeCurrentUrl: String = ""
    private var activeProxyApplied: Boolean = false
    private lateinit var repository: ClonixRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = ClonixDatabase.getInstance(applicationContext)
        val crypto = VaultCryptoManager(applicationContext)
        repository = ClonixRepository(applicationContext, db.clonixDao(), crypto)
        val installedRepo = InstalledAppsRepository(applicationContext)
        val prefsRepo = PreferencesRepository(applicationContext)

        val cloneId = intent.getStringExtra(EXTRA_CLONE_ID)
            ?: intent.data?.lastPathSegment
            ?: run {
                finish()
                return
            }
        activeCloneId = cloneId

        setContent {
            val prefs by prefsRepo.preferencesFlow.collectAsStateWithLifecycle(initialValue = UserPreferences())
            ClonixTheme(themeMode = prefs.themeMode) {
                CloneRuntimeContainer(
                    cloneId = cloneId,
                    db = db,
                    repository = repository,
                    installedRepo = installedRepo,
                    onTaskReady = { label, icon ->
                        applyCloneTaskDescription(label, icon)
                    },
                    onUrlChanged = { target, current ->
                        activeTargetUrl = target
                        activeCurrentUrl = current
                    },
                    onProxyStateChanged = { applied ->
                        activeProxyApplied = applied
                    },
                    onCloseTask = {
                        finishAndRemoveTask()
                    }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val id = activeCloneId ?: return
        val baseUrl = activeTargetUrl
        if (baseUrl.isBlank()) return
        val snapshot = runCatching {
            val cm = CookieManager.getInstance()
            val c1 = cm.getCookie(baseUrl).orEmpty()
            val c2 = if (activeCurrentUrl.isNotBlank() && activeCurrentUrl != baseUrl) {
                cm.getCookie(activeCurrentUrl).orEmpty()
            } else ""
            buildString {
                if (c1.isNotBlank()) appendLine("$baseUrl|$c1")
                if (c2.isNotBlank()) appendLine("$activeCurrentUrl|$c2")
            }.trim()
        }.getOrDefault("")
        if (snapshot.isNotEmpty()) {
            ioScope.launch {
                runCatching { repository.saveEncryptedSessionCookies(id, snapshot) }
            }
        }
    }

    override fun onDestroy() {
        if (activeProxyApplied && WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            runCatching {
                ProxyController.getInstance().clearProxyOverride(Executor { it.run() }, Runnable {})
            }
        }
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun applyCloneTaskDescription(appName: String, iconBitmap: Bitmap?) {
        val taskLabel = "$appName by Clonix"
        title = taskLabel
        val taskDesc = if (iconBitmap != null) {
            ActivityManager.TaskDescription(taskLabel, iconBitmap)
        } else {
            ActivityManager.TaskDescription(taskLabel)
        }
        setTaskDescription(taskDesc)
    }

    companion object {
        const val EXTRA_CLONE_ID = "extra_clone_id"

        fun launchCloneTask(context: Context, cloneId: String) {
            val intent = Intent(context, CloneRuntimeActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("clonix://container/$cloneId")
                putExtra(EXTRA_CLONE_ID, cloneId)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }
            context.startActivity(intent)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CloneRuntimeContainer(
    cloneId: String,
    db: ClonixDatabase,
    repository: ClonixRepository,
    installedRepo: InstalledAppsRepository,
    onTaskReady: (String, Bitmap?) -> Unit,
    onUrlChanged: (String, String) -> Unit,
    onProxyStateChanged: (Boolean) -> Unit,
    onCloseTask: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val glass = LocalLiquidGlass.current

    var clone by remember(cloneId) { mutableStateOf<ClonedAppEntity?>(null) }
    var profile by remember(cloneId) { mutableStateOf<VirtualDeviceProfile?>(null) }
    var proxyConfig by remember(cloneId) { mutableStateOf(ProxyConfig()) }
    var desktopMode by remember(cloneId) { mutableStateOf(false) }
    var containerReady by remember(cloneId) { mutableStateOf(false) }
    var loadError by remember(cloneId) { mutableStateOf<String?>(null) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var currentUrl by remember(cloneId) { mutableStateOf("") }
    var showHudDetails by remember { mutableStateOf(false) }
    var exitReport by remember(cloneId) { mutableStateOf<NetworkExitReport?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(cloneId) {
        val loadedClone = db.clonixDao().getCloneById(cloneId)
        if (loadedClone == null) {
            loadError = "Aplikasi kloning tidak ditemukan."
            containerReady = true
            return@LaunchedEffect
        }
        clone = loadedClone
        desktopMode = loadedClone.preferDesktopWeb
        currentUrl = loadedClone.targetWebUrl
        onUrlChanged(loadedClone.targetWebUrl, loadedClone.targetWebUrl)

        val iconBmp = installedRepo.getCachedIcon(loadedClone.packageName)
        onTaskReady(loadedClone.originalAppName, iconBmp)

        repository.markCloneLaunched(loadedClone.id)
        val loadedProfile = repository.loadDecryptedProfile(loadedClone)
        val loadedProxy = repository.loadEncryptedProxy(loadedClone.id)
        profile = loadedProfile
        proxyConfig = loadedProxy

        var proxyApplied = false
        if (loadedProxy.isConfigured && WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            val rule = when (loadedProxy.type) {
                ProxyType.SOCKS5 -> "socks://${loadedProxy.host.trim()}:${loadedProxy.port}"
                ProxyType.HTTP -> "${loadedProxy.host.trim()}:${loadedProxy.port}"
                ProxyType.DIRECT -> ""
            }
            if (rule.isNotEmpty()) {
                val deferred = CompletableDeferred<Unit>()
                val webProxy = androidx.webkit.ProxyConfig.Builder()
                    .addProxyRule(rule)
                    .build()
                runCatching {
                    ProxyController.getInstance().setProxyOverride(
                        webProxy,
                        Executor { it.run() },
                        Runnable { deferred.complete(Unit) }
                    )
                    deferred.await()
                    proxyApplied = true
                }
            }
        }
        onProxyStateChanged(proxyApplied)

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.removeAllCookies(null)
        cm.flush()

        val savedCookies = repository.loadEncryptedSessionCookies(loadedClone.id)
        if (savedCookies.isNotBlank()) {
            savedCookies.split("\n").forEach { line ->
                val parts = line.split("|", limit = 2)
                if (parts.size == 2) {
                    val domainUrl = parts[0]
                    parts[1].split(";").forEach { cookiePair ->
                        val trimmed = cookiePair.trim()
                        if (trimmed.isNotEmpty()) cm.setCookie(domainUrl, trimmed)
                    }
                }
            }
            cm.flush()
        }
        containerReady = true
    }

    fun sealAndExit() {
        scope.launch {
            val activeClone = clone
            if (activeClone != null) {
                val cm = CookieManager.getInstance()
                val baseCookie = cm.getCookie(activeClone.targetWebUrl).orEmpty()
                val curCookie = if (currentUrl.isNotBlank()) cm.getCookie(currentUrl).orEmpty() else ""
                val snapshot = buildString {
                    if (baseCookie.isNotBlank()) appendLine("${activeClone.targetWebUrl}|$baseCookie")
                    if (curCookie.isNotBlank() && currentUrl != activeClone.targetWebUrl) {
                        appendLine("$currentUrl|$curCookie")
                    }
                }.trim()
                repository.saveEncryptedSessionCookies(activeClone.id, snapshot)
                cm.removeAllCookies(null)
                cm.flush()
                WebStorage.getInstance().deleteAllData()
                webViewRef?.clearCache(true)
                webViewRef?.destroy()
            }
            onCloseTask()
        }
    }

    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            sealAndExit()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
        }
    }

    val activeClone = clone
    val activeProfile = profile
    val isWhatsApp = activeClone?.packageName?.contains("whatsapp", ignoreCase = true) == true
    val isIos = activeProfile?.devicePlatform == DevicePlatform.IOS
    val isWhatsAppIos = isWhatsApp && isIos
    val region = remember(activeClone?.regionCode) {
        VirtualRegionCatalog.findByCode(activeClone?.regionCode ?: "US")
    }

    val iosBlue = Color(0xFF007AFF)
    val iosGreen = Color(0xFF25D366)

    LiquidGlassBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            LiquidGlassSurface(
                shape = RoundedCornerShape(bottomStart = if (isWhatsAppIos) 18.dp else 22.dp, bottomEnd = if (isWhatsAppIos) 18.dp else 22.dp),
                elevated = true,
                accentRimColor = if (isWhatsAppIos) iosBlue else glass.accentTeal,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { sealAndExit() },
                            modifier = Modifier.testTag("runtime_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Tutup ruang aplikasi",
                                tint = if (isWhatsAppIos) iosBlue else scheme.onSurface
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (activeClone != null) "${activeClone.originalAppName} by Clonix" else "Clonix Runtime",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                StatusPillBadge(
                                    text = if (isWhatsAppIos) "iOS • ${region.code}" else region.code,
                                    color = if (isWhatsAppIos) iosBlue else glass.accentTeal
                                )
                            }
                            if (activeProfile != null) {
                                val routeLabel = if (proxyConfig.isConfigured) {
                                    "Proxy ${region.code}"
                                } else {
                                    region.timezoneId
                                }
                                Text(
                                    text = "${activeProfile.model} • $routeLabel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isWhatsAppIos) iosGreen else glass.verifiedEmerald,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (webViewRef?.canGoBack() == true) webViewRef?.goBack()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Halaman sebelumnya",
                                tint = if (isWhatsAppIos) iosBlue else scheme.onSurface
                            )
                        }
                        IconButton(onClick = { webViewRef?.reload() }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Muat ulang",
                                tint = if (isWhatsAppIos) iosBlue else scheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { showHudDetails = !showHudDetails },
                            modifier = Modifier.testTag("runtime_hud_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "Info perangkat virtual",
                                tint = if (showHudDetails) {
                                    if (isWhatsAppIos) iosBlue else glass.accentTeal
                                } else {
                                    scheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    AnimatedVisibility(visible = showHudDetails && activeProfile != null && activeClone != null) {
                        val vp = activeProfile!!
                        val ac = activeClone!!
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = scheme.background.copy(alpha = 0.80f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Perangkat Virtual Aktif (${vp.devicePlatform.label})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isWhatsAppIos) iosBlue else glass.accentTeal
                                )
                                Text(
                                    text = "${vp.manufacturer} ${vp.model} • ID: ${vp.virtualAndroidId}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                if (vp.virtualImei.isNotBlank()) {
                                    Text(
                                        text = "IMEI: ${vp.virtualImei} • SIM: ${vp.carrierName} (${vp.mccMnc})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "Wilayah: ${region.city} (${region.localeTag}) • Rute: ${if (proxyConfig.isConfigured) proxyConfig.summary() else "Direct Regional"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = glass.verifiedEmerald
                                )

                                if (exitReport != null && !exitReport!!.isLoading) {
                                    val rep = exitReport!!
                                    Text(
                                        text = if (rep.errorMessage != null) {
                                            "Cek IP: ${rep.errorMessage}"
                                        } else {
                                            "IP Keluar: ${rep.ipAddress} (${rep.countryCode} • ${rep.city})"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (rep.matchesTargetRegion) glass.verifiedEmerald else glass.warningAmber
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            desktopMode = !desktopMode
                                            webViewRef?.let { wv ->
                                                wv.settings.userAgentString = if (desktopMode) vp.desktopUserAgent else vp.userAgent
                                                wv.reload()
                                            }
                                        },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                desktopMode -> Icons.Filled.DesktopWindows
                                                isIos -> Icons.Filled.PhoneIphone
                                                else -> Icons.Filled.PhoneAndroid
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (desktopMode) "Mode Desktop" else if (isIos) "Mode iPhone" else "Mode Ponsel",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                exitReport = NetworkExitReport(isLoading = true)
                                                exitReport = repository.probeExitIp(proxyConfig, ac.regionCode)
                                            }
                                        },
                                        shape = CircleShape,
                                        border = BorderStroke(1.dp, (if (isWhatsAppIos) iosBlue else glass.accentTeal).copy(alpha = 0.55f)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.NetworkCheck,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (exitReport?.isLoading == true) "Menguji..." else "Cek IP & Negara",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (pageProgress in 1..99) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { pageProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(CircleShape),
                            color = if (isWhatsAppIos) iosBlue else glass.accentTeal
                        )
                    }
                }
            }

            if (loadError != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = loadError!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.error
                    )
                }
            } else if (!containerReady || activeClone == null || activeProfile == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = if (isWhatsAppIos) iosBlue else glass.accentTeal)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Membuka ruang terpisah ${region.code}...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val antiDetectShim = remember(activeProfile, desktopMode, isWhatsAppIos) {
                    repository.cryptoManager.buildAntiDetectJsShim(
                        profile = activeProfile,
                        region = region,
                        desktopMode = desktopMode,
                        isWhatsAppIosMode = isWhatsAppIos
                    )
                }
                val headers = remember(region) {
                    mapOf(
                        "Accept-Language" to region.acceptLanguage,
                        "X-Time-Zone" to region.timezoneId
                    )
                }

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewRef = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.userAgentString = if (desktopMode) activeProfile.desktopUserAgent else activeProfile.userAgent
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    pageProgress = newProgress
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onReceivedHttpAuthRequest(
                                    view: WebView?,
                                    handler: HttpAuthHandler?,
                                    host: String?,
                                    realm: String?
                                ) {
                                    if (proxyConfig.isConfigured && proxyConfig.username.isNotBlank()) {
                                        handler?.proceed(proxyConfig.username, proxyConfig.password)
                                    } else {
                                        super.onReceivedHttpAuthRequest(view, handler, host, realm)
                                    }
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    if (url.startsWith("http://") || url.startsWith("https://")) {
                                        currentUrl = url
                                        onUrlChanged(activeClone.targetWebUrl, url)
                                        view?.loadUrl(url, headers)
                                        return true
                                    }
                                    return true
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    if (!url.isNullOrBlank()) {
                                        currentUrl = url
                                        onUrlChanged(activeClone.targetWebUrl, url)
                                    }
                                    val latestShim = repository.cryptoManager.buildAntiDetectJsShim(
                                        profile = activeProfile,
                                        region = region,
                                        desktopMode = desktopMode,
                                        isWhatsAppIosMode = isWhatsAppIos
                                    )
                                    view?.evaluateJavascript(latestShim, null)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    val latestShim = repository.cryptoManager.buildAntiDetectJsShim(
                                        profile = activeProfile,
                                        region = region,
                                        desktopMode = desktopMode,
                                        isWhatsAppIosMode = isWhatsAppIos
                                    )
                                    view?.evaluateJavascript(latestShim, null)
                                }
                            }

                            loadUrl(activeClone.targetWebUrl, headers)
                        }
                    },
                    update = { wv ->
                        val desiredUa = if (desktopMode) activeProfile.desktopUserAgent else activeProfile.userAgent
                        if (wv.settings.userAgentString != desiredUa) {
                            wv.settings.userAgentString = desiredUa
                            wv.evaluateJavascript(antiDetectShim, null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scheme.background)
                        .testTag("runtime_webview")
                )
            }
        }
    }
}
