package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import com.example.data.model.AppCategory
import com.example.data.model.InstalledAppInfo
import com.example.data.model.LaunchMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class InstalledAppsRepository(private val context: Context) {

    private val iconCache = ConcurrentHashMap<String, Bitmap>()

    data class KnownPortalSpec(
        val url: String,
        val category: AppCategory,
        val displayLabel: String
    )

    private val knownSocialPortals: Map<String, KnownPortalSpec> = mapOf(
        "com.whatsapp" to KnownPortalSpec("https://web.whatsapp.com", AppCategory.MESSAGING, "WhatsApp"),
        "com.whatsapp.w4b" to KnownPortalSpec("https://web.whatsapp.com", AppCategory.MESSAGING, "WhatsApp Business"),
        "com.instagram.android" to KnownPortalSpec("https://www.instagram.com", AppCategory.SOCIAL, "Instagram"),
        "com.instagram.barcelona" to KnownPortalSpec("https://www.threads.net", AppCategory.SOCIAL, "Threads"),
        "com.zhiliaoapp.musically" to KnownPortalSpec("https://www.tiktok.com", AppCategory.SOCIAL, "TikTok"),
        "com.ss.android.ugc.trill" to KnownPortalSpec("https://www.tiktok.com", AppCategory.SOCIAL, "TikTok"),
        "com.facebook.katana" to KnownPortalSpec("https://m.facebook.com", AppCategory.SOCIAL, "Facebook"),
        "com.facebook.lite" to KnownPortalSpec("https://m.facebook.com", AppCategory.SOCIAL, "Facebook Lite"),
        "com.facebook.orca" to KnownPortalSpec("https://www.messenger.com", AppCategory.MESSAGING, "Messenger"),
        "com.twitter.android" to KnownPortalSpec("https://x.com", AppCategory.SOCIAL, "X (Twitter)"),
        "org.telegram.messenger" to KnownPortalSpec("https://web.telegram.org", AppCategory.MESSAGING, "Telegram"),
        "org.telegram.plus" to KnownPortalSpec("https://web.telegram.org", AppCategory.MESSAGING, "Telegram Plus"),
        "com.discord" to KnownPortalSpec("https://discord.com/app", AppCategory.MESSAGING, "Discord"),
        "com.reddit.frontpage" to KnownPortalSpec("https://www.reddit.com", AppCategory.SOCIAL, "Reddit"),
        "com.linkedin.android" to KnownPortalSpec("https://www.linkedin.com", AppCategory.SOCIAL, "LinkedIn"),
        "com.snapchat.android" to KnownPortalSpec("https://web.snapchat.com", AppCategory.SOCIAL, "Snapchat"),
        "com.pinterest" to KnownPortalSpec("https://www.pinterest.com", AppCategory.SOCIAL, "Pinterest"),
        "com.google.android.youtube" to KnownPortalSpec("https://m.youtube.com", AppCategory.SOCIAL, "YouTube"),
        "com.google.android.apps.youtube.music" to KnownPortalSpec("https://music.youtube.com", AppCategory.SOCIAL, "YouTube Music"),
        "com.spotify.music" to KnownPortalSpec("https://open.spotify.com", AppCategory.SOCIAL, "Spotify"),
        "com.netflix.mediaclient" to KnownPortalSpec("https://www.netflix.com", AppCategory.SOCIAL, "Netflix"),
        "tv.twitch.android.app" to KnownPortalSpec("https://m.twitch.tv", AppCategory.SOCIAL, "Twitch"),
        "com.shopee.id" to KnownPortalSpec("https://shopee.co.id", AppCategory.OTHER, "Shopee"),
        "com.tokopedia.tkpd" to KnownPortalSpec("https://www.tokopedia.com", AppCategory.OTHER, "Tokopedia"),
        "com.github.android" to KnownPortalSpec("https://github.com", AppCategory.OTHER, "GitHub"),
        "com.google.android.gm" to KnownPortalSpec("https://mail.google.com", AppCategory.MESSAGING, "Gmail"),
        "jp.naver.line.android" to KnownPortalSpec("https://line.me", AppCategory.MESSAGING, "LINE")
    )

    val instantSocialPresets: List<InstalledAppInfo> = listOf(
        InstalledAppInfo("com.instagram.android", "Instagram", "Web Sandbox", 0L, AppCategory.SOCIAL, null, LaunchMode.ISOLATED_CONTAINER, "https://www.instagram.com", isVirtualPreset = true),
        InstalledAppInfo("com.whatsapp", "WhatsApp", "Web Sandbox", 0L, AppCategory.MESSAGING, null, LaunchMode.ISOLATED_CONTAINER, "https://web.whatsapp.com", isVirtualPreset = true),
        InstalledAppInfo("com.zhiliaoapp.musically", "TikTok", "Web Sandbox", 0L, AppCategory.SOCIAL, null, LaunchMode.ISOLATED_CONTAINER, "https://www.tiktok.com", isVirtualPreset = true),
        InstalledAppInfo("com.facebook.katana", "Facebook", "Web Sandbox", 0L, AppCategory.SOCIAL, null, LaunchMode.ISOLATED_CONTAINER, "https://m.facebook.com", isVirtualPreset = true),
        InstalledAppInfo("com.twitter.android", "X (Twitter)", "Web Sandbox", 0L, AppCategory.SOCIAL, null, LaunchMode.ISOLATED_CONTAINER, "https://x.com", isVirtualPreset = true),
        InstalledAppInfo("org.telegram.messenger", "Telegram", "Web Sandbox", 0L, AppCategory.MESSAGING, null, LaunchMode.ISOLATED_CONTAINER, "https://web.telegram.org", isVirtualPreset = true),
        InstalledAppInfo("com.discord", "Discord", "Web Sandbox", 0L, AppCategory.MESSAGING, null, LaunchMode.ISOLATED_CONTAINER, "https://discord.com/app", isVirtualPreset = true),
        InstalledAppInfo("com.reddit.frontpage", "Reddit", "Web Sandbox", 0L, AppCategory.SOCIAL, null, LaunchMode.ISOLATED_CONTAINER, "https://www.reddit.com", isVirtualPreset = true)
    )

    suspend fun loadInstalledDeviceApps(): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        val ownPackage = context.packageName
        val seenPackages = HashSet<String>()
        val results = mutableListOf<InstalledAppInfo>()

        for (ri in resolveInfos) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (pkg == ownPackage || !seenPackages.add(pkg)) continue

            val appInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkg, 0)
                }
            }.getOrNull() ?: continue

            val pkgInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
            }.getOrNull()

            val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty().ifBlank { pkg }
            val versionName = pkgInfo?.versionName ?: "1.0"
            val apkSize = appInfo.sourceDir?.let { path ->
                runCatching { File(path).length() }.getOrDefault(0L)
            } ?: 0L

            val iconBmp = iconCache[pkg] ?: runCatching {
                drawableToBitmap(ri.loadIcon(pm))
            }.getOrNull()?.also { iconCache[pkg] = it }

            val portalSpec = knownSocialPortals[pkg]
            val category = portalSpec?.category ?: inferCategory(pkg, label, appInfo)
            // Installed device apps are best cloned natively (real second instance).
            val launchMode = LaunchMode.WORK_PROFILE
            val portalUrl = portalSpec?.url ?: inferWebFallback(pkg)

            results += InstalledAppInfo(
                packageName = pkg,
                appName = label,
                versionName = versionName,
                apkSizeBytes = apkSize,
                category = category,
                iconBitmap = iconBmp,
                recommendedLaunchMode = launchMode,
                defaultWebPortalUrl = portalUrl,
                isVirtualPreset = false
            )
        }

        results.sortedWith(
            compareBy<InstalledAppInfo> {
                when (it.category) {
                    AppCategory.SOCIAL -> 0
                    AppCategory.MESSAGING -> 1
                    AppCategory.BROWSER -> 2
                    else -> 3
                }
            }.thenBy { it.appName.lowercase() }
        )
    }

    fun getCachedIcon(packageName: String): Bitmap? {
        iconCache[packageName]?.let { return it }
        val pm = context.packageManager
        return runCatching {
            val drawable = pm.getApplicationIcon(packageName)
            drawableToBitmap(drawable).also { iconCache[packageName] = it }
        }.getOrNull()
    }

    private fun inferCategory(pkg: String, label: String, info: ApplicationInfo): AppCategory {
        val lower = "$pkg $label".lowercase()
        return when {
            lower.contains("browser") || lower.contains("chrome") || lower.contains("firefox") ||
                lower.contains("opera") || lower.contains("brave") || lower.contains("webview") -> AppCategory.BROWSER
            lower.contains("chat") || lower.contains("messag") || lower.contains("sms") ||
                lower.contains("talk") || lower.contains("signal") || lower.contains("viber") -> AppCategory.MESSAGING
            lower.contains("social") || lower.contains("gram") || lower.contains("tok") ||
                lower.contains("face") || lower.contains("tube") || lower.contains("twitch") -> AppCategory.SOCIAL
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && info.category == ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
            else -> AppCategory.OTHER
        }
    }

    private fun inferWebFallback(pkg: String): String {
        val known = knownSocialPortals[pkg]
        if (known != null) return known.url
        return "https://duckduckgo.com/?q=${pkg.substringAfterLast('.')}"
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, 96, 96, true)
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
        val bitmap = Bitmap.createBitmap(width.coerceAtMost(128), height.coerceAtMost(128), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
