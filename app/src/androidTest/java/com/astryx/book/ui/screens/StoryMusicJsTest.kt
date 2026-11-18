package com.astryx.book.ui.screens
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.astryx.book.assertTrue
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private fun String.unquoted() = removeSurrounding("\"")

/**
 * story_music.js, the native music picker's side in Facebook's story creator, on a fake creator as
 * read on the emulator (2026-11-18): the Add music tool, its panel (search box, rows with Play), a
 * chosen song's card (lyrics styles, Done) and clip bar (Pause / Play, the scrolling strip, Done).
 * Facebook's GraphQL answers come through a stubbed XMLHttpRequest, which the script wraps.
 */
@RunWith(AndroidJUnit4::class)
class StoryMusicJsTest {

    private val page = PageFixture(width = 1080, height = 2000)

    private fun open(body: String = CREATOR) {
        page.load("https://www.facebook.com/stories/create/", "<html><body style='margin:0'>$body</body></html>")
        page.eval(FAKE_XHR)
        page.eval("window.__music = []; window.AstryxShare = { postMessage: function (m) { window.__music.push(JSON.parse(m)); } }; 1")
        page.run(R.raw.story_music)
    }

    private fun eval(js: String) = page.eval(js)
    private fun waitFor(js: String) = page.waitFor { eval(js) == "true" }

    // Facebook's panel asking for songs ([search]: its search text), answered with [answer].
    private fun answer(search: String, answer: String) = eval(
        "window.__answer = ${org.json.JSONObject.quote(answer)};" +
            " var x = new XMLHttpRequest(); x.open('POST', '/api/graphql/');" +
            " x.send('fb_api_req_friendly_name=StoriesCreateMusicSelectorMainPageQuery&variables=' +" +
            " encodeURIComponent(JSON.stringify({ params: { first: 20, search_text: ${org.json.JSONObject.quote(search)} } }))); 1"
    )

    @Test
    fun theSongsInFacebooksAnswer_goToTheApp_byTheSearchTheyAnswer() {
        open()
        answer("", ANSWER)
        assertTrue({ "no songs reported: ${eval("JSON.stringify(window.__music)")}" }, waitFor("window.__music.length === 1"))
        assertEquals(
            "\"music story '' 2: 1 Sunshine on My Shoulders/John Denver https://scontent.x.fbcdn.net/c1.jpg 313440, 2 Sunflower/Post Malone, Swae Lee https://scontent.x.fbcdn.net/c2.jpg 158040\"",
            eval("(function (m) { return m.call + ' ' + m.target + ' \\'' + m.query + '\\' ' + m.songs.length + ': ' + m.songs.map(function (s) {" +
                " return s.id + ' ' + s.title + '/' + s.artist + ' ' + s.cover + ' ' + s.durationMs; }).join(', '); })(window.__music[0])")
        )
        answer("sun", ANSWER)
        assertTrue(waitFor("window.__music.length === 2 && window.__music[1].query === 'sun'"))
    }

    @Test
    fun anAnswerOutsideTheCreator_isLeftAlone() {
        open()
        eval("history.pushState({}, '', '/stories/'); 1")
        answer("", ANSWER)
        Thread.sleep(800)
        assertEquals("0", eval("window.__music.length"))
    }

    @Test
    fun open_pressesAddMusic_onlyOnceTheEditorShowsTheStory() {
        open(CREATOR.replace("<img", "<span"))
        assertEquals("\"notReady\"", eval("window.__astryxStoryMusic.open()"))
        open()
        assertEquals("\"opening\"", eval("window.__astryxStoryMusic.open()"))
        assertTrue(waitFor("window.__astryxStoryMusic.state().panel"))
        assertEquals("\"open\"", eval("window.__astryxStoryMusic.open()"))
    }

    @Test
    fun search_typesIntoFacebooksBox_andPressesEnter() {
        open()
        eval("window.__astryxStoryMusic.open(); 1")
        assertTrue(waitFor("window.__astryxStoryMusic.state().panel"))
        assertEquals("\"searching\"", eval("window.__astryxStoryMusic.search('sunshine')"))
        assertEquals("the box didn't search what was typed", "\"sunshine\"", eval("window.__searched"))
    }

    @Test
    fun aSong_isPlayedAndChosen_throughItsRowInThePanel() {
        open()
        answer("", ANSWER)
        assertTrue(waitFor("window.__music.length === 1"))
        eval("window.__astryxStoryMusic.open(); 1")
        assertTrue(waitFor("window.__astryxStoryMusic.state().rows === 2"))
        assertEquals("\"ok\"", eval("window.__astryxStoryMusic.play('2')"))
        assertEquals("\"Sunflower\"", eval("window.__played"))
        assertEquals("\"noRow\"", eval("window.__astryxStoryMusic.choose('9')"))
        assertEquals("\"ok\"", eval("window.__astryxStoryMusic.choose('1')"))
        assertTrue("no clip bar once chosen", waitFor("window.__astryxStoryMusic.state().chosen"))
    }

    @Test
    fun theChosenSong_hasItsStyleItsStripItsReplayAndItsDone() {
        open()
        answer("", ANSWER)
        assertTrue(waitFor("window.__music.length === 1"))
        eval("window.__astryxStoryMusic.open(); 1")
        assertTrue(waitFor("window.__astryxStoryMusic.state().rows === 2"))
        eval("window.__astryxStoryMusic.choose('1'); 1")
        assertTrue(waitFor("window.__astryxStoryMusic.state().chosen"))
        assertEquals("\"ok\"", eval("window.__astryxStoryMusic.style(2)"))
        assertEquals("\"Dark lyrics style\"", eval("window.__style"))
        // The strip: where it is and how far it scrolls (its content less its own width).
        assertEquals("\"200 3000 120\"", eval("(function (s) { return Math.round(s.width) + ' ' + Math.round(s.range) + ' ' + Math.round(s.scroll); })" +
            "(window.__astryxStoryMusic.strip())"))
        // Replay: the bar's Pause, then (once shown) its Play; the start is where it plays from.
        // The preview playing at 40 s (an <audio> without a source can't play: its state stubbed).
        eval("(function (a) { Object.defineProperty(a, 'paused', { get: function () { return false; } });" +
            " Object.defineProperty(a, 'currentTime', { get: function () { return 40; } }); })(document.querySelector('audio'));" +
            " window.__astryxStoryMusic.replay(); 1")
        assertTrue("Play wasn't pressed after Pause", waitFor("window.__replayed === 'pause,play'"))
        val start = eval("window.__astryxStoryMusic.position()").toLong()
        assertTrue("the start read was $start", start in 39_000..40_000)
        assertEquals("\"ok\"", eval("window.__astryxStoryMusic.done()"))
        assertEquals("the card's Done and the bar's weren't both pressed", "\"card,bar\"", eval("window.__done.join()"))
    }

    companion object {
        // XMLHttpRequest answering every request with window.__answer, a moment later.
        private const val FAKE_XHR = "XMLHttpRequest.prototype.send = function () { var x = this;" +
            " Object.defineProperty(x, 'responseText', { get: function () { return window.__answer; } });" +
            " setTimeout(function () { x.dispatchEvent(new Event('load')); }, 10); }; 1"

        // Facebook's answer: its songs as AudioAssets inside the music picker's connection.
        private const val ANSWER = "{\"data\":{\"xfb_music_picker_connection_container\":{\"items\":{\"edges\":[" +
            "{\"node\":{\"item\":{\"__typename\":\"AudioAsset\",\"id\":\"1\",\"title\":{\"text\":\"Sunshine on My Shoulders\"}," +
            "\"display_artist\":{\"text\":\"John Denver\"},\"cover_artwork\":{\"uri\":\"https://scontent.x.fbcdn.net/c1.jpg\"},\"duration_in_ms\":313440}}}," +
            "{\"node\":{\"item\":{\"__typename\":\"AudioAsset\",\"id\":\"2\",\"title\":{\"text\":\"Sunflower\"}," +
            "\"display_artist\":{\"text\":\"Post Malone, Swae Lee\"},\"cover_artwork\":{\"uri\":\"https://scontent.x.fbcdn.net/c2.jpg\"},\"duration_in_ms\":158040}}}" +
            "]}}}}\nfor (;;);{\"extensions\":{}}"

        // The creator: the story, the tools (Add music opens the panel); the panel's search box
        // (Enter records what it searches) and its rows (Play records, a row's tap shows the card
        // and the clip bar); the card's lyrics styles and Done; the bar's Pause / Play, strip and Done.
        const val CREATOR =
            "<div data-astryx-story='main'><img width='100' height='100'></div>" +
                "<div role='button' data-astryx-story='tool'>Add text</div>" +
                "<div role='button' data-astryx-story='tool' id='music-tool'>Add music</div>" +
                "<div id='popup'></div><div id='card'></div><audio></audio>" +
                "<script>window.__done = []; window.__replayed = [];" +
                "document.getElementById('music-tool').addEventListener('click', function () {" +
                "  var p = document.getElementById('popup'); p.setAttribute('data-astryx-story-popup', '');" +
                "  p.innerHTML = \"<input aria-label='Search music' id='box'>\" +" +
                "    \"<div role='button' id='r1' style='height:48px'><div>Sunshine on My Shoulders</div><div>John Denver</div><div role='button' aria-label='Play'>&gt;</div></div>\" +" +
                "    \"<div role='button' id='r2' style='height:48px'><div>Sunflower</div><div>Post Malone, Swae Lee</div><div role='button' aria-label='Play'>&gt;</div></div>\";" +
                "  document.getElementById('box').addEventListener('keydown', function (e) { if (e.key === 'Enter') window.__searched = e.target.value; });" +
                "  ['r1', 'r2'].forEach(function (id) { var row = document.getElementById(id);" +
                "    row.querySelector('[aria-label=Play]').addEventListener('click', function (e) { e.stopPropagation(); window.__played = row.innerText.split('\\n')[0]; });" +
                "    row.addEventListener('click', __choose); }); });" +
                "function __choose() { document.getElementById('popup').innerHTML = '';" +
                "  var c = document.getElementById('card');" +
                "  c.innerHTML = \"<div><div role='button' aria-label='Large lyrics style'></div><div role='button' aria-label='Light Lyrics style'></div>\" +" +
                "    \"<div role='button' aria-label='Dark lyrics style'></div><div role='button' aria-label='Floating lyrics style'></div>\" +" +
                "    \"<div role='button' id='card-done'>Done</div></div>\" +" +
                "    \"<div id='bar' style='position:fixed;top:600px;left:50px;display:flex'>\" +" +
                "    \"<div role='button' aria-label='Pause' id='pause'>||</div>\" +" +
                "    \"<div id='strip' style='width:200px;height:40px;overflow-x:scroll;white-space:nowrap'></div>\" +" +
                "    \"<div role='button' aria-label='Done' id='bar-done'>v</div></div>\";" +
                "  var strip = document.getElementById('strip');" +
                "  for (var i = 0; i < 100; i++) { var bar = document.createElement('span'); bar.style.cssText = 'display:inline-block;width:32px;height:30px'; strip.appendChild(bar); }" +
                "  strip.scrollLeft = 120;" +
                "  c.querySelectorAll('[aria-label\$=\"yrics style\"]').forEach(function (b) { b.addEventListener('click', function () { window.__style = b.getAttribute('aria-label'); }); });" +
                "  document.getElementById('card-done').addEventListener('click', function () { window.__done.push('card'); });" +
                "  document.getElementById('bar-done').addEventListener('click', function () { window.__done.push('bar'); });" +
                "  document.getElementById('pause').addEventListener('click', function () { if (this.getAttribute('aria-label') !== 'Pause') return;" +
                "    window.__replayed.push('pause'); var b = this;" +
                "    setTimeout(function () { b.setAttribute('aria-label', 'Play'); b.onclick = function () { window.__replayed.push('play'); window.__replayed = window.__replayed.join(); }; }, 150); }); }" +
                "</script>"
    }
}
