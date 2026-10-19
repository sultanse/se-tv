@file:Suppress("PackageDirectoryMismatch")

package com.se.simpletv

import android.content.Context
import android.util.Log
import org.json.JSONArray

/** Everything the app keeps between launches, in one SharedPreferences file. */
class ChannelStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadChannels(): List<Channel> {
        val saved = prefs.getString(KEY_CHANNELS, null) ?: return emptyList()
        val array = try {
            JSONArray(saved)
        } catch (e: Exception) {
            // Keep the unreadable copy, or the next import would save over every channel in it.
            Log.w(TAG, "Saved channels are unreadable, moved to $KEY_CHANNELS_UNREADABLE", e)
            prefs.edit().putString(KEY_CHANNELS_UNREADABLE, saved).remove(KEY_CHANNELS).apply()
            return emptyList()
        }
        // A bad entry drops only itself, not the whole list.
        val channels = (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val name = item.optString("name").trim()
            val url = item.optString("url").trim()
            if (name.isBlank() || url.isBlank()) null
            else Channel(name, url, item.optString("group").trim())
        }
        if (channels.size != array.length()) saveChannels(channels)
        return channels
    }

    fun saveChannels(channels: List<Channel>) {
        val array = JSONArray(channels.map { mapOf("name" to it.name, "url" to it.url, "group" to it.group) })
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
        private const val TAG = "SeTV"
        private const val PREFS = "se_tv_prefs"
        private const val KEY_CHANNELS = "channels"
        private const val KEY_CHANNELS_UNREADABLE = "channels_unreadable"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_LAST_URL = "last_url"
        private const val KEY_DEFAULT_PLAYLISTS_LOADED = "default_playlists_loaded"
    }
}
