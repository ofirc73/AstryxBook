package com.astryx.book.ui.screens
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.astryx.book.PageFixture
import com.eepiemi.materialbook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// evaluateJavascript returns strings JSON-quoted.
private fun String.unquoted() = removeSurrounding("\"")

/**
 * Tests for story_layout.js (the desktop story creator laid out like the Facebook app's story
 * editor) against a real WebView. The fixture is built like Facebook's creator: a sidebar
 * (role=navigation; role=region since 2026-10-30) with the privacy button, the tools, the AI label row and Discard / Share,
 * beside the preview (role=main) whose story frame Facebook sizes from the window's height.
 */
@RunWith(AndroidJUnit4::class)
class StoryLayoutJsTest {

    private class Harness {
        private val page = PageFixture(width = 1080, height = 2000)

        fun load(body: String) {
            page.load(
                "https://www.facebook.com/stories/create",
                "<html><head><meta name='viewport' content='width=device-width'></head>" +
                    "<body style='margin:0'>$body</body></html>"
            )
        }

        fun run() {
            page.run(R.raw.story_layout)
        }

        fun eval(js: String) = page.eval(js)

        fun waitFor(js: String, ms: Long = 6000) = page.waitFor(ms) { eval(js) == "true" }

        fun tag(selector: String) = eval("(document.querySelector('$selector') || {}).getAttribute ? " +
            "document.querySelector('$selector').getAttribute('data-astryx-story') : null").unquoted()

        fun hidden(selector: String) = eval("!!document.querySelector('$selector').closest('[data-astryx-story-hide]')")
    }

    @Test
    fun theStory_isTheLargest9to16FrameThatFits_andThePageGetsItsHeightBackAfterwards() {
        val h = Harness()
        h.load(CREATOR)
        h.run()
        h.eval("window.__showFrame()")

        // As wide as the column (less the frame's inset on each side), or as tall as the screen
        // above the bottom bar allows, whichever is smaller.
        val expected = "(function () { var box = document.getElementById('box');" +
            " var byWidth = box.clientWidth - 32;" +
            " var byHeight = (window.__realHeight - box.getBoundingClientRect().top - 16 - 68 - 64) * 9 / 16;" +
            " return Math.floor(Math.min(byWidth, byHeight)); })()"
        assertTrue(h.eval("JSON.stringify(window.__frameSize()) + ' wanted ' + $expected"), h.waitFor(
            "(function (s, w) { return Math.abs(s.width - w) <= 1 && Math.abs(s.boxHeight - (w * 16 / 9 + 68)) <= 2; })" +
                "(window.__frameSize(), $expected)"
        ))
        // The story is shared or discarded: the page gets its real height back.
        h.eval("window.__hideFrame()")
        assertTrue(h.waitFor("window.innerHeight === window.__realHeight"))
    }

    @Test
    fun aStoryInTheEditor_isLaidOutAtPhoneWidth_choosingOneAtTheDesktopWidthFitted() {
        val h = Harness()
        h.load(CREATOR)
        h.run()
        val viewport = "document.querySelector('meta[name=viewport]').content"
        assertTrue(h.eval(viewport), h.waitFor("/^width=600, initial-scale=0\\.\\d+/.test($viewport)"))
        h.eval("window.__showFrame()")
        assertTrue(h.eval(viewport), h.waitFor("$viewport.indexOf('width=device-width') === 0"))
    }

    @Test
    fun theTools_goOnTheStory_andPrivacyDiscardShare_inTheBottomBar() = assertLaidOut(CREATOR)

    // Since 2026-10-30 the sidebar is a region ("Stories Creation Form"), no longer a navigation.
    @Test
    fun aSidebarThatIsARegion_isLaidOutTheSameWay() = assertLaidOut(
        CREATOR.replace("role='navigation' id='nav'", "role='region' aria-label='Stories Creation Form' id='nav'")
    )

    private fun assertLaidOut(creator: String) {
        val h = Harness()
        h.load(creator)
        h.run()
        h.eval("window.__showFrame()")

        assertTrue(h.waitFor("document.querySelectorAll('[data-astryx-story=tool]').length === 3"))
        assertEquals("tools", h.tag("#tools"))
        assertEquals("actions", h.tag("#actions"))
        assertEquals("privacy", h.tag("#privacy"))
        assertEquals("main", h.tag("#main"))
        // The rest of the sidebar, and the narrow layout's own row under the story, are hidden.
        assertEquals("true", h.hidden("#title"))
        assertEquals("true", h.hidden("#ai"))
        assertEquals("true", h.hidden("#narrow-discard"))
        assertEquals("true", h.hidden("#narrow-privacy"))
        assertEquals("false", h.hidden("#frame"))
        // Other text buttons outside the sidebar (a panel's "See all") stay.
        assertEquals("false", h.hidden("#see-all"))
        // The tools sit on the story, above its bottom edge.
        assertTrue(h.waitFor(
            "document.getElementById('tools').getBoundingClientRect().bottom <= document.getElementById('frame').getBoundingClientRect().bottom"
        ))
        assertTrue(h.waitFor(
            "document.getElementById('tools').getBoundingClientRect().left - document.getElementById('frame').getBoundingClientRect().left >= 3"
        ))
        assertTrue(h.waitFor(
            "document.getElementById('frame').getBoundingClientRect().right - document.getElementById('tools').getBoundingClientRect().right >= 13"
        ))
        // Choosing a story again: Facebook's own page.
        h.eval("window.__hideFrame()")
        assertTrue(h.waitFor("!document.querySelector('[data-astryx-story]')"))
    }

    @Test
    fun theAiLabelRedrawnWithoutItsSwitch_isNeverATool() {
        // While posting, Facebook redraws the AI label row as an icon with its short label (no
        // switch, no explanation); taken for a tool, it was squeezed into a slot beside Share,
        // its letters in a column. The sidebar's switch row named it first.
        val h = Harness()
        h.load(CREATOR)
        h.run()
        h.eval("window.__showFrame()")
        assertTrue(h.waitFor("document.querySelectorAll('[data-astryx-story=\"tool\"]').length > 0"))
        h.eval(
            "(function () { var row = document.createElement('div'); row.id = 'ai-row';" +
                " row.innerHTML = \"<div role='button' id='ai-tool'><svg width='20' height='20'></svg><span>Add AI label</span></div>\";" +
                " document.getElementById('tools').appendChild(row); })()"
        )
        assertTrue(h.waitFor("document.getElementById('ai-tool').hasAttribute('data-astryx-story-hide')"))
        assertEquals("true", h.hidden("#ai-tool"))
        assertEquals("null", h.eval("document.getElementById('ai-tool').getAttribute('data-astryx-story')"))
    }

    @Test
    fun aSwitchRowInAnyLanguage_isHidden() {
        // Recognized by its switch, not its words (Hebrew here).
        val h = Harness()
        h.load(CREATOR)
        h.eval(
            "(function () { var row = document.createElement('div'); row.setAttribute('role', 'button'); row.id = 'he-ai';" +
                " row.innerHTML = \"<svg width='20' height='20'></svg><span>הוספת תווית AI</span><div role='switch' aria-checked='false'></div>\";" +
                " document.getElementById('tools').appendChild(row); })()"
        )
        h.run()
        h.eval("window.__showFrame()")
        assertTrue(h.waitFor("document.getElementById('he-ai').hasAttribute('data-astryx-story-hide')"))
        assertEquals("true", h.hidden("#he-ai"))
    }

    @Test
    fun aDisclosureBesideTheTools_isHidden_andSoIsTheAiLabel() {
        // The AI label is the sidebar's switch row (CREATOR's #ai), never a tool: shown as an
        // extra tool, it was squeezed into a slot with its letters in a column.
        val h = Harness()
        h.load(CREATOR)
        h.eval(
            "(function () { var row = document.createElement('div'); row.id = 'ai-row';" +
                " row.innerHTML = \"<div role='button' id='ai-tool'><svg width='20' height='20'></svg><span>Add AI label</span></div>\" +" +
                " \"<div id='ai-disclosure'>We require you to label certain realistic content that’s made with AI. Learn more</div>\";" +
                " document.getElementById('tools').appendChild(row); })()"
        )
        h.run()
        h.eval("window.__showFrame()")

        assertTrue(h.waitFor("document.getElementById('ai-tool').hasAttribute('data-astryx-story-hide')"))
        assertEquals("true", h.hidden("#ai-tool"))
        assertTrue(h.waitFor("!!document.getElementById('ai-disclosure').closest('[data-astryx-story-hide]')"))
        assertEquals("true", h.hidden("#ai-disclosure"))
    }

    @Test
    fun aDisclosureMovedOutsideTheSidebar_isHiddenWhilePosting() {
        val h = Harness()
        h.load(CREATOR)
        h.run()
        h.eval("window.__showFrame()")
        h.eval(
            "(function () { var overlay = document.createElement('div'); overlay.id = 'posting-overlay';" +
                " overlay.innerHTML = \"<div id='posting'>Posting</div>\" +" +
                " \"<div id='ai-row'><div role='button' id='ai-tool'><span>Add AI label</span></div>\" +" +
                " \"<div id='ai-disclosure'>We require you to label certain realistic content that’s made with AI. Learn more</div></div>\";" +
                " document.getElementById('main').appendChild(overlay); })()"
        )

        assertTrue(h.waitFor("!!document.getElementById('ai-tool').hasAttribute('data-astryx-story-hide')"))
        assertEquals("true", h.hidden("#ai-disclosure"))
        assertEquals("true", h.hidden("#ai-tool"))
        assertEquals("false", h.hidden("#posting"))
    }

    @Test
    fun facebooksOpenPopups_areCentred_closedOnesLeftAlone() {
        val h = Harness()
        h.load(CREATOR + POPUPS)
        h.run()
        h.eval("window.__showFrame()")
        assertTrue(h.waitFor("document.getElementById('open-popup').hasAttribute('data-astryx-story-popup')"))
        assertEquals("false", h.eval("document.getElementById('closed-popup').hasAttribute('data-astryx-story-popup')"))
        // A closed popup's host lets taps through to the story under it.
        assertTrue(h.waitFor("getComputedStyle(document.getElementById('closed-popup')).pointerEvents === 'none'"))
        assertEquals("\"auto\"", h.eval("getComputedStyle(document.getElementById('open-popup')).pointerEvents"))
    }

    // Facebook's popups aren't modal: Share posted the story with the music list still open (Galaxy
    // A56, 2026-10-27). While one is open, Discard and Share are dimmed and a tap only closes it.
    @Test
    fun withTheMusicListOpen_shareFirstClosesIt_thenShares() {
        val h = Harness()
        h.load(CREATOR + MUSIC_LIST)
        h.run()
        h.eval("window.__showFrame(); window.__shared = 0;" +
            " document.querySelectorAll('#actions [role=button]')[1].addEventListener('click', function () { window.__shared++; }); 1")
        assertTrue("the music list was never seen as open", h.waitFor(POPUP_OPEN_AND_LAID_OUT))
        assertEquals("\"0.5\"", h.eval("getComputedStyle(document.querySelectorAll('#actions [role=button]')[1]).opacity"))
        h.eval("document.querySelectorAll('#actions [role=button]')[1].click(); 1")
        assertEquals("Share went through with the music list open", "0", h.eval("window.__shared"))
        assertEquals("the music list's close button wasn't pressed", "true", h.eval("!!window.__musicClosed"))
        assertTrue(h.waitFor("!document.documentElement.classList.contains('astryx-story-popup-open')"))
        h.eval("document.querySelectorAll('#actions [role=button]')[1].click(); 1")
        assertEquals("Share didn't go through once the list was closed", "1", h.eval("window.__shared"))
    }

    // A popup it can't close (no close button found) never keeps Share from posting: the second tap goes.
    @Test
    fun aPopupThatCantBeClosed_letsTheSecondTapThrough() {
        val h = Harness()
        h.load(CREATOR + POPUPS)
        h.run()
        h.eval("window.__showFrame(); window.__shared = 0;" +
            " document.querySelectorAll('#actions [role=button]')[1].addEventListener('click', function () { window.__shared++; }); 1")
        assertTrue(h.waitFor(POPUP_OPEN_AND_LAID_OUT))
        h.eval("document.querySelectorAll('#actions [role=button]')[1].click(); 1")
        assertEquals("0", h.eval("window.__shared"))
        h.eval("document.querySelectorAll('#actions [role=button]')[1].click(); 1")
        assertEquals("the second tap on Share was held back too", "1", h.eval("window.__shared"))
    }

    @Test
    fun fiveTools_fitTheStorysWidth_eachWithOneIcon() {
        // An account with more tools (Apps, with a submenu arrow, and Add time) than the sidebar's
        // three: the row stays within the story.
        val h = Harness()
        h.load(CREATOR)
        h.eval(
            "(function () { var tools = document.querySelectorAll('#tools .t');" +
                " var apps = tools[0].cloneNode(true); apps.id = 'apps'; apps.querySelector('span').textContent = 'Apps';" +
                " apps.appendChild(apps.querySelector('svg').cloneNode(true)); apps.lastChild.id = 'arrow';" +
                " tools[0].parentElement.insertBefore(apps, tools[0]);" +
                " var time = tools[1].cloneNode(true); time.querySelector('span').textContent = 'Add time';" +
                " tools[2].parentElement.insertBefore(time, tools[2]); })()"
        )
        h.run()
        h.eval("window.__showFrame()")
        assertTrue(h.waitFor("document.querySelectorAll('[data-astryx-story=tool]').length === 5"))
        val inside = "(function () { var f = document.getElementById('frame').getBoundingClientRect();" +
            " var tools = document.querySelectorAll('[data-astryx-story=tool]'); var last = -1;" +
            " for (var i = 0; i < tools.length; i++) { var r = tools[i].getBoundingClientRect();" +
            "   if (r.width < 30 || r.left < f.left - 1 || r.right > f.right + 1 || r.left < last - 1) return false; last = r.right; }" +
            " return true; })()"
        assertTrue(h.eval("JSON.stringify([].map.call(document.querySelectorAll('[data-astryx-story=tool]'), function (t) {" +
            " var r = t.getBoundingClientRect(); return [r.left, r.right]; }))"), h.waitFor(inside))
        assertEquals("true", h.hidden("#arrow"))
    }

    @Test
    fun aToolOutsideTheToolsList_isLaidOutAtTheRowsStart() {
        // On the phone, Apps (taller: a submenu arrow under its label) sits in another branch of
        // the sidebar than the other tools.
        val h = Harness()
        h.load(CREATOR)
        h.eval(
            "(function () { var apps = document.querySelector('#tools .t').cloneNode(true); apps.id = 'apps';" +
                " apps.querySelector('span').textContent = 'Apps'; apps.style.height = '110px';" +
                " var branch = document.createElement('div'); branch.id = 'apps-branch'; branch.appendChild(apps);" +
                " document.getElementById('tools').parentElement.insertBefore(branch, document.getElementById('tools')); })()"
        )
        h.run()
        h.eval("window.__showFrame()")
        assertTrue(h.waitFor("document.getElementById('apps').getAttribute('data-astryx-story') === 'tool-extra'"))
        assertEquals("false", h.hidden("#apps"))
        val first = "(function () { var apps = document.getElementById('apps').getBoundingClientRect();" +
            " var tools = document.querySelectorAll('[data-astryx-story=tool]');" +
            " var f = document.getElementById('frame').getBoundingClientRect();" +
            " return apps.width > 20 && apps.left >= f.left - 1 && apps.right <= tools[0].getBoundingClientRect().left + 1; })()"
        assertTrue(h.waitFor(first))
    }

    @Test
    fun aStickersRemoveButton_isMadeVisible() {
        val h = Harness()
        h.load(CREATOR)
        h.run()
        h.eval("window.__showFrame()")
        h.eval(
            "(function () { var layer = document.createElement('div'); layer.style.position = 'absolute';" +
                " layer.innerHTML = \"<div role='button' id='remove' aria-label='Remove time sticker' style='width:24px;height:24px'>" +
                "<svg width='12' height='12'></svg></div><div role='button' id='sticker' aria-label='Time sticker'" +
                " style='width:120px;height:60px'>3:51 PM</div>\";" +
                " document.getElementById('column').appendChild(layer); })()"
        )
        assertTrue(h.waitFor("document.getElementById('remove').hasAttribute('data-astryx-story-x')"))
        assertEquals("false", h.eval("document.getElementById('sticker').hasAttribute('data-astryx-story-x')"))
        assertEquals("\"rgba(0, 0, 0, 0.7)\"", h.eval("getComputedStyle(document.getElementById('remove')).backgroundColor"))
    }

    @Test
    fun musicStickerIsHiddenByDefault_withoutRemovingItsTrackOrAddingAControl() {
        val h = Harness()
        h.load(CREATOR + STICKER_PANEL.replace("Hotel California", "Autumn Instrumental The Lyrical Lanterns"))
        h.run()
        h.eval("window.__showFrame()")
        h.eval(
            "(function () { var sticker = document.createElement('div'); sticker.id = 'music-sticker';" +
                " sticker.setAttribute('role', 'button'); sticker.innerHTML = \"<img><span>Autumn Instrumental</span><span> The Lyrical Lanterns</span>\";" +
                " sticker.style.cssText = 'position:absolute;left:20px;top:40px;width:150px;height:100px';" +
                // Facebook puts the music sticker beside the photo frame in the live editor.
                " document.getElementById('box').appendChild(sticker); })()"
        )

        assertTrue(h.waitFor("document.getElementById('music-sticker').hasAttribute('data-astryx-story-music-hidden')"))
        assertEquals(
            "hidden",
            h.eval("getComputedStyle(document.getElementById('music-sticker')).visibility").unquoted()
        )
        assertTrue(h.eval("document.getElementById('music-sticker').textContent.indexOf('Autumn Instrumental') >= 0") == "true")
        assertTrue(h.eval("document.getElementById('music-sticker').textContent.indexOf('The Lyrical Lanterns') >= 0") == "true")
        assertEquals("false", h.eval("!!document.querySelector('[data-astryx-story-music-toggle]')"))
        assertTrue(h.eval("document.getElementById('sticker-layer').textContent.indexOf('Autumn Instrumental') >= 0") == "true")
    }

    @Test
    fun aToolsEditor_opensAsAPanel_whoseDoneClosesItThroughTheTool() {
        // Alternative text opens its editor (choices and a text box) under its tool in the sidebar.
        val h = Harness()
        h.load(CREATOR)
        h.run()
        h.eval("window.__showFrame()")
        assertTrue(h.waitFor("!!document.querySelector('[data-astryx-story=tools]')"))
        h.eval(
            "(function () { var tools = document.querySelectorAll('#tools .t'); var alt = tools[2];" +
                " window.__altClicks = 0; alt.addEventListener('click', function () { window.__altClicks++; });" +
                " var editor = document.createElement('div'); editor.id = 'editor';" +
                " editor.innerHTML = \"<input type='radio' name='a'><input type='radio' name='b'><textarea></textarea>\";" +
                " alt.parentElement.insertBefore(editor, alt.nextSibling); })()"
        )
        assertTrue(h.waitFor("document.getElementById('editor').getAttribute('data-astryx-story') === 'sheet'"))
        assertEquals("false", h.hidden("#editor"))
        assertTrue(h.waitFor("!!document.querySelector('#editor [data-astryx-story-done]')"))
        h.eval("document.querySelector('#editor [data-astryx-story-done]').click()")
        assertEquals("1", h.eval("window.__altClicks"))
        // The AI label row (one checkbox) stays hidden.
        assertEquals("true", h.hidden("#ai"))
    }

    @Test
    fun aPanelsIconButtons_areLabelled_andThePanelGetsADoneThatTapsTheStory() {
        val h = Harness()
        h.load(CREATOR + STICKER_PANEL)
        h.eval("window.__astryxStoryDone = 'Fertig';")
        h.run()
        h.eval("window.__showFrame()")

        assertTrue(h.waitFor("document.querySelectorAll('#styles [data-astryx-story-caption]').length === 4"))
        assertTrue(h.waitFor(
            "getComputedStyle(document.querySelector('#styles [role=button]'), '::after').content.indexOf('Large lyrics style') >= 0"
        ))
        // The clip bar has no text: labelled, but Facebook's own Done is enough.
        assertTrue(h.waitFor("document.querySelectorAll('#clip [data-astryx-story-caption]').length === 3"))
        assertEquals("0", h.eval("document.querySelectorAll('#clip-layer [data-astryx-story-done]').length"))

        assertTrue(h.waitFor("!!document.querySelector('#sticker-layer [data-astryx-story-done]')"))
        assertEquals("Fertig", h.eval("document.querySelector('[data-astryx-story-done]').textContent").unquoted())
        h.eval(RECORD_MOUSE)
        h.eval("document.querySelector('[data-astryx-story-done]').click()")
        assertTrue(h.eval("window.__mouse.join(' ')"), h.waitFor("window.__mouse.join(' ').indexOf('mousedown@') >= 0 && window.__mouse.join(' ').indexOf('mouseup@') >= 0"))
    }

    @Test
    fun aFingerDraggingAStickerOnTheStory_isReplayedAsAMouseDrag_aTapIsNot() {
        val h = Harness()
        h.load(CREATOR)
        h.run()
        h.eval("window.__showFrame()")
        assertTrue(h.waitFor("!!document.querySelector('[data-astryx-story=main]')"))
        h.eval(RECORD_MOUSE)

        // A tap stays the browser's own; then the mouse is put where the finger was (a sticker's ×
        // shows on mouse hover).
        h.eval(touch("touchstart", 100, 300) + touch("touchmove", 102, 301) + touch("touchend", 102, 301))
        assertEquals("tap", "", h.eval("window.__mouse.join(' ')").unquoted())
        assertTrue(h.eval("window.__mouse.join(' ')"), h.waitFor("window.__mouse.join(' ') === 'pointermove@102,301 mousemove@102,301'"))
        h.eval("window.__mouse = []")

        h.eval(touch("touchstart", 100, 300) + touch("touchmove", 100, 330) + touch("touchmove", 100, 360) +
            touch("touchend", 100, 360))
        assertEquals(
            "pointerdown@100,300 mousedown@100,300 pointermove@100,330 mousemove@100,330 " +
                "pointermove@100,360 mousemove@100,360 pointerup@100,360 mouseup@100,360",
            h.eval("window.__mouse.join(' ')").unquoted()
        )
    }

    private companion object {
        // Facebook's creator: the sidebar (privacy in its header, the tools and the AI label row,
        // Discard / Share at its foot) beside the preview, whose 316 px column holds the frame.
        // Facebook sizes the frame (innerHeight - 256) x 9/16 wide on every resize. Its narrow
        // layout repeats privacy / Discard / Share outside the sidebar.
        const val CREATOR =
            "<div style='display:flex'>" +
                "<div role='navigation' id='nav' style='width:360px'>" +
                "<div id='header'><span id='title'>Your story</span>" +
                "<div role='button' id='privacy' aria-label='Story privacy settings'><svg width='20' height='20'></svg></div></div>" +
                "<div id='tools'>" +
                "<div role='button' class='t'><svg width='20' height='20'></svg><span>Add text</span></div>" +
                "<div role='button' class='t'><svg width='20' height='20'></svg><span>Add music</span></div>" +
                "<div role='button' class='t'><svg width='20' height='20'></svg><span>Alternative text</span></div>" +
                "<div role='button' id='ai' style='height:122px'><svg width='20' height='20'></svg><span>Add AI label</span>" +
                "<input type='checkbox'></div>" +
                "</div>" +
                "<div id='actions'><div role='button'>Discard</div><div role='button'>Share to story</div></div>" +
                "</div>" +
                "<div role='main' id='main'>" +
                "<div id='narrow'><div role='button' id='narrow-privacy' aria-label='Story privacy settings'><svg width='20' height='20'></svg></div>" +
                "<div role='button' id='narrow-discard'>Discard</div><div role='button'>Share to story</div></div>" +
                "<div id='panel'><div role='button' id='see-all'>See all</div><div role='button'>Browse</div></div>" +
                "<div id='column' style='position:relative;width:316px;height:1500px;overflow:hidden'>" +
                "<div id='box' style='position:absolute;left:0;right:0;top:0;bottom:0'></div></div>" +
                "</div></div>" +
                "<script>window.__realHeight = window.innerHeight;" +
                "function __size() { var f = document.getElementById('frame'); if (!f) return;" +
                "  f.setAttribute('style', 'position: absolute; height: calc(100% - 68px); top: 16px; width: ' + ((window.innerHeight - 256) * 9 / 16) + 'px;'); }" +
                "window.addEventListener('resize', __size);" +
                "window.__showFrame = function () { document.getElementById('box').innerHTML =" +
                "  \"<div id='frame'><img width='10' height='10'></div>\"; __size(); };" +
                "window.__hideFrame = function () { document.getElementById('box').innerHTML = ''; };" +
                "window.__frameSize = function () { var f = document.getElementById('frame');" +
                "  return { width: Math.round(parseFloat(f.style.width)), boxHeight: document.getElementById('box').getBoundingClientRect().height }; };" +
                "</script>"

        // Facebook's popup hosts: fixed layers placed with translate(); a closed one keeps its
        // (hidden) content.
        const val POPUPS =
            "<div id='open-popup' style='position: fixed; transform: translate(355px, 157px);'><div>Search music</div></div>" +
                "<div id='closed-popup' style='position: fixed; transform: translate(355px, 157px);'>" +
                "<div style='visibility: hidden'><img width='10' height='10'>Hidden</div></div>"

        // A popup is open, and Discard / Share are in the bottom bar.
        const val POPUP_OPEN_AND_LAID_OUT = "document.documentElement.classList.contains('astryx-story-popup-open') &&" +
            " document.getElementById('actions').getAttribute('data-astryx-story') === 'actions'"

        // Add music's song list, as read from a Galaxy A56 (2026-10-27): a search box with a small
        // close button at the popup's top right (which hides the popup's content), then the songs.
        const val MUSIC_LIST =
            "<div id='music' style='position: fixed; transform: translate(53px, 64px);'><div id='music-content' style='width:280px'>" +
                "<div style='display:flex;justify-content:space-between'><input aria-label='Search music' style='width:170px'>" +
                "<div role='button' aria-label='Close' style='width:36px;height:36px'" +
                " onclick=\"window.__musicClosed = true; document.getElementById('music-content').style.visibility = 'hidden'\">x</div></div>" +
                "<div role='button' aria-label='Play' style='width:24px;height:24px'>&gt;</div><div>For you</div></div></div>"

        // A music sticker's panel (song, Remove, four style buttons named only for screen readers)
        // and the song clip bar (Pause, Undo, Done; no text). Facebook's fixed layers.
        const val STICKER_PANEL =
            "<div id='sticker-layer' style='position: fixed; transform: translate(40px, 300px);'><div style='background:#222;width:300px'>" +
                "<div>Hotel California</div><div role='button'>Remove</div>" +
                "<div id='styles' style='display:flex'>" +
                "<div role='button' aria-label='Large lyrics style' style='width:40px;height:40px'><svg width='20' height='20'></svg></div>" +
                "<div role='button' aria-label='Light lyrics style' style='width:40px;height:40px'><svg width='20' height='20'></svg></div>" +
                "<div role='button' aria-label='Dark lyrics style' style='width:40px;height:40px'><svg width='20' height='20'></svg></div>" +
                "<div role='button' aria-label='Floating lyrics style' style='width:40px;height:40px'><svg width='20' height='20'></svg></div>" +
                "</div></div></div>" +
                "<div id='clip-layer' style='position: fixed; transform: translate(10px, 700px);'><div id='clip' style='display:flex;width:360px'>" +
                "<div role='button' aria-label='Pause' style='width:40px;height:40px'><svg width='20' height='20'></svg></div>" +
                "<div style='width:300px;height:40px'></div>" +
                "<div role='button' aria-label='Undo' style='width:40px;height:40px'><svg width='20' height='20'></svg></div>" +
                "<div role='button' aria-label='Done' style='width:40px;height:40px'><svg width='20' height='20'></svg></div>" +
                "</div></div>"

        const val RECORD_MOUSE = "window.__mouse = [];" +
            "['pointerdown', 'mousedown', 'pointermove', 'mousemove', 'pointerup', 'mouseup'].forEach(function (t) {" +
            "  document.getElementById('frame').addEventListener(t, function (e) {" +
            "    if (e.pointerType === undefined || e.pointerType === 'mouse') window.__mouse.push(t + '@' + e.clientX + ',' + e.clientY); }); });"

        // A one-finger touch event on the story's frame, at (x, y).
        fun touch(type: String, x: Int, y: Int) =
            "(function () { var f = document.getElementById('frame');" +
                " var t = new Touch({ identifier: 1, target: f, clientX: $x, clientY: $y, screenX: $x, screenY: $y });" +
                " f.dispatchEvent(new TouchEvent('$type', { bubbles: true, cancelable: true, composed: true," +
                "   touches: '$type' === 'touchend' ? [] : [t], targetTouches: '$type' === 'touchend' ? [] : [t], changedTouches: [t] })); })();"
    }
}
