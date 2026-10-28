package com.astryx.book.utils
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import com.eepiemi.materialbook.MainActivity
import com.eepiemi.materialbook.R

/** A launcher shortcut (long-press the app icon): a Facebook page the app opens at. */
data class AppShortcut(val id: String, val url: String, @param:StringRes val label: Int, @param:DrawableRes val icon: Int)

val APP_SHORTCUTS = listOf(
    // Messages links open where the Messages tab would take them (see MaterialbookWebView).
    AppShortcut("messages", "https://m.facebook.com/messages/", R.string.shortcut_messages, R.drawable.ic_shortcut_messages),
    AppShortcut("notifications", "https://m.facebook.com/notifications/", R.string.shortcut_notifications, R.drawable.ic_shortcut_notifications),
    AppShortcut("marketplace", "https://m.facebook.com/marketplace/", R.string.shortcut_marketplace, R.drawable.ic_shortcut_marketplace),
)

/**
 * Publishes [APP_SHORTCUTS] as dynamic shortcuts. Dynamic rather than res/xml/shortcuts.xml:
 * a static shortcut names its target package, which differs in debug builds
 * (com.astryx.book.test). Republished on each launch, so labels follow the language.
 */
fun publishAppShortcuts(context: Context) {
    val shortcuts = APP_SHORTCUTS.map { shortcut ->
        val label = context.getString(shortcut.label)
        ShortcutInfoCompat.Builder(context, shortcut.id)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithResource(context, shortcut.icon))
            .setIntent(Intent(Intent.ACTION_VIEW, shortcut.url.toUri(), context, MainActivity::class.java))
            .build()
    }
    runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
}
