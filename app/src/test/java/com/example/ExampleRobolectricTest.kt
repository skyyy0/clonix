package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ClonedAppEntity
import com.example.data.local.ClonixDatabase
import com.example.data.model.VirtualRegionCatalog
import com.example.security.HostDeviceBaseline
import com.example.security.VaultCryptoManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `app name matches Clonix`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Clonix", appName)
  }

  @Test
  fun `virtual identity for US region differs from host baseline`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val crypto = VaultCryptoManager(context)
    val usRegion = VirtualRegionCatalog.findByCode("US")
    val host = HostDeviceBaseline(
      manufacturer = "Xiaomi",
      model = "Pixel 9 Pro",
      androidId = "1234567890abcdef",
      fingerprint = "xiaomi/host/device",
      sdkInt = 35
    )

    val virtualProfile = crypto.synthesizeVirtualIdentity(usRegion, host)

    assertNotEquals(host.androidId, virtualProfile.virtualAndroidId)
    assertNotEquals(host.model, virtualProfile.model)
    assertEquals("US", virtualProfile.regionCode)
    assertEquals("en-US", virtualProfile.localeTag)
    assertEquals("America/New_York", virtualProfile.timezoneId)
    assertEquals("310-260", virtualProfile.mccMnc)
    assertTrue(virtualProfile.userAgent.contains(virtualProfile.model))

    val jsShim = crypto.buildAntiDetectJsShim(virtualProfile, usRegion)
    assertTrue(jsShim.contains("America/New_York"))
    assertTrue(jsShim.contains("en-US"))
  }

  @Test
  fun `pin hashing verifies valid pin and rejects invalid pin`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val crypto = VaultCryptoManager(context)
    val salt = crypto.generateSalt()
    val hash = crypto.hashPin("4829", salt)

    assertEquals(hash, crypto.hashPin("4829", salt))
    assertNotEquals(hash, crypto.hashPin("0000", salt))
  }

  @Test
  fun `room database persists and removes cloned apps`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, ClonixDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    val dao = db.clonixDao()

    val entity = ClonedAppEntity(
      id = "clone-us-1",
      packageName = "com.instagram.android",
      originalAppName = "Instagram",
      cloneLabel = "Instagram • US #1",
      instanceNumber = 1,
      regionCode = "US",
      regionName = "United States",
      city = "New York, NY",
      timezoneId = "America/New_York",
      localeTag = "en-US",
      carrierName = "T-Mobile US",
      mccMnc = "310-260",
      virtualModel = "SM-S938U",
      virtualManufacturer = "Samsung",
      virtualAndroidId = "aabbccddeeff0011",
      virtualSerial = "CX9988776655",
      virtualFingerprint = "samsung/pa3q/pa3q:15/AP3A/1234:user/release-keys",
      virtualFingerprintHash = "A1B2C3D4E5F60718",
      virtualMacAddress = "02:11:22:33:44:55",
      virtualAdvertisingId = "00000000-0000-0000-0000-000000000001",
      encryptedVaultPath = "/tmp/clone-us-1_profile.enc",
      sandboxSizeBytes = 1024L,
      launchMode = "ISOLATED_CONTAINER",
      targetWebUrl = "https://www.instagram.com",
      isEncrypted = true,
      isAntiDetectActive = true,
      isLocked = false,
      createdAt = 1000L,
      lastLaunchedAt = null
    )

    dao.insertClone(entity)
    val list = dao.observeClonedApps().first()
    assertEquals(1, list.size)
    assertEquals("US", list.first().regionCode)

    dao.deleteCloneById("clone-us-1")
    val afterDelete = dao.observeClonedApps().first()
    assertTrue(afterDelete.isEmpty())
    db.close()
  }

  @Test
  fun `ios virtual identity uses Apple hardware and blocks WebRTC leaks`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val crypto = VaultCryptoManager(context)
    val usRegion = VirtualRegionCatalog.findByCode("US")
    val host = HostDeviceBaseline(
      manufacturer = "Samsung",
      model = "SM-S928B",
      androidId = "1234567890abcdef",
      fingerprint = "samsung/host/device",
      sdkInt = 35
    )

    val iosProfile = crypto.synthesizeVirtualIdentity(
      region = usRegion,
      hostBaseline = host,
      platform = com.example.data.model.DevicePlatform.IOS
    )

    assertEquals("Apple", iosProfile.manufacturer)
    assertTrue(iosProfile.model.startsWith("iPhone"))
    assertTrue(iosProfile.userAgent.contains("iPhone OS"))
    assertEquals("iPhone", iosProfile.navigatorPlatform)
    assertEquals("Apple GPU", iosProfile.webglRenderer)

    val shim = crypto.buildAntiDetectJsShim(iosProfile, usRegion, desktopMode = false)
    assertTrue(shim.contains("Apple GPU"))
    assertTrue(shim.contains("RTCPeerConnection"))

    val proxy = com.example.data.model.ProxyConfig(
      type = com.example.data.model.ProxyType.HTTP,
      host = "198.51.100.42",
      port = 8080,
      username = "clonix_user",
      password = "secret"
    )
    crypto.saveEncryptedProxy("test-ios-clone", proxy)
    val loadedProxy = crypto.loadEncryptedProxy("test-ios-clone")
    assertEquals(proxy.host, loadedProxy.host)
    assertEquals(proxy.port, loadedProxy.port)
    assertEquals(proxy.username, loadedProxy.username)
  }

  @Test
  fun `fresh hardware synthesis and native sandbox partition isolate all device identifiers`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val crypto = VaultCryptoManager(context)
    val jpRegion = VirtualRegionCatalog.findByCode("JP")
    val host = HostDeviceBaseline(
      manufacturer = "OnePlus",
      model = "CPH2581",
      androidId = "fedcba0987654321",
      fingerprint = "oneplus/host/device",
      sdkInt = 35
    )

    val profile = crypto.synthesizeVirtualIdentity(jpRegion, host)

    assertEquals(15, profile.virtualImei.length)
    assertTrue(profile.virtualImei.all { it.isDigit() })
    assertTrue("IMEI must pass Luhn checksum", isValidLuhn(profile.virtualImei))
    assertTrue(profile.virtualImsi.startsWith(jpRegion.mcc))
    assertEquals(16, profile.virtualGsfId.length)
    assertEquals(32, profile.virtualWidevineId.length)

    val partition = crypto.provisionNativeSandboxPartition(
      cloneId = "test-native-jp-1",
      packageName = "com.whatsapp",
      sourceApkPath = null,
      profile = profile,
      region = jpRegion
    )

    val sandboxDir = java.io.File(partition.containerPath)
    assertTrue(sandboxDir.exists())
    assertTrue(java.io.File(sandboxDir, "env/container_manifest.json").exists())
    assertTrue(java.io.File(sandboxDir, "data/shared_prefs").exists())
    assertEquals(64, partition.apkDigestSha256.length)

    val decryptedProp = crypto.decryptFromVault("test-native-jp-1", "buildprop")?.toString(Charsets.UTF_8).orEmpty()
    assertTrue(decryptedProp.contains("ro.product.model=${profile.model}"))
    assertTrue(decryptedProp.contains("clonix.virtual.imei=${profile.virtualImei}"))
    assertTrue(decryptedProp.contains("persist.sys.timezone=Asia/Tokyo"))

    crypto.secureWipeClone("test-native-jp-1")
    assertTrue(!sandboxDir.exists())
  }

  @Test
  fun `identity shim patches the signals a page actually reads and strict mode adds noise`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val crypto = VaultCryptoManager(context)
    val jpRegion = VirtualRegionCatalog.findByCode("JP")
    val host = HostDeviceBaseline(
      manufacturer = "samsung",
      model = "SM-S938B",
      androidId = "host1234567890ab",
      fingerprint = "samsung/host",
      sdkInt = 35
    )

    val profile = crypto.synthesizeVirtualIdentity(jpRegion, host)
    val strictShim = crypto.buildAntiDetectJsShim(profile, jpRegion, strictAntiDetect = true)

    assertTrue(strictShim.contains("Asia/Tokyo"))
    assertTrue(strictShim.contains("ja-JP"))
    assertTrue(strictShim.contains("getTimezoneOffset"))
    assertTrue(strictShim.contains("hardwareConcurrency"))
    assertTrue(strictShim.contains("RTCPeerConnection"))
    assertTrue(strictShim.contains("getImageData"))
    assertTrue(strictShim.contains(profile.webglRenderer))
    assertTrue(strictShim.contains("geolocation"))

    val looseShim = crypto.buildAntiDetectJsShim(profile, jpRegion, strictAntiDetect = false)
    assertTrue(!looseShim.contains("getImageData"))

    val probe = com.example.security.IdentityShield.probeScript()
    assertTrue(probe.contains("timezone"))
    assertTrue(probe.contains("webgl"))
  }

  @Test
  fun `native profile marker records per-profile identity without faking hardware`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val crypto = VaultCryptoManager(context)
    val host = HostDeviceBaseline(
      manufacturer = "Samsung",
      model = "SM-S938B",
      androidId = "hostAndroidId1234",
      fingerprint = "samsung/pa3q/pa3q:15/AP3A/1234:user/release-keys",
      sdkInt = 35
    )

    val marker = crypto.serializeNativeProfileMarker("com.whatsapp", host)

    assertTrue(marker.contains("WORK_PROFILE"))
    assertTrue(marker.contains("com.whatsapp"))
    assertTrue(marker.contains("per work profile user"))
    assertTrue(marker.contains(host.model))
    assertTrue("native marker must not claim a spoofed IMEI", !marker.contains("imei"))
  }

  private fun isValidLuhn(digits: String): Boolean {
    var sum = 0
    for (i in digits.indices) {
      var d = digits[i] - '0'
      if (i % 2 == 1) {
        d *= 2
        if (d > 9) d -= 9
      }
      sum += d
    }
    return sum % 10 == 0
  }

  @Test
  fun `whatsapp ios mode injects Apple SF styling and runtime launches isolated document task`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val crypto = VaultCryptoManager(context)
    val usRegion = VirtualRegionCatalog.findByCode("US")
    val host = HostDeviceBaseline(
      manufacturer = "Xiaomi",
      model = "23117RK66C",
      androidId = "hostid1234567890",
      fingerprint = "xiaomi/host",
      sdkInt = 35
    )

    val iosProfile = crypto.synthesizeVirtualIdentity(
      region = usRegion,
      hostBaseline = host,
      platform = com.example.data.model.DevicePlatform.IOS
    )
    val waIosShim = crypto.buildAntiDetectJsShim(
      profile = iosProfile,
      region = usRegion,
      desktopMode = true,
      isWhatsAppIosMode = true
    )

    assertTrue(waIosShim.contains("clonix-wa-ios-theme"))
    assertTrue(waIosShim.contains("SF Pro Text"))
    assertTrue(waIosShim.contains("Apple GPU"))

    val appContext = org.robolectric.Shadows.shadowOf(context as android.app.Application)
    com.example.runtime.CloneRuntimeActivity.launchCloneTask(context, "yt-clone-99")
    val launchedIntent = appContext.nextStartedActivity
    assertEquals(com.example.runtime.CloneRuntimeActivity::class.java.name, launchedIntent.component?.className)
    assertTrue((launchedIntent.flags and android.content.Intent.FLAG_ACTIVITY_NEW_DOCUMENT) != 0)
    assertTrue((launchedIntent.flags and android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK) != 0)
    assertEquals("clonix://container/yt-clone-99", launchedIntent.dataString)
  }
}
