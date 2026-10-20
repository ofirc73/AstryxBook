package com.eepiemi.materialbook

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eepiemi.materialbook.data.local.SettingsDataStore
import com.eepiemi.materialbook.utils.AppLock
import com.eepiemi.materialbook.utils.canUseAppLock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * App lock (Settings, Privacy): the page is covered until the user unlocks with the screen lock
 * (or a fingerprint), on a cold start and on coming back after the grace period; off, or on a
 * phone without a screen lock, the app opens as usual. The unlock itself needs the screen lock's
 * PIN (`-e pin`); without one those checks are skipped (as in CI, whose emulator has no lock).
 */
@RunWith(AndroidJUnit4::class)
class AppLockTest {

    private val app = AppHarness()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val pin: String? = InstrumentationRegistry.getArguments().getString("pin")

    @Before
    fun setUp() {
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    @After
    fun tearDown() {
        app.finish()
        runBlocking { SettingsDataStore(app.context).setAppLock(AppLock.OFF) }
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    private fun setLock(mode: String) = runBlocking { SettingsDataStore(app.context).setAppLock(mode) }

    private fun locked() = app.onActivity { it.isAppLocked }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes() }
    }

    // The system's prompt is up: the screen lock's PIN, as the user types it (again if the prompt
    // wasn't up yet the first time).
    private fun unlockWithPin() {
        for (attempt in 1..3) {
            Thread.sleep(2000)
            shell("input text $pin")
            shell("input keyevent KEYCODE_ENTER")
            if (app.waitFor(5_000) { !locked() }) return
        }
    }

    @Test
    fun lockOff_opensTheAppUnlocked() {
        setLock(AppLock.OFF)
        app.start()
        Thread.sleep(2000)
        assertFalse("the app locked with app lock off", locked())
    }

    @Test
    fun lockOn_withoutAScreenLock_opensTheAppUnlocked() {
        // Nothing could unlock it, so it mustn't lock the user out.
        assumeTrue(!canUseAppLock(app.context))
        setLock(AppLock.IMMEDIATELY)
        app.start()
        Thread.sleep(2000)
        assertFalse("the app locked on a phone without a screen lock", locked())
    }

    @Test
    fun lockOn_locksAColdStart_andThePinUnlocksIt() {
        assumeTrue(canUseAppLock(app.context) && pin != null)
        setLock(AppLock.IMMEDIATELY)
        app.start()
        assertTrue("a cold start wasn't locked", app.waitFor { locked() })
        unlockWithPin()
        assertTrue("the PIN never unlocked the app", app.waitFor(10_000) { !locked() })
    }

    @Test
    fun lockOn_locksAgainOnComingBack() {
        assumeTrue(canUseAppLock(app.context) && pin != null)
        setLock(AppLock.IMMEDIATELY)
        app.start()
        assertTrue(app.waitFor { locked() })
        unlockWithPin()
        assertTrue(app.waitFor(10_000) { !locked() })

        app.pressHome()
        assertTrue(app.waitFor { app.isInBackground() })
        app.start()
        assertTrue("coming back wasn't locked (grace period: immediately)", app.waitFor { locked() })
        unlockWithPin()
        assertTrue(app.waitFor(10_000) { !locked() })
    }

    @Test
    fun aGracePeriod_letsAQuickReturnIn() {
        assumeTrue(canUseAppLock(app.context) && pin != null)
        setLock(AppLock.AFTER_5_MIN)
        app.start()
        assertTrue(app.waitFor { locked() })
        unlockWithPin()
        assertTrue(app.waitFor(10_000) { !locked() })

        app.pressHome()
        assertTrue(app.waitFor { app.isInBackground() })
        app.start()
        Thread.sleep(2000)
        assertFalse("a return within the grace period was locked", locked())
    }
}
