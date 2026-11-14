package com.astryx.book.ui.components.settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.astryx.book.utils.AppLock
import com.eepiemi.materialbook.R
import com.eepiemi.materialbook.data.local.SettingsDataStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings' own screens, composed alone: the choice dialogs and the Facebook notifications row.
 * No live check: these are the app's screens, not Facebook's.
 */
@RunWith(AndroidJUnit4::class)
class SettingsDialogsTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        runBlocking { SettingsDataStore(context).setFacebookNotifications(false) }
    }

    @Test
    fun aChoiceDialog_reachesItsLastChoice_whenItDoesntFitTheWindow() {
        // At Android's largest font size in a split-screen-sized window, App lock's last choice ran
        // off the dialog with no way to reach it (seen on the emulator). This emulator's window is
        // taller: the same overflow at twice that font size.
        lateinit var last: String
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 4f)) {
                val labels = AppLock.ALL.associateWith { appLockLabel(it) }
                last = labels.values.last()
                ChoiceDialog(
                    title = "App lock",
                    options = labels,
                    current = AppLock.OFF,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText(last).performScrollTo().assertIsDisplayed()
    }

    // Facebook notifications on, Android letting the app notify or not ([allowed]).
    private fun showNotificationsRow(allowed: Boolean) {
        runBlocking { SettingsDataStore(context).setFacebookNotifications(true) }
        compose.setContent { NotificationsSettings(viewModel = viewModel(), canNotify = { allowed }) }
    }

    @Test
    fun facebookNotificationsOn_butBlockedInAndroid_saysSo() {
        // The permission withdrawn, or settings imported on a new phone: the worker ran and every
        // notification was dropped, with the row looking as if all was well.
        showNotificationsRow(allowed = false)
        compose.onNodeWithText(context.getString(R.string.facebook_notifications_denied)).assertIsDisplayed()
    }

    @Test
    fun facebookNotificationsOn_andAllowed_showsWhatItDoes() {
        showNotificationsRow(allowed = true)
        compose.onNodeWithText(context.getString(R.string.facebook_notifications_summary)).assertIsDisplayed()
    }
}
