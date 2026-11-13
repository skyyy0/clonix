package com.example.security

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.CrossProfileApps
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import com.example.runtime.CloneLauncherActivity

/** Outcome of trying to open a clone that lives in the work profile. */
sealed interface NativeLaunchResult {
    /** The app was opened in the work profile. */
    data object Launched : NativeLaunchResult

    /** No work profile exists yet; the user has to approve provisioning. */
    data object NeedsProfile : NativeLaunchResult

    /** The app is not installed in the work profile yet (or Android blocked the launch). */
    data class NeedsInstall(val message: String) : NativeLaunchResult

    /** The device or platform cannot host a work profile at all. */
    data class Failed(val message: String) : NativeLaunchResult
}

/**
 * Thin, honest wrapper around the work profile APIs.
 *
 * What a work profile really gives a clone:
 *  - a second installation of the app (own process, storage, accounts, notifications);
 *  - its own ANDROID_ID and advertising ID, because Android scopes those per user.
 *
 * What it cannot do: change IMEI, serial or `Build.*`, because those are hardware/kernel
 * properties and no normal app may rewrite them for another app's process.
 */
class WorkProfileManager(private val context: Context) {

    val adminComponent: ComponentName = ComponentName(context, ClonixDeviceAdminReceiver::class.java)

    private val dpm: DevicePolicyManager? =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    private val userManager: UserManager? =
        context.getSystemService(Context.USER_SERVICE) as? UserManager
    private val launcherApps: LauncherApps? =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)

    val profileUser: UserHandle?
        get() = runCatching {
            userManager?.userProfiles?.firstOrNull { it.isManagedProfile }?.userHandle
        }.getOrNull()

    val hasProfile: Boolean get() = profileUser != null

    val isProfileOwner: Boolean
        get() = runCatching { dpm?.isProfileOwnerApp(context.packageName) == true }.getOrDefault(false)

    /** Intent for the system consent screen that creates the work profile. */
    fun provisioningIntent(): Intent? {
        if (!isSupported) return null
        return Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                adminComponent
            )
        }
    }

    fun isInstalledInProfile(packageName: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val user = profileUser ?: return false
        val apps = launcherApps ?: return false
        return runCatching { apps.getApplicationInfo(packageName, 0, user) != null }.getOrDefault(false)
    }

    /**
     * Installs a package that already exists in the personal profile into the work
     * profile. Requires Android 9+ and a profile owner; returns false instead of
     * pretending it worked.
     */
    fun installIntoProfile(packageName: String): Boolean {
        val manager = dpm ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return runCatching { manager.installExistingPackage(adminComponent, packageName) }
            .getOrDefault(false)
    }

    /**
     * Installs the clone if needed, then tries every bridge Android offers to open it in
     * the work profile. Each attempt is a real platform call; when all of them are
     * blocked we return an actionable message instead of a fake success.
     */
    fun ensureAndLaunch(packageName: String, appName: String): NativeLaunchResult {
        if (!isSupported) {
            return NativeLaunchResult.Failed(
                "Perangkat ini tidak mendukung profil kerja Android, jadi klon native tidak bisa dibuat."
            )
        }
        val user = profileUser ?: return NativeLaunchResult.NeedsProfile

        if (!isInstalledInProfile(packageName) && !installIntoProfile(packageName)) {
            return NativeLaunchResult.NeedsInstall(
                "$appName belum ada di profil kerja. Pasang $appName dari Play Store di sisi Kerja " +
                    "(tab Kerja pada launcher), lalu buka lagi dari Clonix."
            )
        }

        // Bridge 1: LauncherApps. Works when Clonix has cross-profile launcher access.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val info = launcherApps?.getApplicationInfo(packageName, 0, user)
                val component = info?.componentName
                if (component != null) {
                    launcherApps?.startMainActivity(component, user, null, null)
                    return NativeLaunchResult.Launched
                }
            }
        }

        // Bridge 2: start Clonix's own bridge activity inside the profile, which then opens
        // the clone locally (Android 11+, no cross-user permission for other packages).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val crossProfileApps = context.getSystemService(CrossProfileApps::class.java)
                val bridge = Intent(context, CloneLauncherActivity::class.java).apply {
                    putExtra(CloneLauncherActivity.EXTRA_TARGET_PACKAGE, packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                crossProfileApps?.startActivity(bridge, user)
                return NativeLaunchResult.Launched
            }
        }

        return NativeLaunchResult.NeedsInstall(
            "Android memblokir peluncuran lintas profil untuk $appName. Buka $appName dari tab " +
                "Kerja di launcher HP; klonnya tetap tersimpan di Clonix."
        )
    }

    /** Package-scoped identity note shown in the UI for native clones. */
    fun identityNote(): String = if (hasProfile && isProfileOwner) {
        "Profil kerja aktif: Android ID, akun, dan penyimpanan klon berbeda dari aplikasi asli. " +
            "IMEI, serial, dan Build tetap milik perangkat karena dibatasi Android."
    } else {
        "Profil kerja belum dibuat. Android ID, akun, dan penyimpanan akan terpisah setelah profil dibuat; " +
            "IMEI, serial, dan Build tetap milik perangkat."
    }
}
