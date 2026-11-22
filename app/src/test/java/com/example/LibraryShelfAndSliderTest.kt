package com.example

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.preferences.MochiPreferences
import com.example.data.preferences.ReaderPreset
import com.example.theme.BundledThemes
import com.example.theme.GlassIntensity
import com.example.theme.ShelfStyle
import com.example.ui.designsystem.GlassSwitchItem
import com.example.ui.designsystem.LiquidGlassSlider
import com.example.ui.designsystem.LiquidGlassViewSwitcher
import com.example.ui.theme.MochiExpressiveTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LibraryShelfAndSliderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun skeuomorphicShelfOptInAndGlassShelfTransitionsWork() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = MochiPreferences(context)
        prefs.resetDefaults()

        val initial = prefs.state.first()
        assertFalse(initial.skeuomorphicShelfEnabled)
        assertEquals(ShelfStyle.FLAT_GRID, initial.defaultShelfStyle)

        // Switching to Glass Shelf works with zero skeuomorphism enabled
        prefs.setShelfStyle(ShelfStyle.GLASS_SHELF)
        assertEquals(ShelfStyle.GLASS_SHELF, prefs.state.first().defaultShelfStyle)

        // Opting in upgrades Glass Shelf to Wooden Shelf, opting out reverts Wooden Shelf back to Glass Shelf
        prefs.setSkeuomorphicShelfEnabled(true)
        var updated = prefs.state.first()
        assertTrue(updated.skeuomorphicShelfEnabled)
        assertEquals(ShelfStyle.WOODEN_SHELF, updated.defaultShelfStyle)

        prefs.setSkeuomorphicShelfEnabled(false)
        updated = prefs.state.first()
        assertFalse(updated.skeuomorphicShelfEnabled)
        assertEquals(ShelfStyle.GLASS_SHELF, updated.defaultShelfStyle)

        // Switching to List & Grid persists properly, Cozy preset chooses non-skeuomorphic Glass Shelf
        prefs.setShelfStyle(ShelfStyle.LIST)
        assertEquals(ShelfStyle.LIST, prefs.state.first().defaultShelfStyle)
        prefs.applyPreset(ReaderPreset.COZY)
        assertEquals(ShelfStyle.GLASS_SHELF, prefs.state.first().defaultShelfStyle)
    }

    @Test
    fun liquidGlassViewSwitcherSwitchesBetweenGridListAndShelf() {
        var activeStyle by mutableStateOf(ShelfStyle.FLAT_GRID)
        val palette = BundledThemes.byId(BundledThemes.DEFAULT_LIGHT_ID)

        composeRule.setContent {
            MochiExpressiveTheme(
                palette = palette,
                glassIntensity = GlassIntensity.RICH,
                solidChrome = false,
                reduceMotion = true,
                useDynamicColor = false,
                matchSystemBars = false,
                warmLightEnabled = false,
                warmLightIntensity = 0f
            ) {
                LiquidGlassViewSwitcher(
                    items = listOf(
                        GlassSwitchItem(ShelfStyle.FLAT_GRID, "Grid", Icons.Rounded.Apps, "shelf_style_grid_button"),
                        GlassSwitchItem(ShelfStyle.LIST, "List", Icons.Rounded.ViewAgenda, "shelf_style_list_button"),
                        GlassSwitchItem(ShelfStyle.GLASS_SHELF, "Shelf", Icons.Rounded.Bookmarks, "shelf_style_shelf_button")
                    ),
                    selected = activeStyle,
                    onSelect = { activeStyle = it }
                )
            }
        }

        composeRule.onNodeWithTag("shelf_style_list_button").performClick()
        assertEquals(ShelfStyle.LIST, activeStyle)

        composeRule.onNodeWithTag("shelf_style_shelf_button").performClick()
        assertEquals(ShelfStyle.GLASS_SHELF, activeStyle)

        composeRule.onNodeWithTag("shelf_style_grid_button").performClick()
        assertEquals(ShelfStyle.FLAT_GRID, activeStyle)
    }

    @Test
    fun liquidGlassSliderRendersWithTicksInBothGlassAndSolidModes() {
        var sliderVal by mutableFloatStateOf(0.5f)
        var solidMode by mutableStateOf(false)
        val palette = BundledThemes.byId(BundledThemes.DEFAULT_LIGHT_ID)

        composeRule.setContent {
            MochiExpressiveTheme(
                palette = palette,
                glassIntensity = GlassIntensity.RICH,
                solidChrome = solidMode,
                reduceMotion = true,
                useDynamicColor = false,
                matchSystemBars = false,
                warmLightEnabled = false,
                warmLightIntensity = 0f
            ) {
                LiquidGlassSlider(
                    value = sliderVal,
                    onValueChange = { sliderVal = it },
                    valueRange = 0f..10f,
                    tickFractions = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
                )
            }
        }

        composeRule.waitForIdle()
        solidMode = true
        composeRule.waitForIdle()
        assertEquals(0.5f, sliderVal, 0.001f)
    }
}
