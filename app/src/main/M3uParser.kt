@file:Suppress("PackageDirectoryMismatch")

package com.se.simpletv

/** Reads M3U playlists into channels. Pure text processing, no Android dependencies. */
object M3uParser {

    private val NAME_REGEX = Regex("tvg-name=\"([^\"]*)\"")
    private val GROUP_REGEX = Regex("group-title=\"([^\"]*)\"")

    fun parse(text: String): List<Channel> {
        val result = mutableListOf<Channel>()
        var currentName: String? = null
        var currentGroup = ""

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val commaIndex = line.indexOf(',')
                    val commaName = if (commaIndex >= 0 && commaIndex < line.lastIndex) {
                        line.substring(commaIndex + 1).trim()
                    } else {
                        ""
                    }
                    val tvgName = NAME_REGEX.find(line)?.groupValues?.getOrNull(1).orEmpty()
                    currentName = commaName.ifBlank { tvgName }.ifBlank { null }
                    currentGroup = GROUP_REGEX.find(line)?.groupValues?.getOrNull(1).orEmpty()
                }

                line.isNotBlank() && !line.startsWith("#") -> {
                    if (line.startsWith("http://", ignoreCase = true) ||
                        line.startsWith("https://", ignoreCase = true) ||
                        line.startsWith("rtsp://", ignoreCase = true)
                    ) {
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

    /** True for a single stream's own playlist (pasted instead of a channel list). */
    fun looksLikeHlsMediaPlaylist(text: String): Boolean =
        text.lineSequence().any { line ->
            line.startsWith("#EXT-X-TARGETDURATION") ||
                line.startsWith("#EXT-X-STREAM-INF") ||
                line.startsWith("#EXT-X-MEDIA-SEQUENCE")
        }
}
