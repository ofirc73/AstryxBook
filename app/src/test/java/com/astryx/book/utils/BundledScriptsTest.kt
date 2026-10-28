package com.astryx.book.utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledScriptsTest {

    private fun contentFor(resourceId: Int) = "content-$resourceId;"

    // The scripts' own text, in the order they appear in the bundle.
    private fun contents(bundle: String) = Regex("content-\\d+;").findAll(bundle).map { it.value }.toList()

    @Test
    fun concatenatesEnabledScripts_inCallerOrder() {
        val result = bundledScripts(
            listOf(Script(true, 2), Script(true, 1), Script(true, 3)),
            ::contentFor
        )

        assertEquals(listOf("content-2;", "content-1;", "content-3;"), contents(result))
    }

    @Test
    fun skipsDisabledScripts() {
        val result = bundledScripts(
            listOf(Script(true, 1), Script(false, 2)),
            ::contentFor
        )

        assertEquals(listOf("content-1;"), contents(result))
    }

    @Test
    fun eachScript_isInItsOwnTryBlock() {
        val result = bundledScripts(listOf(Script(true, 1), Script(true, 2)), ::contentFor)

        assertEquals(2, Regex("try \\{\\ncontent-\\d+;\\n\\} catch").findAll(result).count())
        assertTrue(result.trimEnd().endsWith("}"))
    }
}
