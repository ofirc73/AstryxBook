package com.astryx.book.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

// Robolectric for android.util.Log.
@RunWith(RobolectricTestRunner::class)
class FacebookFetchTest {

    @Test
    fun aRedirect_isFollowedOnlyToFacebooksOwnHttpsPages() {
        val from = "https://m.facebook.com/notifications/"
        assertEquals("https://m.facebook.com/login.php?next=x", facebookRedirectUrl(from, "/login.php?next=x"))
        assertEquals("https://www.facebook.com/photo/?fbid=1", facebookRedirectUrl(from, "https://www.facebook.com/photo/?fbid=1"))
        assertEquals("https://facebook.com/", facebookRedirectUrl(from, " https://facebook.com/ "))
        listOf(
            null,
            "",
            "https://example.com/",
            "http://m.facebook.com/",                      // not https: the cookies would go in the clear
            "https://facebook.com.example.com/",
            "https://notfacebook.com/",
            "https://m.facebook.com@example.com/",
            "//example.com/x",
            "https://www.messenger.com/",                  // another site's cookies
            "intent://profile/1#Intent;package=com.facebook.katana;end",
            "https://exa mple.com/",
        ).forEach { assertNull(it, facebookRedirectUrl(from, it)) }
    }

    // A local server that answers every request with [answer] and keeps the requests it got.
    private class Server(answer: (String) -> String) : AutoCloseable {
        val requests = CopyOnWriteArrayList<String>()
        private val socket = ServerSocket(0)
        val url = "http://127.0.0.1:${socket.localPort}/"

        init {
            thread(isDaemon = true) {
                runCatching {
                    while (true) socket.accept().use { client ->
                        val reader = client.getInputStream().bufferedReader()
                        val request = generateSequence { reader.readLine()?.takeIf { it.isNotEmpty() } }.joinToString("\n")
                        requests += request
                        client.getOutputStream().apply { write(answer(request).toByteArray()); flush() }
                    }
                }
            }
        }

        override fun close() = socket.close()
    }

    private val headers = facebookPageHeaders("c_user=1; xs=secret", "Test agent", mobile = true)

    @Test
    fun theLoginsCookies_areNotSentOnToAnotherSite() {
        Server { "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nhi" }.use { other ->
            Server { "HTTP/1.1 302 Found\r\nLocation: ${other.url}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n" }.use { first ->
                assertNull("the redirect to another site was followed", openFacebookPage(first.url, headers))
                assertEquals(1, first.requests.size)
                assertTrue(first.requests.single(), first.requests.single().contains("Cookie: c_user=1; xs=secret"))
                assertTrue("the other site was sent a request: ${other.requests}", other.requests.isEmpty())
            }
        }
    }

    @Test
    fun aPageThatIsntARedirect_isHandedBackWithItsAnswer() {
        Server { "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nhi" }.use { server ->
            val connection = openFacebookPage(server.url, headers)
            assertNotNull(connection)
            try {
                assertEquals(200, connection!!.responseCode)
                assertEquals("hi", connection.inputStream.bufferedReader().readText())
            } finally {
                connection?.disconnect()
            }
            assertTrue(server.requests.single(), server.requests.single().contains("User-Agent: Test agent"))
        }
    }

    @Test
    fun theHeaders_sayMobileOrDesktop() {
        assertEquals("?1", headers["Sec-CH-UA-Mobile"])
        assertEquals("\"Windows\"", facebookPageHeaders("c", "ua", mobile = false)["Sec-CH-UA-Platform"])
    }
}
