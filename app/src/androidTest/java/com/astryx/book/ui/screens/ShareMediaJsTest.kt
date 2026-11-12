package com.astryx.book.ui.screens
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.astryx.book.assertTrue
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// evaluateJavascript returns strings JSON-quoted.
private fun String.unquoted() = removeSurrounding("\"")

/**
 * Tests for share_media.js (photos and videos shared into Facebook's composer) against a real
 * WebView, same harness approach as ReelAdsJsTest. A fake AstryxShare answers the script the way
 * ShareMediaBridge does (a header, then each file's bytes); the fixtures mimic m.facebook.com's
 * Photo button (which sets up a hidden file input and clicks it) and the desktop story creator.
 */
@RunWith(AndroidJUnit4::class)
class ShareMediaJsTest {

    private class Harness {
        private val page = PageFixture(width = 1080, height = 2000)

        fun load(url: String, body: String) {
            page.load(url, "<html><head></head><body>$body</body></html>")
            eval(FAKE_BRIDGE)
        }

        /** Queues files for the fake app: name, type and size in bytes. */
        fun files(vararg files: Triple<String, String, Int>, base64: Boolean = false) = eval(
            "window.__base64 = $base64; window.__files = [" +
                files.joinToString(",") { (n, t, s) -> "{name:'$n',type:'$t',bytes:$s}" } + "];"
        )

        /** share_media.js for [target], after [setup] (the flags the app sets, ShareEffects). */
        fun run(target: String, setup: String = "") {
            eval("window.__astryxShareFindMs = 1500; window.__astryxShareTarget = '$target'; $setup\n${page.script(R.raw.share_media)}")
        }

        fun eval(js: String) = page.eval(js)

        fun waitFor(js: String, ms: Long = 5000) = page.waitFor(ms, everyMs = 50) { eval(js) == "true" }

        fun calls() = eval("window.__calls.join(' ')").unquoted()
    }

    @Test
    fun feed_photosGoIntoTheComposer_throughThePhotoButton_inAnyLanguage() {
        val h = Harness()
        h.load("https://m.facebook.com/", feedPage(label = "תמונה"))
        h.eval(PHOTO_ICON)
        h.files(Triple("0_a.jpg", "image/jpeg", 5), Triple("1_b.png", "image/png", 7))
        h.run("feed")

        assertTrue("the composer never got the files", h.waitFor("!!window.__got"))
        assertEquals("0_a.jpg:image/jpeg:5,1_b.png:image/png:7", h.eval("window.__got.join(',')").unquoted())
        // No picker opened: the page's click on its input was answered with the files.
        assertEquals("0", h.eval("window.__pickerOpened"))
        assertTrue(h.waitFor("window.__calls.indexOf('finished:feed') >= 0", 7000))
        assertTrue(h.calls(), h.calls().startsWith("all:feed"))
    }

    @Test
    fun feed_inTheComposerOpenedDirectly_goesThroughItsPhotosButton() {
        // The share opens m.facebook.com/composer/ (shown at once); its Photos button (the
        // add-photo icon U+F18BB) gets the files.
        val h = Harness()
        h.load("https://m.facebook.com/composer/", COMPOSER_PAGE)
        h.eval(ADD_PHOTO_ICON)
        h.files(Triple("0_a.jpg", "image/jpeg", 5))
        h.run("feed")

        assertTrue("the composer never got the files", h.waitFor("!!window.__got"))
        assertEquals("0_a.jpg:image/jpeg:5", h.eval("window.__got.join(',')").unquoted())
        assertEquals("0", h.eval("window.__pickerOpened"))
    }

    @Test
    fun feed_fromHome_hasTheAppTapItsComposerBox_thenGivesTheFilesToTheComposer() {
        // A share that comes with Home showing: the app taps the box (a real tap, which Back's
        // history needs), the composer opens in-page, and its Photos button gets the files.
        val h = Harness()
        h.load("https://m.facebook.com/", homePage())
        h.eval(PHOTO_ICON)
        h.eval(ADD_PHOTO_ICON)
        h.files(Triple("0_a.jpg", "image/jpeg", 5))
        h.run("feed", "window.__astryxShareFromHome = true;")

        assertTrue({ "the composer never got the files (${h.calls()})" }, h.waitFor("!!window.__got"))
        assertEquals("0_a.jpg:image/jpeg:5", h.eval("window.__got.join(',')").unquoted())
        assertEquals("/composer/", h.eval("location.pathname").unquoted())
        assertTrue(h.calls(), h.calls().startsWith("tap:feed all:feed"))
    }

    // A feed share run on Home whose box can't open the composer: the app's asked to load it, for
    // [reason]; the page is [page], changed by [change], and the script runs after [setup].
    private fun assertTheAppLoadsTheComposer(reason: String, page: String, change: String = "", setup: String = "") {
        val h = Harness()
        h.load("https://m.facebook.com/", page)
        h.eval(PHOTO_ICON + change)
        h.files(Triple("0_a.jpg", "image/jpeg", 5))
        h.run("feed", "window.__astryxShareFromHome = true; $setup")

        assertTrue(h.calls(), h.waitFor("window.__calls.indexOf('openComposer:feed:$reason') >= 0"))
        assertEquals("the files went somewhere", "false", h.eval("!!window.__got"))
    }

    @Test
    fun feed_fromHome_withTheBoxOffScreen_hasTheAppLoadTheComposer() =
        assertTheAppLoadsTheComposer("boxHidden", homePage(boxTop = 5000))

    @Test
    fun feed_fromHome_withASheetOverTheBox_hasTheAppLoadTheComposer() =
        assertTheAppLoadsTheComposer("boxHidden", homePage(), change = SHEET_OVER_THE_PAGE)

    @Test
    fun feed_fromHome_whenTheTapOpensNoComposer_hasTheAppLoadIt() =
        assertTheAppLoadsTheComposer(
            "notOpened", homePage(opensComposer = false), setup = "window.__astryxShareComposerMs = 600;"
        )

    @Test
    fun aShareRunsOncePerPage_andTheNextShareRunsThereToo() {
        // Opened from Home, the composer stays in Home's page, where the next share runs too.
        val h = Harness()
        h.load("https://m.facebook.com/composer/", COMPOSER_PAGE)
        h.eval(ADD_PHOTO_ICON)
        h.files(Triple("0_a.jpg", "image/jpeg", 5))
        h.run("feed", "window.__astryxShareId = 1;")
        assertTrue(h.waitFor("!!window.__got"))
        h.run("feed", "window.__astryxShareId = 1;")
        Thread.sleep(500)
        assertEquals("the same share ran twice", "all:feed", h.calls().substringBefore(" finished"))

        h.files(Triple("1_b.jpg", "image/jpeg", 7))
        h.run("feed", "window.__astryxShareId = 2;")
        assertTrue("the next share never ran", h.waitFor("window.__got.join(',') === '1_b.jpg:image/jpeg:7'"))
    }

    @Test
    fun feed_aNoteShows_whileFacebookPreparesThePhotos() {
        // Facebook takes a few seconds to resize a phone's photo and draw its preview (a data:
        // image): the app's note covers that wait, then goes once every preview is there.
        val h = Harness()
        h.load("https://m.facebook.com/", feedPage(label = "Photo"))
        h.eval(PHOTO_ICON)
        h.eval("window.__astryxShareAdding = 'Adding 2 photos…';")
        h.files(Triple("0_a.jpg", "image/jpeg", 5), Triple("1_b.png", "image/png", 7))
        h.run("feed")

        assertTrue("the composer never got the files", h.waitFor("!!window.__got"))
        assertTrue(h.waitFor("(document.getElementById('astryx-share-adding') || {}).textContent === 'Adding 2 photos…'"))
        // The first preview: still waiting for the second.
        h.eval("var p1 = document.createElement('img'); p1.src = 'data:image/gif;base64,R0lGODlhAQABAAAAACw='; document.body.appendChild(p1); 1")
        Thread.sleep(600)
        assertEquals("true", h.eval("!!document.getElementById('astryx-share-adding')"))
        h.eval("var p2 = document.createElement('img'); p2.src = 'data:image/gif;base64,R0lGODlhAQABAAAAACw='; document.body.appendChild(p2); 1")
        assertTrue(h.waitFor("!document.getElementById('astryx-share-adding')"))
    }

    @Test
    fun feed_photoButtonWithoutItsIcon_isFoundByItsEnglishLabel() {
        val h = Harness()
        h.load("https://m.facebook.com/", feedPage(label = "Photo"))
        h.files(Triple("0_a.jpg", "image/jpeg", 3))
        h.run("feed")

        assertTrue(h.waitFor("!!window.__got"))
    }

    @Test
    fun feed_withoutAComposer_saysSo() {
        // Logged out (the login page), or Facebook changed its Home.
        val h = Harness()
        h.load("https://m.facebook.com/", "<form id='login'></form>")
        h.files(Triple("0_a.jpg", "image/jpeg", 3))
        h.run("feed")

        assertTrue(h.waitFor("window.__calls.indexOf('failed:feed:noComposer') >= 0"))
    }

    @Test
    fun story_takesTheFilesOneByOne_eachWhenTheCreatorIsReadyAgain() {
        val h = Harness()
        h.load("https://www.facebook.com/stories/create", STORY_PAGE)
        h.files(Triple("0_a.jpg", "image/jpeg", 4), Triple("1_v.mp4", "video/mp4", 9))
        h.run("story")

        assertTrue("the first file never arrived", h.waitFor("window.__got.length === 1"))
        assertEquals("0_a.jpg:image/jpeg:4", h.eval("window.__got[0]").unquoted())
        // The story is shared (or discarded): the creator shows a new input, and the next file comes.
        h.eval("window.__resetCreator()")
        assertTrue("the second file never arrived", h.waitFor("window.__got.length === 2"))
        assertEquals("1_v.mp4:video/mp4:9", h.eval("window.__got[1]").unquoted())
        assertTrue(h.waitFor("window.__calls.indexOf('finished:story') >= 0"))
    }

    // The creator opened again for the share's next file (after a story posted) is waited for as long
    // as it takes (window.__astryxShareNextStory, ShareEffects): over a slow link it came after the
    // first file's limit, and the files left were dropped. A first file still has its limit.
    @Test
    fun story_aCreatorOpenedForTheNextFile_isWaitedFor_pastTheFirstFilesLimit() {
        // The creator still loading (no file input yet), share_media.js run with [setup].
        fun loading(setup: String = "") = Harness().apply {
            load("https://www.facebook.com/stories/create", STORY_PAGE)
            eval("document.getElementById('creator').innerHTML = ''")
            files(Triple("1_b.jpg", "image/jpeg", 6))
            run("story", setup)
        }
        val next = loading("window.__astryxShareNextStory = true;")
        Thread.sleep(2500)
        assertEquals("the share gave up on the creator", "", next.calls())
        next.eval("window.__resetCreator()")
        assertTrue("the next file never arrived", next.waitFor("window.__got.length === 1"))

        assertTrue(loading().waitFor("window.__calls.indexOf('failed:story:noComposer') >= 0"))
    }

    @Test
    fun story_closesAfterTheLastItem_onceTheCreatorIsChoosingAgain() {
        val h = Harness()
        h.load("https://www.facebook.com/stories/create", STORY_PAGE)
        h.eval("window.__closed = 0; window.__astryxShareClose = function () { window.__closed++; };")
        h.files(Triple("0_a.jpg", "image/jpeg", 4))
        h.run("story")

        assertTrue(h.waitFor("window.__got.length === 1"))
        // Still editing it: the screen stays.
        Thread.sleep(2500)
        assertEquals("0", h.eval("window.__closed"))
        // Discarded: the creator is choosing a story again, and the share is over.
        h.eval("window.__resetCreator()")
        assertTrue(h.waitFor("window.__closed === 1", 5000))
    }

    @Test
    fun profilePicture_goesToUploadPhoto_throughTheProfilePictureMenu() {
        val h = Harness()
        h.load("https://m.facebook.com/profile.php", PROFILE_PAGE)
        h.eval(UPLOAD_ICON)
        h.files(Triple("0_me.jpg", "image/jpeg", 5), Triple("1_other.jpg", "image/jpeg", 6))
        h.run("profile")

        assertTrue("the upload never got the photo", h.waitFor("!!window.__got"))
        assertEquals("0_me.jpg:image/jpeg:5", h.eval("window.__got.join(',')").unquoted())
        assertEquals("0", h.eval("window.__pickerOpened"))
    }

    @Test
    fun group_photosGoIntoTheComposer_onceAGroupIsOpen() {
        val h = Harness()
        h.load("https://m.facebook.com/groups/123456/", feedPage(label = "Photo"))
        h.files(Triple("0_a.jpg", "image/jpeg", 3), Triple("1_b.jpg", "image/jpeg", 4))
        h.run("group")

        assertTrue(h.waitFor("!!window.__got"))
        assertEquals("0_a.jpg:image/jpeg:3,1_b.jpg:image/jpeg:4", h.eval("window.__got.join(',')").unquoted())
    }

    @Test
    fun group_photoButtonWithoutAButtonRole_isFoundToo() {
        // A group's page renders its composer's Photo button as a bare action container
        // (data-action-id, no role), unlike Home.
        val h = Harness()
        h.load("https://m.facebook.com/groups/355723031242469/", feedPage(label = "Photo").replace("role='button' ", "data-action-id='32671' "))
        h.eval(PHOTO_ICON)
        h.files(Triple("0_a.jpg", "image/jpeg", 3))
        h.run("group")

        assertTrue(h.waitFor("!!window.__got"))
        assertEquals("0_a.jpg:image/jpeg:3", h.eval("window.__got.join(',')").unquoted())
    }

    @Test
    fun group_severalPhotos_singleFileInput_restAddedOneByOneThroughTheComposer() {
        // A group's Photo button opens a single-file input; the composer that then shows takes
        // one more photo per click on its "Add or remove photos" button.
        val h = Harness()
        h.load("https://m.facebook.com/groups/355723031242469/", GROUP_COMPOSER)
        h.files(Triple("0_a.jpg", "image/jpeg", 3), Triple("1_b.jpg", "image/jpeg", 4), Triple("2_c.jpg", "image/jpeg", 5))
        h.run("group")

        assertTrue(h.eval("JSON.stringify(window.__got)"), h.waitFor("window.__got.length === 3", 15000))
        assertEquals("0_a.jpg,1_b.jpg,2_c.jpg", h.eval("window.__got.join(',')").unquoted())
        assertTrue(h.waitFor("window.__calls.indexOf('finished:group') >= 0", 5000))
    }

    @Test
    fun group_notOnTheGroupsListOrHome() {
        // The list where the user picks the group (and Home, if they go back there) has a Photo
        // button too: the photos wait for a group.
        val h = Harness()
        h.load("https://m.facebook.com/groups/feed/", feedPage(label = "Photo"))
        h.files(Triple("0_a.jpg", "image/jpeg", 3))
        h.run("group")

        assertFalse(h.waitFor("!!window.__got", 2500))
        assertEquals("", h.calls())
    }

    @Test
    fun reel_videoGoesToTheCreatorInputThatTakesIt() {
        val h = Harness()
        h.load("https://www.facebook.com/reel/", REEL_PAGE)
        h.files(Triple("0_v.mp4", "video/mp4", 9))
        h.run("reel")

        assertTrue("the creator never got the video", h.waitFor("!!window.__got", 8000))
        assertEquals("0_v.mp4:video/mp4:9", h.eval("window.__got").unquoted())
        assertTrue(h.waitFor("window.__calls.indexOf('finished:reel') >= 0", 5000))
    }

    @Test
    fun aFileInManyPieces_arrivesWhole_andOneTheAppCouldntRead_isLeftOut() {
        // A file goes in pieces (a 250 MB video in one message couldn't fit in the app's memory).
        val h = Harness()
        h.load("https://m.facebook.com/composer/", COMPOSER_PAGE)
        h.eval(ADD_PHOTO_ICON)
        h.eval("window.__chunk = 3; 1")
        h.files(Triple("0_a.jpg", "image/jpeg", 10), Triple("1_b.jpg", "image/jpeg", 7), Triple("2_c.jpg", "image/jpeg", 11))
        h.eval("window.__files[1].failed = true; 1")
        h.run("feed")

        assertTrue("the composer never got the files", h.waitFor("!!window.__got"))
        assertEquals("0_a.jpg:image/jpeg:10,2_c.jpg:image/jpeg:11", h.eval("window.__got.join(',')").unquoted())
    }

    @Test
    fun filesSentAsBase64_onOlderWebViews_arriveWhole() {
        val h = Harness()
        h.load("https://www.facebook.com/stories/create", STORY_PAGE)
        h.files(Triple("0_a.jpg", "image/jpeg", 6), base64 = true)
        h.run("story")

        assertTrue(h.waitFor("window.__got.length === 1"))
        assertEquals("0_a.jpg:image/jpeg:6", h.eval("window.__got[0]").unquoted())
    }

    internal companion object {
        // Answers share_media.js like ShareMediaBridge: a header, then each file in pieces of
        // window.__chunk bytes as ArrayBuffers (or base64) and its end, from window.__files; records
        // the calls as "call:target[:reason]". A file with failed: true ends as one the app couldn't read.
        const val FAKE_BRIDGE = """
            window.__calls = []; window.__files = []; window.__base64 = false; window.__chunk = 4;
            function __payload(bytes) {
              if (!window.__base64) return bytes.buffer;
              var s = ''; for (var i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
              return btoa(s);
            }
            function __pieces(f) {
              var bytes = new Uint8Array(f.bytes), out = [];
              for (var at = 0; at < bytes.length; at += window.__chunk) out.push(__payload(bytes.slice(at, at + window.__chunk)));
              out.push(f.failed ? '{"end":true,"failed":true}' : '{"end":true}');
              return out;
            }
            window.AstryxShare = { postMessage: function (text) {
              var m = JSON.parse(text), self = this;
              window.__calls.push(m.call + ':' + m.target + (m.reason ? ':' + m.reason : ''));
              // The app's tap lands on what's at that point of the screen (no zoom here).
              if (m.call === 'tap') { var hit = document.elementFromPoint(m.x, m.y); if (hit) hit.click(); }
              setTimeout(function () {
                if (m.call !== 'all' && m.call !== 'next') return;
                var files = m.call === 'all' ? window.__files.splice(0) : window.__files.splice(0, 1);
                if (!files.length) { self.onmessage({ data: '{"done":true}' }); return; }
                self.onmessage({ data: JSON.stringify({ count: files.length, remaining: window.__files.length,
                  base64: window.__base64, files: files.map(function (f) { return { name: f.name, type: f.type }; }) }) });
                files.forEach(function (f) { __pieces(f).forEach(function (p) { self.onmessage({ data: p }); }); });
              }, 10);
            } };
        """

        // m.facebook.com's Photo button: sets up the hidden input and clicks it (which would open
        // the picker); the composer reads the input's files on change.
        fun feedPage(label: String) =
            "<div role='button' id='photo' aria-label='$label'><span id='icon'></span><span>$label</span></div>" +
                "<input type='file' id='in' style='display:none'>" +
                "<script>window.__pickerOpened = 0;" +
                "document.getElementById('photo').addEventListener('click', function () {" +
                "  var i = document.getElementById('in'); i.accept = 'image/*'; i.multiple = true; i.click(); });" +
                "document.getElementById('in').addEventListener('change', function (e) {" +
                "  window.__got = Array.prototype.map.call(e.target.files, function (f) { return f.name + ':' + f.type + ':' + f.size; }); });" +
                "</script>"

        // The composer page (m.facebook.com/composer/): its Photos row, icon put in by script.
        const val COMPOSER_PAGE =
            "<div role='button' id='photos' aria-label='Photos'><span id='cicon'></span><span>Photos</span></div>" +
                "<input type='file' id='in' style='display:none'>" +
                "<script>window.__pickerOpened = 0;" +
                "document.getElementById('photos').addEventListener('click', function () {" +
                "  var i = document.getElementById('in'); i.accept = 'image/*'; i.multiple = true; i.click(); });" +
                "document.getElementById('in').addEventListener('change', function (e) {" +
                "  window.__got = Array.prototype.map.call(e.target.files, function (f) { return f.name + ':' + f.type + ':' + f.size; }); });" +
                "</script>"

        // Home's composer row: the profile picture, the "What's on your mind?" box (Hebrew here: found
        // by being the row's widest button; Facebook draws its text over it, beside it in the row, and
        // the box's field takes the tap) and the Photo button. The field opens the composer in-page
        // (a moment later, at /composer/, as Facebook's server draws it), whose Photos button (the
        // add-photo icon) takes the files. [boxTop]: the row's distance from the page's top.
        fun homePage(opensComposer: Boolean = true, boxTop: Int = 0) =
            "<div id='home' style='display:flex;margin-top:${boxTop}px'>" +
                "<div role='button' aria-label='Go to profile' style='width:40px;height:40px'></div>" +
                "<div id='field' style='position:relative'>" +
                "<div role='button' aria-label='על מה אתה חושב?' style='width:240px;height:36px'></div>" +
                "<div style='position:absolute;left:16px;top:0;height:36px'>על מה אתה חושב?</div></div>" +
                "<div role='button' id='photo' aria-label='תמונה' style='width:48px;height:40px'><span id='icon'></span></div></div>" +
                "<div id='composer' style='display:none'>$COMPOSER_PAGE</div>" +
                "<script>document.getElementById('field').addEventListener('click', function () {" +
                (if (opensComposer) "  setTimeout(function () { history.pushState({}, '', '/composer/');" +
                    "    document.getElementById('home').style.display = 'none';" +
                    "    document.getElementById('composer').style.display = 'block'; }, 200);" else "") +
                "});</script>"

        // A sheet over the whole page (Facebook's "better on the app" one), the box under it.
        const val SHEET_OVER_THE_PAGE = "var sheet = document.createElement('div');" +
            " sheet.style.cssText = 'position:fixed;left:0;top:0;right:0;bottom:0;background:#fff';" +
            " document.body.appendChild(sheet);"

        // The composer's Photos button's icon (U+F18BB), put in by script.
        const val ADD_PHOTO_ICON = "document.getElementById('cicon').textContent = String.fromCharCode(0xDB86, 0xDCBB);"

        // The Photo button's icon glyph (U+F323C), put in by script: no private-use character here.
        const val PHOTO_ICON = "document.getElementById('icon').textContent = String.fromCharCode(0xDB8C, 0xDE3C);"

        // The mobile profile: the profile picture (a large square button) opens a menu whose Upload
        // photo sets up a hidden input and clicks it.
        const val PROFILE_PAGE =
            "<div role='button' id='picture' style='width:155px;height:155px'><img width='155' height='155'></div>" +
                "<div id='menu'></div><input type='file' id='in' style='display:none'>" +
                "<script>window.__pickerOpened = 0;" +
                "document.getElementById('picture').addEventListener('click', function () {" +
                "  document.getElementById('menu').innerHTML = \"<div role='button' id='upload'><span id='uicon'></span><span>Upload photo</span></div>\";" +
                "  document.getElementById('uicon').textContent = window.__uploadIcon;" +
                "  document.getElementById('upload').addEventListener('click', function () {" +
                "    var i = document.getElementById('in'); i.accept = 'image/*'; i.click(); }); });" +
                "document.getElementById('in').addEventListener('change', function (e) {" +
                "  window.__got = Array.prototype.map.call(e.target.files, function (f) { return f.name + ':' + f.type + ':' + f.size; }); });" +
                "</script>"

        // A group's composer: the Photo button (no button role) clicks a single-file input; the
        // composer then shows an "Add or remove photos" button that clicks it again. Each change
        // adds the one file given.
        const val GROUP_COMPOSER =
            "<div data-action-id='1' id='photo' aria-label='Photo'><span>Photo</span></div>" +
                "<div id='composer'></div><input type='file' id='in' style='display:none'>" +
                "<script>window.__got = [];" +
                "document.getElementById('photo').addEventListener('click', function () {" +
                "  var i = document.getElementById('in'); i.accept = 'image/*,video/*'; i.click(); });" +
                "document.getElementById('in').addEventListener('change', function (e) {" +
                "  window.__got.push(e.target.files[0].name);" +
                "  document.getElementById('composer').innerHTML = \"<div role='button' id='add'><span>Add or remove photos</span></div>\";" +
                "  document.getElementById('add').addEventListener('click', function () { document.getElementById('in').click(); }); });" +
                "</script>"

        // The profile menu's Upload photo icon (U+F16E6), put in by script.
        const val UPLOAD_ICON = "window.__uploadIcon = String.fromCharCode(0xDB85, 0xDEE6);"

        // The desktop reels page: Create reel (a clapperboard icon) opens the creator, which has two
        // video inputs; only the second is wired to it, and it clears the input it reads.
        const val REEL_PAGE =
            "<div role='button' id='create'><svg viewBox='0 0 16 16'><path d='M5.79 1.016 6.724 4h3.179z'></path></svg></div>" +
                "<div id='dialog'></div>" +
                "<script>document.getElementById('create').addEventListener('click', function () {" +
                "  var d = document.getElementById('dialog');" +
                "  d.innerHTML = \"<input type='file' accept='video/*'><input type='file' accept='video/*'>\";" +
                "  d.lastChild.addEventListener('change', function (e) {" +
                "    window.__got = e.target.files[0].name + ':' + e.target.files[0].type + ':' + e.target.files[0].size;" +
                "    e.target.value = ''; }); });" +
                "</script>"

        // The desktop story creator: an input ready on load; after a story, a new one replaces it.
        const val STORY_PAGE =
            "<div id='creator'><input type='file' id='story-in' accept='image/*,video/*'></div>" +
                "<script>window.__got = [];" +
                "function __listen(i) { i.addEventListener('change', function (e) {" +
                "  window.__got.push(e.target.files[0].name + ':' + e.target.files[0].type + ':' + e.target.files[0].size); }); }" +
                "__listen(document.getElementById('story-in'));" +
                "window.__resetCreator = function () { var c = document.getElementById('creator');" +
                "  c.innerHTML = \"<input type='file' accept='image/*,video/*'>\"; __listen(c.firstChild); };" +
                "</script>"
    }
}
