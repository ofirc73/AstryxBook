package com.astryx.book
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eepiemi.materialbook.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A Facebook link tapped while the app runs must reach the running MainActivity
 * (onNewIntent), not start a second copy with its own WebView.
 */
@RunWith(AndroidJUnit4::class)
class LinkHandlingManifestTest {

    @Test
    fun mainActivityIsSingleTask() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activityInfo = context.packageManager
            .getActivityInfo(ComponentName(context, MainActivity::class.java), 0)

        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, activityInfo.launchMode)
    }
}
