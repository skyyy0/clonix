package com.example.security

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle

/** Outcome of trying to open a clone that lives in the work profile. */
sealed interface NativeLaunchResult {
    /** The launch request was handed to Android successfully. */
    data object Launched : NativeLaunchResult

    /** No work profile exists yet; the user has to approve provisioning. */
    data object NeedsProfile : NativeLaunchResult

    /** The app is not installed in the work profile yet, or Android blocked the launch. */
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
 *
 * Launching another package inside the work profile is also restricted by Android:
 * `CrossProfileApps`/`LauncherApps` cross-profile access needs a permission a profile
 * owner cannot request for itself. So Clonix asks Android for the launch, and when the
 * platform refuses it says so and points at the launcher's work tab instead of faking
 * success.
 */
class WorkProfileManager(private val context: Context) {

    val adminComponent: ComponentName = ComponentName(context, ClonixDeviceAdminReceiver::class.java)

    private val dpm: DevicePolicyManager? =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    private val launcherApps: LauncherApps? =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)

    /**
     * The work profile user, if the device already has one that Clonix can see.
     * `LauncherApps.profiles` is the public way for an app to enumerate the users in
     * its own profile group, which is exactly the set containing the work profile.
     */
    val profileUser: UserHandle?
        get() = runCatching {
            val me = Process.myUserHandle()
            launcherApps?.profiles?.firstOrNull { it != me }
        }.getOrNull()

    val hasProfile: Boolean get() = profileUser != null

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
        val user = profileUser ?: return false
        val apps = launcherApps ?: return false
        return runCatching { apps.getActivityList(packageName, user).isNotEmpty() }.getOrDefault(false)
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
     * Installs the clone if needed, then asks Android to open it in the work profile.
     * When the platform refuses, the caller gets a message it can show to the user.
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

        val apps = launcherApps
        val mainActivity = context.packageManager.getLaunchIntentForPackage(packageName)?.component
        if (apps != null && mainActivity != null) {
            val launched = runCatching {
                apps.startMainActivity(mainActivity, user, null, null)
                true
            }.getOrDefault(false)
            if (launched) return NativeLaunchResult.Launched
        }

        return NativeLaunchResult.NeedsInstall(
            "Android membatasi peluncuran aplikasi lain dari dalam profil kerja untuk aplikasi biasa. " +
                "Buka $appName dari tab Kerja di launcher HP — klonnya tetap tersimpan di halaman klon Clonix."
        )
    }

    /** Identity note shown in the UI for native clones. */
    fun identityNote(): String = if (hasProfile) {
        "Profil kerja aktif: Android ID, akun, dan penyimpanan klon berbeda dari aplikasi asli. " +
            "IMEI, serial, dan Build tetap milik perangkat karena dibatasi Android."
    } else {
        "Profil kerja belum dibuat. Android ID, akun, dan penyimpanan akan terpisah setelah profil dibuat; " +
            "IMEI, serial, dan Build tetap milik perangkat."
    }
}
