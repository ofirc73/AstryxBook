package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * video_catch_up.js, shared by the reel back from its comments (reel_comments_audio.js) and the PiP
 * video back from the lock screen (pipHandbackJs, PipHandoffJsTest): each use's instance keeps its
 * own clock and remembered seek time. Faked: two videos' paused, seeking, readyState, duration and
 * currentTime, and each use's clock (window.__clockA, __clockB).
 */
@RunWith(AndroidJUnit4::class)
class VideoCatchUpJsTest {

    private val page = PageFixture(domStorage = true)

    private fun eval(js: String) = page.eval(js)

    private fun load() {
        page.load(
            "https://example.com/",
            "<html><body><video id='a'></video><video id='b'></video></body></html>"
        )
        eval(
            """
            localStorage.clear();
            window.__clockA = 10; window.__clockB = 50;
            ['a', 'b'].forEach(function (id) {
              var v = document.getElementById(id);
              v.__time = 0; v.__rs = 4;
              Object.defineProperty(v, 'paused', { get: function () { return false; } });
              Object.defineProperty(v, 'seeking', { get: function () { return false; } });
              Object.defineProperty(v, 'readyState', { get: function () { return this.__rs; } });
              Object.defineProperty(v, 'duration', { get: function () { return 60; } });
              Object.defineProperty(v, 'currentTime', { get: function () { return this.__time; }, set: function (t) { this.__time = t; } });
            });
            1
            """.trimIndent()
        )
        page.run(R.raw.video_catch_up)
        eval(
            "window.__a = __astryxCatchUp(document.getElementById('a'), function () { return __clockA; }, { leadKey: 'leadA', firstLead: 0.3 });" +
                " window.__b = __astryxCatchUp(document.getElementById('b'), function () { return __clockB; }, { leadKey: 'leadB', firstLead: 0.6 }); 1"
        )
    }

    @Test
    fun eachUse_keepsItsOwnClockAndSeekTime() {
        load()
        assertEquals(10.3, eval("__a.target()").toDouble(), 0.001)
        assertEquals(50.6, eval("__b.target()").toDouble(), 0.001)
        // A's move takes a moment, and A learns from it: B doesn't.
        eval("__a.move(__a.target()); document.getElementById('a').__rs = 1; 1")
        Thread.sleep(300)
        eval("document.getElementById('a').__rs = 4; __a.step(); 1")
        assertEquals("true", eval("parseFloat(localStorage.getItem('leadA')) > 0.3"))
        assertEquals("null", eval("localStorage.getItem('leadB')"))
        assertEquals(50.6, eval("__b.target()").toDouble(), 0.001)
        assertEquals("B's video was touched", "0", eval("document.getElementById('b').currentTime"))
    }

    @Test
    fun aSeekTimeKeptFromBefore_isUsed() {
        load()
        eval("localStorage.setItem('leadA', '1.2'); window.__a2 = __astryxCatchUp(document.getElementById('a'), function () { return __clockA; }, { leadKey: 'leadA', firstLead: 0.3 }); 1")
        assertEquals(11.2, eval("__a2.target()").toDouble(), 0.001)
    }

    @Test
    fun step_inStep_heldStillAhead_fasterBehind_orApart() {
        load()
        val a = "document.getElementById('a')"
        fun stepAt(time: Double) = eval("$a.__time = $time; __a.step()").removeSurrounding("\"")
        assertEquals("inStep", stepAt(10.05))
        assertEquals("wait", stepAt(10.3))
        assertEquals("ahead: held still", "0.0625", eval("$a.playbackRate"))
        assertEquals("wait", stepAt(9.7))
        assertEquals("behind: faster by the gap", "1.3", eval("$a.playbackRate"))
        assertEquals("wait", stepAt(13.0))
        assertEquals("0.0625", eval("$a.playbackRate"))
        assertEquals("apart", stepAt(15.0))
        assertEquals("apart", stepAt(7.0))
        eval("$a.__rs = 1; 1")
        assertEquals("not ready yet", "wait", stepAt(10.0))
    }

    @Test
    fun theVideosVeryEnd_isTheStartInstead_andHoldAndReleaseSetItsSpeed() {
        load()
        val a = "document.getElementById('a')"
        assertEquals("0", eval("__a.place(59.6)"))
        assertEquals(59.4, eval("__a.place(59.4)").toDouble(), 0.001)
        eval("__a.hold(); 1")
        assertEquals("0.0625", eval("$a.playbackRate"))
        eval("__a.release(); 1")
        assertEquals("1", eval("$a.playbackRate"))
        // A move within a second needs no seek.
        eval("$a.__time = 10; 1")
        assertEquals("false", eval("__a.move(10.5)"))
        assertEquals("true", eval("__a.move(20)"))
        assertEquals("20", eval("$a.currentTime"))
    }
}
