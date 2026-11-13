package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.cloud.ClonixCloudRepository
import com.example.data.local.ClonedAppEntity
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ClonixCloudRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun sampleClone(id: String): ClonedAppEntity = ClonedAppEntity(
        id = id,
        packageName = "com.whatsapp",
        originalAppName = "WhatsApp",
        cloneLabel = "WhatsApp • US iPhone",
        instanceNumber = 1,
        devicePlatform = "IOS",
        regionCode = "US",
        regionName = "United States",
        city = "New York, NY",
        timezoneId = "America/New_York",
        localeTag = "en-US",
        carrierName = "T-Mobile US",
        mccMnc = "310-260",
        virtualModel = "iPhone 16 Pro Max",
        virtualManufacturer = "Apple",
        virtualAndroidId = "A1B2C3D4-1122-3344-5566-AABBCCDDEEFF",
        virtualSerial = "F2LX998877",
        virtualFingerprint = "Apple/iPhone17,2/D94AP:iOS/18.3.1/22D72:user/release",
        virtualFingerprintHash = "A1B2C3D4E5F60718",
        virtualMacAddress = "02:AA:BB:CC:DD:EE",
        virtualAdvertisingId = "11111111-2222-3333-4444-555555555555",
        encryptedVaultPath = "/tmp/vault.enc",
        sandboxSizeBytes = 2048L,
        launchMode = "ISOLATED_CONTAINER",
        targetWebUrl = "https://web.whatsapp.com",
        proxyEnabled = true,
        proxyType = "HTTP",
        proxyHost = "45.12.33.10",
        proxyPort = 8080,
        preferDesktopWeb = true,
        isEncrypted = true,
        isAntiDetectActive = true,
        isLocked = false,
        createdAt = System.currentTimeMillis(),
        lastLaunchedAt = null
    )

    @Test
    fun syncAndObserveClone_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = ClonixCloudRepository(firestore)
        val cloneId = "clone_${UUID.randomUUID().toString().replace("-", "_")}"

        val syncResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.syncCloneToCloud(sampleClone(cloneId))
        }
        assertTrue(syncResult.isSuccess)

        val clones = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeCloudClones(uid).first { list -> list.any { it.id == cloneId } }
        }
        val synced = clones.first { it.id == cloneId }
        assertEquals("IOS", synced.devicePlatform)
        assertEquals("US", synced.regionCode)
        assertEquals("iPhone 16 Pro Max", synced.virtualModel)
    }

    @Test
    fun observeCloudClones_crossUserAccess_denied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ClonixCloudRepository(firestore)
        val cloneId = "clone_${UUID.randomUUID().toString().replace("-", "_")}"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.syncCloneToCloud(sampleClone(cloneId)).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = ClonixCloudRepository(firestore)
        var capturedException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeCloudClones(aliceUid).first()
            }
        } catch (t: Throwable) {
            capturedException = t
        }
        val firestoreEx = checkNotNull(capturedException?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException for cross-user read, got $capturedException"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    @Test
    fun observeCloudClones_unauthenticated_denied() = runBlocking {
        auth.signOut()
        val repo = ClonixCloudRepository(firestore)
        var capturedException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeCloudClones("alice_uid_placeholder").first()
            }
        } catch (t: Throwable) {
            capturedException = t
        }
        val firestoreEx = checkNotNull(capturedException?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException for unauthenticated read, got $capturedException"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_clonix@test.com"
        const val BOB_EMAIL = "bob_clonix@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3500L
    }
}
