package com.example.data.cloud

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserVaultProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val defaultRegionCode: String = "US",
    val strictAntiDetect: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudCloneRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val packageName: String = "",
    val originalAppName: String = "",
    val cloneLabel: String = "",
    val instanceNumber: Long = 1L,
    val devicePlatform: String = "ANDROID",
    val regionCode: String = "US",
    val regionName: String = "United States",
    val city: String = "New York, NY",
    val timezoneId: String = "America/New_York",
    val localeTag: String = "en-US",
    val carrierName: String = "T-Mobile US",
    val mccMnc: String = "310-260",
    val virtualModel: String = "",
    val virtualManufacturer: String = "",
    val virtualAndroidId: String = "",
    val virtualFingerprintHash: String = "",
    val launchMode: String = "ISOLATED_CONTAINER",
    val targetWebUrl: String = "",
    val proxyEnabled: Boolean = false,
    val proxySummary: String = "Direct",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudAuditLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val cloneId: String = "",
    val eventType: String = "",
    val title: String = "",
    val detail: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
