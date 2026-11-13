package com.example

import com.example.data.model.DevicePlatform
import com.example.data.model.LaunchMode
import com.example.data.model.VirtualModelCatalog
import com.example.data.model.VirtualRegionCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM checks for the device catalog. These guard the promises the UI makes: every
 * preset is internally coherent (SoC/GPU/screen/API level) and no two presets collide,
 * so a synthesized identity never mixes impossible values.
 */
class DeviceCatalogTest {

    @Test
    fun `samsung galaxy s25 ultra is available with coherent hardware`() {
        val preset = VirtualModelCatalog.findByModel("SM-S938B")

        assertNotNull("S25 Ultra preset must exist", preset)
        requireNotNull(preset)
        assertEquals("samsung", preset.brand)
        assertEquals("Samsung", preset.manufacturer)
        assertEquals("Samsung Galaxy S25 Ultra", preset.displayName)
        assertEquals(DevicePlatform.ANDROID, preset.platform)
        assertEquals(35, preset.sdkInt)
        assertEquals(1440, preset.screenWidth)
        assertEquals(3120, preset.screenHeight)
        assertTrue("Snapdragon preset must report an Adreno GPU", preset.gpuRenderer.contains("Adreno"))
        assertEquals("Qualcomm", preset.gpuVendor)
        assertEquals(8, preset.imeiTac.length)
    }

    @Test
    fun `android catalog models are unique and internally consistent`() {
        val models = VirtualModelCatalog.android.map { it.model }
        assertEquals("Model codes must be unique", models.size, models.toSet().size)

        VirtualModelCatalog.android.forEach { preset ->
            assertTrue("${preset.model} needs a marketing name", preset.displayName.isNotBlank())
            assertTrue("${preset.model} API level", preset.sdkInt in 33..35)
            assertTrue("${preset.model} screen", preset.screenWidth in 700..1500)
            assertTrue("${preset.model} height", preset.screenHeight > preset.screenWidth)
            assertTrue("${preset.model} density", preset.densityDpi in 320..600)
            assertTrue("${preset.model} css width", preset.cssWidth in 340..520)
            assertTrue("${preset.model} css height", preset.cssHeight in 600..1060)
            assertTrue("${preset.model} cores", preset.cpuCores in 6..12)
            assertTrue("${preset.model} ram", preset.ramGb in 6..16)
            assertEquals("${preset.model} imei tac digits", 8, preset.imeiTac.length)
            assertTrue(
                "${preset.model} GPU must match its vendor",
                when (preset.gpuVendor) {
                    "Qualcomm" -> preset.gpuRenderer.contains("Adreno")
                    "ARM" -> preset.gpuRenderer.contains("Mali") || preset.gpuRenderer.contains("Xclipse") ||
                        preset.gpuRenderer.contains("Immortalis")
                    else -> preset.gpuRenderer.isNotBlank()
                }
            )
            assertTrue("${preset.model} fingerprint pieces", preset.deviceCode.isNotBlank() && preset.buildId.isNotBlank())
        }
    }

    @Test
    fun `ios catalog stays Apple only`() {
        VirtualModelCatalog.ios.forEach { preset ->
            assertEquals(DevicePlatform.IOS, preset.platform)
            assertEquals("Apple", preset.manufacturer)
            assertTrue(preset.model.startsWith("iPhone"))
            assertTrue(preset.displayName.startsWith("Apple iPhone"))
        }
        assertEquals(
            VirtualModelCatalog.ios.size,
            VirtualModelCatalog.forPlatform(DevicePlatform.IOS).size
        )
    }

    @Test
    fun `every region carries coherent locale carrier and timezone data`() {
        assertTrue(VirtualRegionCatalog.regions.size >= 8)
        VirtualRegionCatalog.regions.forEach { region ->
            assertEquals(2, region.code.length)
            assertEquals(3, region.mcc.length)
            assertTrue(region.localeTag.contains("-"))
            assertEquals(region.code, region.localeTag.substringAfter("-"))
            assertTrue("${region.code} must expose android presets", region.deviceModels.isNotEmpty())
            assertTrue("${region.code} must expose ios presets", region.iosModels.isNotEmpty())
            assertTrue("${region.code} timezone", region.timezoneId.contains("/"))
            assertTrue("${region.code} carrier", region.carrierName.isNotBlank())
            assertTrue("${region.code} coordinates", region.latitude in -90.0..90.0 && region.longitude in -180.0..180.0)
        }
    }

    @Test
    fun `legacy launch modes map to the isolated container`() {
        assertEquals(LaunchMode.ISOLATED_CONTAINER, LaunchMode.fromStored("NATIVE_BRIDGE"))
        assertEquals(LaunchMode.ISOLATED_CONTAINER, LaunchMode.fromStored(null))
        assertEquals(1, LaunchMode.entries.size)
    }
}
