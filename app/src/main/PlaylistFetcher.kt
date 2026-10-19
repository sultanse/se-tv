@file:Suppress("PackageDirectoryMismatch")

package com.se.simpletv

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

object PlaylistFetcher {

    private const val TAG = "SeTV"

    /** Downloads and parses one playlist. Blocking, so call it off the main thread; throws on failure. */
    fun fetch(url: String): List<Channel> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "SeTV/2.0")

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code")
            }

            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val channels = if (M3uParser.looksLikeHlsMediaPlaylist(text)) {
                listOf(Channel("قناة مباشرة", url))
            } else {
                M3uParser.parse(text)
            }
            if (channels.isEmpty()) Log.w(TAG, "No channels in $url, starts with: ${text.take(200)}")
            return channels
        } finally {
            connection.disconnect()
        }
    }
}
