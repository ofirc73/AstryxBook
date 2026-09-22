package com.eepiemi.materialbook

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// Framework internal bitflag for android:supportsPictureInPicture
private const val FLAG_SUPPORTS_PICTURE_IN_PICTURE = 0x00400000

/**
 * Guards android:supportsPictureInPicture on MainActivity from silently
 * regressing; nothing else in the suite would catch a future manifest
 * edit dropping it; PiP would just stop working with no obvious signal why.
 */
@RunWith(AndroidJUnit4::class)
class PipManifestTest {

    @Test
    fun mainActivitySupportsPictureInPicture() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val componentName = ComponentName(context, MainActivity::class.java)
        val activityInfo = context.packageManager.getActivityInfo(componentName, 0)
        val supportsPip = (activityInfo.flags and FLAG_SUPPORTS_PICTURE_IN_PICTURE) != 0

        assertTrue(
            "MainActivity must declare android:supportsPictureInPicture",
            supportsPip
        )
    }
}