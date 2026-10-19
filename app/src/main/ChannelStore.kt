@file:Suppress("PackageDirectoryMismatch")

package com.se.simpletv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Everything the app keeps between launches, in one SharedPreferences file. */
class ChannelStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadChannels(): List<Channel> {
        val saved = prefs.getString(KEY_CHANNELS, null) ?: return emptyList()
        return try {
            val array = JSONArray(saved)
            val channels = mutableListOf<Channel>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val name = item.optString("name").trim()
                val url = item.optString("url").trim()
                val group = item.optString("group").trim()
                // Older versions saved a demo stream alongside the real channels; drop it.
                if (name.isNotBlank() && url.isNotBlank() && url != OLD_DEMO_URL) {
                    channels += Channel(name, url, group)
                }
            }
            if (channels.size != array.length()) saveChannels(channels)
            channels
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveChannels(channels: List<Channel>) {
        val array = JSONArray()
        channels.forEach { channel ->
            array.put(
                JSONObject().apply {
                    put("name", channel.name)
                    put("url", channel.url)
                    put("group", channel.group)
                }
            )
        }
        prefs.edit().putString(KEY_CHANNELS, array.toString()).apply()
    }

    fun loadFavorites(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toSet()

    fun saveFavorites(urls: Set<String>) {
        prefs.edit().putStringSet(KEY_FAVORITES, urls.toSet()).apply()
    }

    var lastUrl: String?
        get() = prefs.getString(KEY_LAST_URL, null)
        set(value) = prefs.edit().putString(KEY_LAST_URL, value).apply()

    var defaultPlaylistsLoaded: Boolean
        get() = prefs.getBoolean(KEY_DEFAULT_PLAYLISTS_LOADED, false)
        set(value) = prefs.edit().putBoolean(KEY_DEFAULT_PLAYLISTS_LOADED, value).apply()

    companion object {
        private const val PREFS = "se_tv_prefs"
        private const val KEY_CHANNELS = "channels"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_LAST_URL = "last_url"
        private const val KEY_DEFAULT_PLAYLISTS_LOADED = "default_playlists_loaded"
        private const val OLD_DEMO_URL = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    }
}
