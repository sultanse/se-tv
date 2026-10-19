@file:Suppress("PackageDirectoryMismatch")

package com.se.simpletv

/** Reads M3U playlists into channels. Pure text processing, no Android dependencies. */
object M3uParser {

    private val NAME_REGEX = Regex("tvg-name=\"([^\"]*)\"")
    private val GROUP_REGEX = Regex("group-title=\"([^\"]*)\"")
    private val STREAM_SCHEMES = listOf("http://", "https://", "rtsp://")

    fun parse(text: String): List<Channel> {
        val result = mutableListOf<Channel>()
        var currentName: String? = null
        var currentGroup = ""

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val comma = titleComma(line)
                    val commaName = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    val tvgName = NAME_REGEX.find(line)?.groupValues?.get(1).orEmpty()
                    currentName = commaName.ifBlank { tvgName }.ifBlank { null }
                    currentGroup = GROUP_REGEX.find(line)?.groupValues?.get(1).orEmpty()
                }

                line.isNotBlank() && !line.startsWith("#") -> {
                    if (STREAM_SCHEMES.any { line.startsWith(it, ignoreCase = true) }) {
                        result += Channel(
                            name = currentName ?: "قناة ${result.size + 1}",
                            url = line,
                            group = currentGroup
                        )
                    }
                    currentName = null
                    currentGroup = ""
                }
            }
        }

        return result.distinctBy { it.url }
    }

    /** The comma before an #EXTINF title: the first one outside a quoted attribute like tvg-name="A, B". */
    private fun titleComma(line: String): Int {
        var quoted = false
        line.forEachIndexed { i, c ->
            if (c == '"') quoted = !quoted
            else if (c == ',' && !quoted) return i
        }
        return -1
    }

    /** True for a single stream's own playlist (pasted instead of a channel list). */
    fun looksLikeHlsMediaPlaylist(text: String): Boolean =
        text.lineSequence().any { line ->
            line.startsWith("#EXT-X-TARGETDURATION") ||
                line.startsWith("#EXT-X-STREAM-INF") ||
                line.startsWith("#EXT-X-MEDIA-SEQUENCE")
        }
}
