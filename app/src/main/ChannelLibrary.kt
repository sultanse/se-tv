@file:Suppress("PackageDirectoryMismatch")

package com.se.simpletv

data class Category(
    val kind: Kind,
    val label: String = "",
    val count: Int = 0,
    val group: String = ""
) {
    enum class Kind { ALL, FAVORITES, GROUP, SEPARATOR, ADD }
}

/**
 * The viewer's channels and favorites, and the questions the menu asks about them.
 * Pure Kotlin so it can be unit-tested; saving is the caller's job (see [ChannelStore]).
 */
class ChannelLibrary(channels: List<Channel> = emptyList(), favorites: Set<String> = emptySet()) {

    private val _channels = mutableListOf<Channel>()
    private val _favorites = favorites.toMutableSet()
    private val numberByUrl = mutableMapOf<String, Int>()

    val channels: List<Channel> get() = _channels
    val favorites: Set<String> get() = _favorites

    init {
        addAll(channels)
    }

    /** Appends the channels whose URL isn't in the library yet. Returns how many were added. */
    fun addAll(imported: List<Channel>): Int {
        var added = 0
        imported.forEach { channel ->
            if (channel.url !in numberByUrl) {
                _channels += channel
                numberByUrl[channel.url] = _channels.size
                added++
            }
        }
        return added
    }

    fun find(url: String?): Channel? = url?.let { u -> _channels.firstOrNull { it.url == u } }

    /** 1-based position in the full list; the number shown next to a channel. */
    fun numberOf(channel: Channel): Int = numberByUrl[channel.url] ?: 0

    fun isFavorite(channel: Channel): Boolean = channel.url in _favorites

    /** Adds or removes the favorite. Returns true when the channel is now a favorite. */
    fun toggleFavorite(channel: Channel): Boolean =
        if (_favorites.add(channel.url)) true else !_favorites.remove(channel.url)

    /** All, favorites, then each group by size, then the "add channels" entry. */
    fun categories(): List<Category> {
        val counts = mutableMapOf<String, Int>()
        _channels.forEach { channel -> channel.groups.forEach { counts[it] = (counts[it] ?: 0) + 1 } }

        return buildList {
            add(Category(Category.Kind.ALL, "كل القنوات", _channels.size))
            add(Category(Category.Kind.FAVORITES, "المفضلة", _channels.count { it.url in _favorites }))
            add(Category(Category.Kind.SEPARATOR))
            counts.entries.sortedByDescending { it.value }.forEach { (group, count) ->
                add(Category(Category.Kind.GROUP, groupLabel(group), count, group))
            }
            add(Category(Category.Kind.SEPARATOR))
            add(Category(Category.Kind.ADD, "إضافة قنوات"))
        }
    }

    fun filter(category: Category?, query: String): List<Channel> =
        _channels.filter { channel ->
            val inCategory = when (category?.kind) {
                Category.Kind.FAVORITES -> channel.url in _favorites
                Category.Kind.GROUP -> category.group in channel.groups
                else -> true
            }
            inCategory && (query.isEmpty() || channel.title.contains(query, ignoreCase = true))
        }
}
