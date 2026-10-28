package com.astryx.book
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.astryx.book.data.local.EXPORTED_BOOLEAN_SETTINGS
import com.astryx.book.data.local.EXPORTED_STRING_SETTINGS
import com.astryx.book.data.local.NOT_EXPORTED
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.HIDE_REELS
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.KEEP_SCREEN_ON
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.REMOVE_ADS
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.TEXT_ZOOM
import com.eepiemi.materialbook.data.local.SettingsDataStore.Companion.WEBVIEW_DEBUGGING
import com.astryx.book.data.local.exportSettingsJson
import com.astryx.book.data.local.importSettingsJson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

// Robolectric for org.json (Android's own copy; the plain JVM stub has no implementation).
@RunWith(RobolectricTestRunner::class)
class SettingsBackupTest {

    @Test
    fun exportThenImport_restoresTheSettings() {
        val source = mutablePreferencesOf(
            REMOVE_ADS to false,
            HIDE_REELS to true,
            KEEP_SCREEN_ON to "video",
            TEXT_ZOOM to "130",
        )
        val target = mutablePreferencesOf()

        val applied = importSettingsJson(exportSettingsJson(source), target)

        assertEquals(4, applied)
        assertEquals(false, target[REMOVE_ADS])
        assertEquals(true, target[HIDE_REELS])
        assertEquals("video", target[KEEP_SCREEN_ON])
        assertEquals("130", target[TEXT_ZOOM])
    }

    @Test
    fun export_leavesOutDefaultsAndDebugging() {
        val json = JSONObject(exportSettingsJson(mutablePreferencesOf(WEBVIEW_DEBUGGING to true, HIDE_REELS to true)))

        assertEquals("astryxbook-settings", json.getString("format"))
        assertEquals(1, json.getInt("version"))
        val settings = json.getJSONObject("settings")
        assertEquals(setOf("hide_reels"), settings.keys().asSequence().toSet())
    }

    @Test
    fun import_skipsWrongTypesUnknownKeysAndDebugging() {
        val file = """
            {"format": "astryxbook-settings", "version": 1, "settings": {
              "remove_ads": "yes",
              "keep_screen_on": true,
              "hide_reels": true,
              "webview_debugging": true,
              "developer_options": true,
              "something_else": 1,
              "text_zoom": "${"9".repeat(100)}"
            }}
        """.trimIndent()
        val target = mutablePreferencesOf()

        assertEquals(1, importSettingsJson(file, target))
        assertEquals(true, target[HIDE_REELS])
        assertNull(target[REMOVE_ADS])
        assertNull(target[KEEP_SCREEN_ON])
        assertNull(target[WEBVIEW_DEBUGGING])
        assertNull(target[TEXT_ZOOM])
    }

    @Test
    fun import_refusesFilesThatArentSettingsFiles() {
        listOf(
            "",
            "not json",
            "[]",
            """{"settings": {"hide_reels": true}}""",
            """{"format": "something-else", "version": 1, "settings": {}}""",
            """{"format": "astryxbook-settings", "version": 2, "settings": {"hide_reels": true}}""",
            """{"format": "astryxbook-settings", "version": 1}""",
        ).forEach { file ->
            val target = mutablePreferencesOf()
            assertThrows(file, IllegalArgumentException::class.java) { importSettingsJson(file, target) }
            assertTrue(file, target.asMap().isEmpty())
        }
    }

    @Test
    fun everySettingIsClassified() {
        // A new key in SettingsDataStore must either travel (and be listed with its type) or
        // be named in NOT_EXPORTED: an unlisted one would silently be left out of backups.
        val source = listOf(File("src/main/java"), File("app/src/main/java"))
            .first { it.isDirectory }
            .resolve("com/eepiemi/materialbook/data/local/SettingsDataStore.kt")
            .readText()
        val declared = Regex("""(?:boolean|string)PreferencesKey\("([^"]+)"\)""")
            .findAll(source).map { it.groupValues[1] }.toSet()
        val classified = (EXPORTED_BOOLEAN_SETTINGS + EXPORTED_STRING_SETTINGS).map { it.name }.toSet() + NOT_EXPORTED

        assertEquals(declared, classified)
    }

    @Test
    fun noSettingIsBothExportedAndExcluded() {
        val exported = (EXPORTED_BOOLEAN_SETTINGS + EXPORTED_STRING_SETTINGS).map { it.name }
        assertEquals(exported.size, exported.toSet().size)
        exported.forEach { assertFalse(it, it in NOT_EXPORTED) }
    }
}
