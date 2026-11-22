package com.example

import com.example.theme.BundledThemes
import com.example.theme.ContrastChecker
import com.example.theme.ThemePackManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastAndPackTest {

    @Test
    fun allBundledThemesPassWcagAaBodyAndGlassContrast() {
        assertTrue("Expected at least 35 bundled themes", BundledThemes.all.size >= 35)
        for (palette in BundledThemes.all) {
            val report = ContrastChecker.evaluate(palette)
            assertTrue(
                "Theme ${palette.name} (${palette.id}) failed body contrast: ${report.bodyOnBackground}",
                report.passesBodyAa
            )
            assertTrue(
                "Theme ${palette.name} (${palette.id}) failed secondary contrast: ${report.secondaryOnBackground}",
                report.passesSecondaryAa
            )
            assertTrue(
                "Theme ${palette.name} (${palette.id}) failed glass contrast: ${report.bodyOnGlass}",
                report.passesGlassAa
            )
        }
    }

    @Test
    fun themeJsonRoundTripPreservesTokens() {
        val original = BundledThemes.byId(BundledThemes.DEFAULT_LIGHT_ID)
        val json = ThemePackManager.toJson(original)
        val restored = ThemePackManager.fromJson(json)
        assertNotNull(restored)
        assertEquals(original.id, restored?.id)
        assertEquals(original.name, restored?.name)
        assertEquals(original.background, restored?.background)
        assertEquals(original.highlightColors, restored?.highlightColors)
        assertEquals(original.shelfWoodTint, restored?.shelfWoodTint)
    }
}
