package com.eepiemi.materialbook

import androidx.compose.ui.graphics.Color
import com.eepiemi.materialbook.ui.theme.DarkColorScheme
import com.eepiemi.materialbook.ui.theme.FacebookBlue
import com.eepiemi.materialbook.ui.theme.LightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Native chrome (splash, settings sheet, dialogs) must default to Facebook's own
 * blue, not Material You's dynamic/purple defaults — that's the whole point of
 * this fork. Guards against the fallback color schemes drifting off-brand.
 */
class ThemeTest {

    @Test
    fun facebookBlueMatchesBrandColor() {
        assertEquals(Color(0xFF1877F2), FacebookBlue)
    }

    @Test
    fun lightSchemePrimaryIsFacebookBlue() {
        assertEquals(FacebookBlue, LightColorScheme.primary)
    }

    @Test
    fun darkSchemePrimaryIsFacebookBlue() {
        assertEquals(FacebookBlue, DarkColorScheme.primary)
    }

    // Material's default scheme is purple: every colour the app draws with must be set, or
    // switch thumbs, the Settings button and dialogs fall back to it.
    @Test
    fun schemesLeaveNoMaterialDefaultsBehind() {
        val defaults = listOf(lightColorScheme(), darkColorScheme())
        listOf(LightColorScheme to defaults[0], DarkColorScheme to defaults[1]).forEach { (scheme, default) ->
            listOf(
                scheme.primaryContainer to default.primaryContainer,
                scheme.secondaryContainer to default.secondaryContainer,
                scheme.tertiary to default.tertiary,
                scheme.surface to default.surface,
                scheme.surfaceContainer to default.surfaceContainer,
                scheme.surfaceVariant to default.surfaceVariant,
                scheme.outline to default.outline,
            ).forEach { (ours, materials) -> assertNotEquals(materials, ours) }
        }
    }

    @Test
    fun switchThumbsAreWhiteOnFacebookBlue() {
        assertEquals(Color.White, LightColorScheme.onPrimary)
        assertEquals(Color.White, DarkColorScheme.onPrimary)
    }
}
