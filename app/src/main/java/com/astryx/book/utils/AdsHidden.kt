package com.astryx.book.utils

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * The ads Remove ads hid since the app started, as adblock.js reports each (AstryxPage call
 * `adHidden`): shown in developer options and logged (`Ads hidden:` under `Astryxbook`). The feed's
 * ads are found by their label's colour alone, and the live checks' account is shown no ads: a
 * count that stays 0 on a phone whose feed has ads is how that breaking shows.
 */
object AdsHidden {
    var feed by mutableIntStateOf(0)
        private set
    var reels by mutableIntStateOf(0)
        private set

    /** One more ad hidden, of [kind] "feed" or "reel"; false for anything else. */
    fun count(kind: String): Boolean {
        when (kind) {
            "feed" -> feed++
            "reel" -> reels++
            else -> return false
        }
        Log.d("Astryxbook", "Ads hidden: feed $feed, reels $reels")
        return true
    }
}
