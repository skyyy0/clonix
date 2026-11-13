package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import android.provider.Settings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.example.data.local.ClonedAppEntity
import com.example.data.model.DevicePlatform
import com.example.data.model.LaunchMode
import com.example.data.model.ProxyConfig
import com.example.data.model.ProxyType
import com.example.data.model.VirtualDeviceProfile
import com.example.data.model.VirtualRegionCatalog
import com.example.ui.ClonixViewModel
import com.example.ui.components.LiquidGlassBackdrop
import com.example.ui.components.LiquidGlassSurface
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.LocalLiquidGlass
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun IsolatedSandboxScreen(
    clone: ClonedAppEntity,
    viewModel: ClonixViewModel,
    onExitSandbox: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val glass = LocalLiquidGlass.current
    val exitIpReport by viewModel.exitIpReport.collectAsStateWithLifecycle()

    var profile by remember(clone.id) { mutableStateOf<VirtualDeviceProfile?>(null) }
    var proxyConfig by remember(clone.id) { mutableStateOf(ProxyConfig()) }
    var desktopMode by remember(clone.id) { mutableStateOf(clone.preferDesktopWeb) }
    var containerReady by remember(clone.id) { mutableStateOf(false) }
    var proxyApplied by remember(clone.id) { mutableStateOf(false) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var currentUrl by remember(clone.id) { mutableStateOf(clone.targetWebUrl) }
    var showHudDetails by remember { mutableStateOf(false) }
    var nativeLaunchError by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val region = remember(clone.regionCode) { VirtualRegionCatalog.findByCode(clone.regionCode) }
    val launchMode = remember(clone.launchMode) {
        runCatching { LaunchMode.valueOf(clone.launchMode) }.getOrDefault(LaunchMode.ISOLATED_CONTAINER)
    }

    LaunchedEffect(clone.id) {
        viewModel.clearExitIpReport()
        val loadedProfile = viewModel.loadSandboxProfile(clone)
        val loadedProxy = viewModel.loadEncryptedProxy(clone.id)
        profile = loadedProfile
        proxyConfig = loadedProxy

        if (launchMode == LaunchMode.ISOLATED_CONTAINER) {
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

            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.removeAllCookies(null)
            cm.flush()

            val savedCookies = viewModel.clonixRepository.loadEncryptedSessionCookies(clone.id)
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
        }
        containerReady = true
    }

    fun clearWebViewProxyOverride() {
        if (proxyApplied && WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            runCatching {
                ProxyController.getInstance().clearProxyOverride(Executor { it.run() }, Runnable {})
            }
            proxyApplied = false
        }
    }

    fun sealAndExit() {
        scope.launch {
            if (launchMode == LaunchMode.ISOLATED_CONTAINER) {
                val cm = CookieManager.getInstance()
                val baseCookie = cm.getCookie(clone.targetWebUrl).orEmpty()
                val activeCookie = cm.getCookie(currentUrl).orEmpty()
                val snapshot = buildString {
                    if (baseCookie.isNotBlank()) appendLine("${clone.targetWebUrl}|$baseCookie")
                    if (activeCookie.isNotBlank() && currentUrl != clone.targetWebUrl) {
                        appendLine("$currentUrl|$activeCookie")
                    }
                }.trim()
                viewModel.clonixRepository.saveEncryptedSessionCookies(clone.id, snapshot)
                cm.removeAllCookies(null)
                cm.flush()
                WebStorage.getInstance().deleteAllData()
                webViewRef?.clearCache(true)
                webViewRef?.destroy()
                clearWebViewProxyOverride()
            }
            viewModel.refreshVaultSize()
            onExitSandbox()
        }
    }

    BackHandler {
        val wv = webViewRef
        if (launchMode == LaunchMode.ISOLATED_CONTAINER && wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            sealAndExit()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.destroy()
            clearWebViewProxyOverride()
        }
    }

    LiquidGlassBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            LiquidGlassSurface(
                shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
                elevated = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { sealAndExit() },
                            modifier = Modifier.testTag("sandbox_exit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Tutup dan kunci brankas",
                                tint = scheme.onSurface
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = clone.cloneLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                StatusPillBadge(
                                    text = region.code,
                                    color = scheme.primary
                                )
                            }
                            val routeLabel = if (proxyConfig.isConfigured) proxyConfig.summary() else "Direct/VPN"
                            Text(
                                text = "${clone.virtualModel} • ${region.timezoneId} • $routeLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = glass.verifiedEmerald,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (launchMode == LaunchMode.ISOLATED_CONTAINER) {
                            IconButton(
                                onClick = {
                                    if (webViewRef?.canGoBack() == true) webViewRef?.goBack()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Kembali di kontainer"
                                )
                            }
                            IconButton(onClick = { webViewRef?.reload() }) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Muat ulang kontainer"
                                )
                            }
                        }

                        IconButton(
                            onClick = { showHudDetails = !showHudDetails },
                            modifier = Modifier.testTag("sandbox_hud_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "Detail isolasi perangkat",
                                tint = if (showHudDetails) scheme.primary else scheme.onSurfaceVariant
                            )
                        }
                    }

                    AnimatedVisibility(visible = showHudDetails && profile != null) {
                        profile?.let { vp ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = scheme.background.copy(alpha = 0.78f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "TELEMETRI PERANGKAT BARU (${vp.devicePlatform.badge})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = glass.accentTeal
                                    )
                                    Text(
                                        text = "ID: ${vp.virtualAndroidId} | ${vp.manufacturer} ${vp.model}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    if (vp.virtualImei.isNotBlank()) {
                                        Text(
                                            text = "IMEI: ${vp.virtualImei} | GSF: ${vp.virtualGsfId.take(10)}.. | DRM: ${vp.virtualWidevineId.take(10)}..",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = scheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "WebGL: ${vp.webglVendor} / ${vp.webglRenderer} | WebRTC Guard: Aktif",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = glass.verifiedEmerald
                                    )
                                    Text(
                                        text = "Geo: ${region.city} (${vp.localeTag}) | Route: ${if (proxyConfig.isConfigured) proxyConfig.summary() else "Direct / VPN Sistem"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                    if (exitIpReport != null && !exitIpReport!!.isLoading) {
                                        val report = exitIpReport!!
                                        Text(
                                            text = if (report.errorMessage != null) {
                                                "IP Probe Error: ${report.errorMessage}"
                                            } else {
                                                "Exit IP Nyata: ${report.ipAddress} (${report.countryCode} • ${report.city}) • ${report.latencyMs}ms"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (report.matchesTargetRegion) glass.verifiedEmerald else glass.warningAmber
                                        )
                                    }

                                    if (launchMode == LaunchMode.ISOLATED_CONTAINER) {
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
                                                    .testTag("sandbox_desktop_toggle")
                                            ) {
                                                Icon(
                                                    imageVector = if (desktopMode) Icons.Filled.DesktopWindows else Icons.Filled.PhoneIphone,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (desktopMode) "Desktop UA" else "Mobile UA",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }

                                            OutlinedButton(
                                                onClick = { viewModel.probeNetworkExitIp(proxyConfig, clone.regionCode) },
                                                shape = CircleShape,
                                                border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.5f)),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(36.dp)
                                                    .testTag("sandbox_probe_ip_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.NetworkCheck,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (exitIpReport?.isLoading == true) "Menguji..." else "Cek IP Nyata",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (launchMode == LaunchMode.ISOLATED_CONTAINER && pageProgress in 1..99) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { pageProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(CircleShape),
                            color = scheme.primary
                        )
                    }
                }
            }

            if (!containerReady || profile == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = scheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Mendekripsi brankas & rute ${region.code}...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant
                        )
                    }
                }
            } else if (launchMode == LaunchMode.ISOLATED_CONTAINER) {
                val activeProfile = profile!!
                val antiDetectShim = remember(activeProfile, desktopMode) {
                    viewModel.buildAntiDetectJs(activeProfile, desktopMode)
                }
                val headers = remember(region) {
                    mapOf("Accept-Language" to region.acceptLanguage)
                }

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewRef = this
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
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
                                        view?.loadUrl(url, headers)
                                        return true
                                    }
                                    return true
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    if (!url.isNullOrBlank()) currentUrl = url
                                    val latestShim = viewModel.buildAntiDetectJs(activeProfile, desktopMode)
                                    view?.evaluateJavascript(latestShim, null)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    val latestShim = viewModel.buildAntiDetectJs(activeProfile, desktopMode)
                                    view?.evaluateJavascript(latestShim, null)
                                }
                            }

                            loadUrl(clone.targetWebUrl, headers)
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
                        .testTag("isolated_webview")
                )
            } else {
                val activeProfile = profile!!
                val isIos = activeProfile.devicePlatform == DevicePlatform.IOS
                val capabilities = viewModel.hostIsolationCapabilities

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LiquidGlassSurface(
                        shape = RoundedCornerShape(26.dp),
                        elevated = true,
                        accentRimColor = glass.accentTeal,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            StatusPillBadge(
                                text = "Perangkat Baru Aktif",
                                color = glass.verifiedEmerald,
                                icon = Icons.Filled.VerifiedUser
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = clone.originalAppName,
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = "${activeProfile.devicePlatform.label}: ${activeProfile.manufacturer} ${activeProfile.model} • ${region.code}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = scheme.background.copy(alpha = 0.72f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = "Identitas Perangkat Virtual",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = glass.accentTeal
                                    )
                                    Text(
                                        text = "${if (isIos) "Device ID iOS" else "Android ID"}: ${activeProfile.virtualAndroidId}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        text = "IMEI: ${activeProfile.virtualImei.ifBlank { "-" }} • Seri: ${activeProfile.serialNumber}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Operator: ${activeProfile.carrierName} (${activeProfile.mccMnc})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "MAC: ${activeProfile.macAddress} • Wi-Fi: ${activeProfile.virtualWifiSsid}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Rute Jaringan: ${if (proxyConfig.isConfigured) proxyConfig.summary() else "Direct Regional"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (proxyConfig.isConfigured) glass.verifiedEmerald else scheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Aplikasi akan dibuka di jendela terpisah \"${clone.originalAppName} by Clonix\" pada Recent Apps menggunakan engine Clonix dengan identitas perangkat baru.",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant
                            )

                            if (nativeLaunchError != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = nativeLaunchError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.error
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    nativeLaunchError = null
                                    com.example.runtime.CloneRuntimeActivity.launchCloneTask(context, clone.id)
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = scheme.primary,
                                    contentColor = scheme.onPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("launch_native_apk_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Buka ${clone.originalAppName} by Clonix")
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.switchLaunchMode(clone.id, LaunchMode.ISOLATED_CONTAINER)
                                },
                                shape = CircleShape,
                                border = BorderStroke(1.dp, glass.accentTeal.copy(alpha = 0.55f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("switch_to_isolated_container_button")
                            ) {
                                Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Buka di Tampilan Web Dalam App")
                            }

                            if (capabilities.supportsManagedProfileProvisioning) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        val multiUserIntent = Intent("android.settings.USER_SETTINGS").apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        runCatching { context.startActivity(multiUserIntent) }
                                            .onFailure { runCatching { context.startActivity(fallbackIntent) } }
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                ) {
                                    Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Pengaturan Multi-User / Profil Kerja")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { sealAndExit() },
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Tutup & Kunci Brankas")
                            }
                        }
                    }
                }
            }
        }
    }
}
