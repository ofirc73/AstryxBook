package com.astryx.book
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What runs at the app's start through androidx.startup. EmojiCompat's initializer (androidx.emoji2,
 * brought in by Compose) is removed in the manifest: it loads an emoji font through Google Play
 * services' FontsProvider, and Android killed the app whenever that process died meanwhile (CI's
 * emulator: "depends on provider … FontsProvider in dying proc"). A library update could bring it
 * back unnoticed. No live check: it's the app's own start, not Facebook's page.
 */
@RunWith(AndroidJUnit4::class)
class StartupInitializersTest {

    private fun initializers(): Set<String> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = context.packageManager.getProviderInfo(
            ComponentName(context, "androidx.startup.InitializationProvider"), PackageManager.GET_META_DATA
        )
        return provider.metaData?.keySet().orEmpty()
    }

    @Test
    fun emojiCompat_doesNotLoadItsFontFromPlayServicesAtStart() {
        val names = initializers()
        assertFalse("EmojiCompat's initializer is back: $names", "androidx.emoji2.text.EmojiCompatInitializer" in names)
        // The rest still run: WorkManager's (Facebook notifications) among them.
        assertTrue("WorkManager's initializer is gone too: $names", "androidx.work.WorkManagerInitializer" in names)
    }
}
