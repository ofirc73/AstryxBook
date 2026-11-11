package com.astryx.book.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkReturnTest {

    @Test
    fun aNetworkThatWasThereAllAlong_isntTheConnectionComingBack() {
        // The page failed for another reason: loading it again by itself would fail again, forever.
        val connection = NetworkReturn(online = true)
        assertFalse(connection.onOnline())
        assertFalse(connection.onOnline())
    }

    @Test
    fun offlineAtTheStart_thenOnline_isTheConnectionComingBack_once() {
        val connection = NetworkReturn(online = false)
        assertTrue(connection.onOnline())
        assertFalse(connection.onOnline())
    }

    @Test
    fun aNetworkLost_thenBack_isTheConnectionComingBack_eachTime() {
        val connection = NetworkReturn(online = true)
        repeat(2) {
            connection.onLost()
            assertTrue(connection.onOnline())
            assertFalse(connection.onOnline())
        }
    }
}
