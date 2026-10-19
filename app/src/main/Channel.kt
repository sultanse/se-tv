@file:Suppress("PackageDirectoryMismatch")

package com.se.simpletv

data class Channel(
    val name: String,
    val url: String,
    val group: String = ""
) {
    // Channels are only read on the main thread, so the lazy values skip locking.

    /** Name without the "(1080p)" and "[Geo-blocked]" markers that iptv-org lists append. */
    val title: String by lazy(LazyThreadSafetyMode.NONE) {
        name.replace(QUALITY_REGEX, "").replace(TAG_REGEX, "").trim().ifBlank { name }
    }

    val quality: String by lazy(LazyThreadSafetyMode.NONE) {
        val height = QUALITY_REGEX.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        when {
            height >= 2160 -> "4K"
            height >= 1080 -> "FHD"
            height >= 720 -> "HD"
            height > 0 -> "SD"
            else -> ""
        }
    }

    val geoBlocked: Boolean get() = name.contains("[Geo-blocked]", ignoreCase = true)
    val notAlwaysOn: Boolean get() = name.contains("[Not 24/7]", ignoreCase = true)

    /** A channel can sit in several groups, e.g. "General;News". Group names ignore case, see [groupKey]. */
    val groups: List<String> by lazy(LazyThreadSafetyMode.NONE) {
        group.split(';').map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy(::groupKey)
            .ifEmpty { listOf(UNDEFINED_GROUP) }
    }

    /** The groups in Arabic, as shown under the channel's name. */
    val groupsLabel: String by lazy(LazyThreadSafetyMode.NONE) { groups.joinToString("، ", transform = ::groupLabel) }

    companion object {
        const val UNDEFINED_GROUP = "Undefined"
        private val QUALITY_REGEX = Regex("\\s*\\((\\d{3,4})[pi]\\)")
        private val TAG_REGEX = Regex("\\s*\\[[^\\]]+]")
    }
}

private val GROUP_LABELS = sortedMapOf(
    String.CASE_INSENSITIVE_ORDER,
    "News" to "أخبار",
    "General" to "عام",
    "Religious" to "ديني",
    "Music" to "موسيقى",
    "Undefined" to "أخرى",
    "Sports" to "رياضة",
    "Entertainment" to "ترفيه",
    "Culture" to "ثقافة",
    "Movies" to "أفلام",
    "Documentary" to "وثائقي",
    "Kids" to "أطفال",
    "Business" to "اقتصاد",
    "Education" to "تعليم",
    "Public" to "قنوات رسمية",
    "Lifestyle" to "أسلوب حياة",
    "Family" to "عائلة",
    "Series" to "مسلسلات",
    "Cooking" to "طبخ",
    "Classic" to "كلاسيكي",
    "Relax" to "استرخاء",
    "Outdoor" to "طبيعة ومغامرات",
    "Comedy" to "كوميديا",
    "Science" to "علوم",
    "Travel" to "سفر",
    "Animation" to "رسوم متحركة",
    "Shop" to "تسوّق",
    "Legislative" to "برلماني",
    "Interactive" to "تفاعلي",
    "Auto" to "سيارات"
)

fun groupLabel(group: String): String = GROUP_LABELS[group] ?: group

/** Playlists spell the same group differently ("News", "news"); groups with the same key are one group. */
fun groupKey(group: String): String = group.lowercase()
