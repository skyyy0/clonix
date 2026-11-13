package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cloned_apps")
data class ClonedAppEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val originalAppName: String,
    val cloneLabel: String,
    val instanceNumber: Int,
    val devicePlatform: String = "ANDROID",
    val regionCode: String,
    val regionName: String,
    val city: String,
    val timezoneId: String,
    val localeTag: String,
    val carrierName: String,
    val mccMnc: String,
    val virtualModel: String,
    val virtualManufacturer: String,
    val virtualAndroidId: String,
    val virtualSerial: String,
    val virtualFingerprint: String,
    val virtualFingerprintHash: String,
    val virtualMacAddress: String,
    val virtualAdvertisingId: String,
    val virtualImei: String = "",
    val virtualImsi: String = "",
    val virtualGsfId: String = "",
    val virtualWidevineId: String = "",
    val apkDigestSha256: String = "",
    val sandboxContainerPath: String = "",
    val encryptedVaultPath: String,
    val sandboxSizeBytes: Long,
    val launchMode: String,
    val targetWebUrl: String,
    val proxyEnabled: Boolean = false,
    val proxyType: String = "DIRECT",
    val proxyHost: String = "",
    val proxyPort: Int = 0,
    val preferDesktopWeb: Boolean = false,
    val isEncrypted: Boolean,
    val isAntiDetectActive: Boolean,
    val isLocked: Boolean,
    val createdAt: Long,
    val lastLaunchedAt: Long?
)
