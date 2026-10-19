package com.sultan.simpletv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    data class Channel(
        val name: String,
        val url: String,
        val group: String = ""
    )

    private val channels = mutableListOf<Channel>()

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var menu: LinearLayout
    private lateinit var channelList: ListView
    private lateinit var titleView: TextView
    private lateinit var adapter: ArrayAdapter<String>

    private var currentChannel = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadSavedChannels()
        if (channels.isEmpty()) {
            channels += Channel(
                "بث تجريبي",
                "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                "تجريبي"
            )
        }

        player = ExoPlayer.Builder(this).build()
        playerView = PlayerView(this).apply {
            this.player = this@MainActivity.player
            useController = false
            setBackgroundColor(Color.BLACK)
            isFocusable = true
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        root.addView(
            playerView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        menu = makeMenu()
        root.addView(
            menu,
            FrameLayout.LayoutParams(dp(760), FrameLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.CENTER
                topMargin = dp(26)
                bottomMargin = dp(26)
            }
        )

        setContentView(root)
        hideSystemUi()
        channelList.post { channelList.requestFocus() }
        if (!getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(KEY_DEFAULT_PLAYLISTS_LOADED, false)
        ) {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DEFAULT_PLAYLISTS_LOADED, true)
                .apply()
            DEFAULT_PLAYLIST_URLS.forEach(::loadM3uFromUrl)
        }
    }

    private fun makeMenu(): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(22), dp(28), dp(22))
            setBackgroundColor(Color.argb(235, 20, 20, 20))
        }

        titleView = TextView(this).apply {
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }
        box.addView(
            titleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val fileButton = Button(this).apply {
            text = "فتح ملف M3U"
            textSize = 17f
            isAllCaps = false
            setOnClickListener { openM3uFile() }
        }
        actions.addView(
            fileButton,
            LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                marginEnd = dp(8)
            }
        )

        val urlButton = Button(this).apply {
            text = "رابط M3U"
            textSize = 17f
            isAllCaps = false
            setOnClickListener { showM3uUrlDialog() }
        }
        actions.addView(
            urlButton,
            LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                marginEnd = dp(8)
            }
        )

        box.addView(
            actions,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        )

        adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            android.R.id.text1,
            channels.map { displayName(it) }.toMutableList()
        )

        channelList = ListView(this).apply {
            this.adapter = this@MainActivity.adapter
            dividerHeight = dp(2)
            setBackgroundColor(Color.argb(90, 0, 0, 0))
            isFocusable = true
            isFocusableInTouchMode = true
            choiceMode = ListView.CHOICE_MODE_SINGLE
            selector = makeListSelector()
            setOnItemClickListener { _, _, position, _ -> playChannel(position) }
        }
        box.addView(
            channelList,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val hint = TextView(this).apply {
            text = "OK تشغيل  •  رجوع للقائمة  •  أثناء البث ↑ ↓ تغيير القناة"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        box.addView(hint)

        refreshChannelList()
        return box
    }

    private fun makeListSelector(): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), ColorDrawable(Color.rgb(75, 75, 75)))
        addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(Color.rgb(75, 75, 75)))
        addState(intArrayOf(android.R.attr.state_selected), ColorDrawable(Color.rgb(60, 60, 60)))
        addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
    }

    private fun displayName(channel: Channel): String =
        if (channel.group.isBlank()) channel.name else "${channel.name}  •  ${channel.group}"

    private fun refreshChannelList() {
        if (!::adapter.isInitialized) return
        adapter.clear()
        adapter.addAll(channels.map { displayName(it) })
        adapter.notifyDataSetChanged()
        titleView.text = "Se TV — ${channels.size} قناة"
    }

    private fun playChannel(index: Int) {
        if (channels.isEmpty()) return
        currentChannel = index.coerceIn(channels.indices)
        val channel = channels[currentChannel]

        player.setMediaItem(MediaItem.fromUri(channel.url))
        player.prepare()
        player.playWhenReady = true

        menu.visibility = View.GONE
        playerView.requestFocus()
        Toast.makeText(this, channel.name, Toast.LENGTH_SHORT).show()
    }

    private fun changeChannel(step: Int) {
        if (channels.isEmpty()) return
        currentChannel = (currentChannel + step + channels.size) % channels.size
        playChannel(currentChannel)
    }

    private fun openM3uFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQUEST_M3U_FILE)
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_M3U_FILE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            importM3uFromUri(uri)
        }
    }

    private fun importM3uFromUri(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: throw IllegalArgumentException("تعذر قراءة الملف")

            val imported = parseM3u(text)
            applyImportedChannels(imported)
        } catch (e: Exception) {
            showError("تعذر فتح القائمة: ${e.message ?: "خطأ غير معروف"}")
        }
    }

    private fun showM3uUrlDialog() {
        val input = EditText(this).apply {
            hint = "https://example.com/list.m3u"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }

        AlertDialog.Builder(this)
            .setTitle("رابط قائمة M3U")
            .setView(input)
            .setPositiveButton("تحميل") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotBlank()) loadM3uFromUrl(url)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun loadM3uFromUrl(url: String) {
        Toast.makeText(this, "جاري تحميل القائمة…", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 12_000
                connection.readTimeout = 20_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "SultanTV/2.0")

                val code = connection.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("HTTP $code")
                }

                val text = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val imported = if (looksLikeHlsMediaPlaylist(text)) {
                    listOf(Channel("قناة مباشرة", url))
                } else {
                    parseM3u(text)
                }

                runOnUiThread { applyImportedChannels(imported) }
            } catch (e: Exception) {
                runOnUiThread {
                    showError("تعذر تحميل القائمة: ${e.message ?: "خطأ غير معروف"}")
                }
            }
        }.start()
    }

    private fun looksLikeHlsMediaPlaylist(text: String): Boolean =
        text.lineSequence().any { line ->
            line.startsWith("#EXT-X-TARGETDURATION") ||
                line.startsWith("#EXT-X-STREAM-INF") ||
                line.startsWith("#EXT-X-MEDIA-SEQUENCE")
        }

    private fun parseM3u(text: String): List<Channel> {
        val result = mutableListOf<Channel>()
        var currentName: String? = null
        var currentGroup = ""

        val nameRegex = Regex("tvg-name=\"([^\"]*)\"")
        val groupRegex = Regex("group-title=\"([^\"]*)\"")

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
                    val tvgName = nameRegex.find(line)?.groupValues?.getOrNull(1).orEmpty()
                    currentName = commaName.ifBlank { tvgName }.ifBlank { null }
                    currentGroup = groupRegex.find(line)?.groupValues?.getOrNull(1).orEmpty()
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

    private fun applyImportedChannels(imported: List<Channel>) {
        if (imported.isEmpty()) {
            showError("لم أجد قنوات قابلة للتشغيل داخل القائمة")
            return
        }

        val existingUrls = channels.mapTo(mutableSetOf()) { it.url }
        imported.forEach { channel ->
            if (existingUrls.add(channel.url)) {
                channels += channel
            }
        }
        currentChannel = 0
        saveChannels()
        refreshChannelList()
        channelList.setSelection(0)
        channelList.requestFocus()
        Toast.makeText(this, "تمت إضافة ${imported.size} قناة، الإجمالي ${channels.size}", Toast.LENGTH_LONG).show()
    }

    private fun clearChannels() {
        channels.clear()
        channels += Channel(
            "بث تجريبي",
            "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            "تجريبي"
        )
        currentChannel = 0
        saveChannels()
        refreshChannelList()
        channelList.setSelection(0)
        channelList.requestFocus()
    }

    private fun saveChannels() {
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
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString(KEY_CHANNELS, array.toString())
            .apply()
    }

    private fun loadSavedChannels() {
        val saved = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(KEY_CHANNELS, null)
            ?: return

        try {
            val array = JSONArray(saved)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val name = item.optString("name").trim()
                val url = item.optString("url").trim()
                val group = item.optString("group").trim()
                if (name.isNotBlank() && url.isNotBlank()) {
                    channels += Channel(name, url, group)
                }
            }
        } catch (_: Exception) {
            channels.clear()
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (menu.visibility == View.GONE) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_CHANNEL_UP -> {
                    changeChannel(-1)
                    return true
                }

                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    changeChannel(1)
                    return true
                }

                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    showMenu()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showMenu() {
        menu.visibility = View.VISIBLE
        channelList.requestFocus()
        channelList.setSelection(currentChannel)
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (menu.visibility == View.GONE) {
            showMenu()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_M3U_FILE = 7001
        private const val PREFS = "sultan_tv_prefs"
        private const val KEY_CHANNELS = "channels"
        private const val KEY_DEFAULT_PLAYLISTS_LOADED = "default_playlists_loaded"
        private val DEFAULT_PLAYLIST_URLS = listOf(
            "https://iptv-org.github.io/iptv/languages/ara.m3u",
            "https://iptv-org.github.io/iptv/regions/arab.m3u",
            "https://iptv-org.github.io/iptv/countries/sa.m3u",
            "https://iptv-org.github.io/iptv/categories/news.m3u"
        )
    }
}
