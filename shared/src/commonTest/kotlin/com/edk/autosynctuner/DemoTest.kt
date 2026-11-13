package com.edk.autosynctuner

import com.edk.autosynctuner.data.DemoBackend
import com.edk.autosynctuner.data.Editor
import com.edk.autosynctuner.ui.Drafts
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Walks the whole demo the way a person (or an app reviewer) would. */
class DemoTest {
    @Test fun everyDemoSettingWorksEndToEnd() = runTest {
        val demo = DemoBackend()
        val profile = demo.profile()
        assertEquals(2, profile.vehicles.size)
        val vin = profile.vehicles.first().vin
        val vehicle = demo.vehicle(vin)
        assertTrue(vehicle.licenseFor("BCM")!!.licensed)
        assertFalse(vehicle.licenseFor("HMI")!!.licensed)

        var count = 0
        for (module in vehicle.modules) {
            assertTrue(vehicle.licenseFor(module.code) != null, "${module.code} has no license row")
            for (ref in module.categories.flatMap { it.settings }) {
                count++
                val page = demo.setting(vin, ref.id)
                assertFalse(page.editor is Editor.Unsupported, ref.name)
                // The stock value text must be understood by the app ("Fill in stock value").
                val stock = Drafts.stock(page)
                assertTrue(stock != null, "stock value not understood for ${ref.name}: ${page.stockValue}")
                val value = Drafts.validate(page, Drafts.initial(page)).getOrThrow()
                val saved = demo.save(vin, page, value, enabled = true)
                assertTrue(saved.isCustomized, ref.name)
                assertTrue(Drafts.matches(saved, value), "read-back mismatch for ${ref.name}")
                val reset = demo.delete(vin, saved)
                assertFalse(reset.isCustomized, ref.name)
            }
        }
        assertTrue(count >= 10)
    }

    @Test fun demoStartsWithSomeCustomizedSettings() = runTest {
        val demo = DemoBackend()
        val vin = demo.profile().vehicles.first().vin
        assertTrue(demo.setting(vin, "359").isCustomized)
        assertEquals("Fog, Low Beam, Park, Puddle, Reverse", Drafts.describe(demo.setting(vin, "359"), Drafts.initial(demo.setting(vin, "359"))))
        assertFalse(demo.setting(vin, "235").isCustomized)
    }
}
