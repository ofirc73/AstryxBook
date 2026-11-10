package com.astryx.book.utils
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import com.eepiemi.materialbook.R

/**
 * Puts a post's text, held down on the page (copy_post_text.js), on the clipboard, with the
 * long-press buzz from [view] (the page's WebView), as the Facebook app does. Android 13 and later
 * show their own note of what was copied; below that a toast says so.
 */
fun copyTextToClipboard(view: View, text: String) {
    val context = view.context
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.post_text_clip_label), text))
    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.post_text_copied, Toast.LENGTH_SHORT).show()
    }
}
