@file:Suppress("PackageDirectoryMismatch")

package com.se.simpletv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Wires the pieces together: playback, the remote's keys, and the activity lifecycle. */
class MainActivity : Activity() {

    private lateinit var store: ChannelStore
    private lateinit var library: ChannelLibrary
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var menu: ChannelMenu
    private lateinit var nowCard: NowPlayingCard
    private lateinit var banner: ChannelBanner
    private lateinit var message: MessageToast

    private var playingUrl: String? = null
    private var okHeld = false
    private var okLongPressed = false
    private var lastExitPress = 0L
    private var exitPresses = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        store = ChannelStore(this)
        library = ChannelLibrary(store.loadChannels(), store.loadFavorites())

        player = ExoPlayer.Builder(this).build()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val channel = library.find(playingUrl) ?: return
                message.show("تعذّر تشغيل ${channel.title}، جرّب قناة ثانية")
            }
        })
        playerView = PlayerView(this).apply {
            this.player = this@MainActivity.player
            useController = false
            setBackgroundColor(Color.BLACK)
            isFocusable = true
        }

        menu = ChannelMenu(
            this,
            library,
            playingUrl = { playingUrl },
            onChannelSelected = ::selectChannel,
            onAddChannels = { AddChannelsDialog(this, ::openM3uFile, ::loadM3uFromUrl).show() }
        )
        nowCard = NowPlayingCard(this)
        banner = ChannelBanner(this)
        message = MessageToast(this)

        setContentView(buildRoot())
        hideSystemUi()
        menu.refresh()

        library.find(store.lastUrl)?.let(::startPlayback)
        showMenu()

        if (!store.defaultPlaylistsLoaded) loadDefaultPlaylists()
    }

    private fun buildRoot(): FrameLayout {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(playerView, FrameLayout.LayoutParams(MATCH, MATCH))

        // The menu's fade reaches into the left half, so the now-playing card is added after it to stay on top.
        root.addView(menu.view, FrameLayout.LayoutParams(dp(ChannelMenu.WIDTH_DP), MATCH).apply {
            gravity = Gravity.RIGHT
        })
        root.addView(nowCard.view, FrameLayout.LayoutParams(WRAP, WRAP).apply {
            gravity = Gravity.LEFT or Gravity.BOTTOM
            leftMargin = dp(36)
            bottomMargin = dp(32)
        })
        root.addView(banner.view, FrameLayout.LayoutParams(WRAP, WRAP).apply {
            gravity = Gravity.RIGHT or Gravity.TOP
            rightMargin = dp(36)
            topMargin = dp(28)
        })
        root.addView(message.view, FrameLayout.LayoutParams(WRAP, WRAP).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(30)
        })

        root.viewTreeObserver.addOnGlobalFocusChangeListener { _, focused -> menu.updateHints(focused) }
        return root
    }

    // ---------------------------------------------------------------- menu

    private fun showMenu() {
        banner.hide()
        if (playingUrl != null) nowCard.show(fadeIn = true) else nowCard.hide()
        menu.show { currentFocus }
    }

    private fun hideMenu() {
        menu.hide()
        nowCard.hide()
        playerView.requestFocus()
    }

    private fun toggleFavorite(channel: Channel) {
        if (library.toggleFavorite(channel)) {
            message.show("أضفت ${channel.title} للمفضلة")
        } else {
            message.show("شلت ${channel.title} من المفضلة")
        }
        store.saveFavorites(library.favorites)
        menu.refresh(keepPosition = true)
    }

    // ---------------------------------------------------------------- playback

    private fun startPlayback(channel: Channel) {
        playingUrl = channel.url
        player.setMediaItem(MediaItem.fromUri(channel.url))
        player.prepare()
        player.playWhenReady = true
        nowCard.setChannel(channel)
        store.lastUrl = channel.url
    }

    private fun playChannel(channel: Channel) {
        startPlayback(channel)
        hideMenu()
        banner.show(channel, library.numberOf(channel))
    }

    /**
     * OK in the menu: the first press starts the channel behind the open menu,
     * a second press on the channel that is already playing closes the menu.
     */
    private fun selectChannel(channel: Channel) {
        if (channel.url == playingUrl) {
            hideMenu()
            banner.show(channel, library.numberOf(channel))
            return
        }
        startPlayback(channel)
        nowCard.show(fadeIn = false)
        menu.onPlayingChanged(currentFocus)
    }

    /** Channel up/down stays inside the category or search the viewer picked. */
    private fun changeChannel(step: Int) {
        val list = menu.visibleChannels.ifEmpty { library.channels }
        if (list.isEmpty()) return
        val current = list.indexOfFirst { it.url == playingUrl }
        val next = if (current < 0) 0 else (current + step + list.size) % list.size
        playChannel(list[next])
    }

    // ---------------------------------------------------------------- importing playlists

    /**
     * Fetches the default lists one after another and only marks them loaded once every list
     * arrived, so a failed download is retried on the next launch instead of being lost.
     */
    private fun loadDefaultPlaylists() {
        message.show("جاري تحميل القوائم…")
        Thread {
            val imported = mutableListOf<Channel>()
            var failed = 0
            DEFAULT_PLAYLIST_URLS.forEach { url ->
                try {
                    imported += PlaylistFetcher.fetch(url)
                } catch (e: Exception) {
                    failed++
                    Log.w(TAG, "Default playlist failed: $url", e)
                }
            }
            runOnUiThread {
                if (failed == 0) store.defaultPlaylistsLoaded = true
                if (imported.isNotEmpty()) applyImportedChannels(imported)
                if (failed > 0) {
                    message.show("تعذّر تحميل $failed من القوائم، بتتحمّل تلقائيًا في التشغيل الجاي", long = true)
                }
            }
        }.start()
    }

    private fun loadM3uFromUrl(url: String) {
        message.show("جاري تحميل القائمة…")
        Thread {
            try {
                val imported = PlaylistFetcher.fetch(url)
                runOnUiThread { applyImportedChannels(imported) }
            } catch (e: Exception) {
                Log.w(TAG, "Playlist failed: $url", e)
                runOnUiThread {
                    message.show("تعذر تحميل القائمة: ${e.message ?: "خطأ غير معروف"}", long = true)
                }
            }
        }.start()
    }

    private fun openM3uFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            startActivityForResult(intent, REQUEST_M3U_FILE)
        } catch (_: android.content.ActivityNotFoundException) {
            message.show("ما فيه تطبيق ملفات على الشاشة، استخدم «تحميل من رابط»", long = true)
        }
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
            applyImportedChannels(M3uParser.parse(text))
        } catch (e: Exception) {
            message.show("تعذر فتح القائمة: ${e.message ?: "خطأ غير معروف"}", long = true)
        }
    }

    private fun applyImportedChannels(imported: List<Channel>) {
        if (imported.isEmpty()) {
            message.show("لم أجد قنوات قابلة للتشغيل داخل القائمة", long = true)
            return
        }
        val added = library.addAll(imported)
        store.saveChannels(library.channels)
        menu.refresh()
        message.show("تمت إضافة $added قناة، الإجمالي ${library.channels.size}", long = true)
    }

    // ---------------------------------------------------------------- keys & lifecycle

    /**
     * OK on a channel selects it on release (see [selectChannel]); holding OK toggles it as a favorite instead.
     * Handled here because ListView only detects long presses for touch-like timing.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            Log.d(TAG, "Key ${KeyEvent.keyCodeToString(event.keyCode)} (${event.keyCode})")
        }
        if (event.keyCode in EXIT_KEYS) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) onExitPressed()
            return true
        }

        val isOk = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER
        if (!isOk || !menu.isOpen || !menu.channelListHasFocus) return super.dispatchKeyEvent(event)

        val channel = menu.selectedChannel
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    okHeld = true
                    okLongPressed = false
                } else if (okHeld && !okLongPressed && channel != null &&
                    (event.flags and KeyEvent.FLAG_LONG_PRESS != 0 || event.eventTime - event.downTime >= LONG_PRESS_MS)
                ) {
                    okLongPressed = true
                    toggleFavorite(channel)
                }
            }

            KeyEvent.ACTION_UP -> {
                if (okHeld && !okLongPressed && channel != null) selectChannel(channel)
                okHeld = false
            }
        }
        return true
    }

    /**
     * Exit on the remote closes the app after [EXIT_PRESSES] presses in a row, each within
     * [EXIT_CONFIRM_MS] of the last, so a stray press doesn't.
     */
    private fun onExitPressed() {
        val now = SystemClock.uptimeMillis()
        exitPresses = if (now - lastExitPress <= EXIT_CONFIRM_MS) exitPresses + 1 else 1
        lastExitPress = now

        val left = EXIT_PRESSES - exitPresses
        when {
            left <= 0 -> {
                player.stop()
                finishAndRemoveTask()
            }
            left == 1 -> message.show("Press Exit once more to close")
            else -> message.show("Press Exit $left more times to close")
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!menu.isOpen) {
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

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        when {
            !menu.isOpen -> showMenu()
            playingUrl != null -> hideMenu()
            else -> super.onBackPressed()
        }
    }

    override fun onStart() {
        super.onStart()
        if (playingUrl != null) player.playWhenReady = true
    }

    override fun onStop() {
        player.pause()
        super.onStop()
    }

    override fun onDestroy() {
        banner.release()
        message.release()
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

    companion object {
        private const val TAG = "SeTV"
        private const val REQUEST_M3U_FILE = 7001
        private const val LONG_PRESS_MS = 600L
        private const val EXIT_CONFIRM_MS = 2000L
        private const val EXIT_PRESSES = 3
        private val EXIT_KEYS = setOf(KeyEvent.KEYCODE_ESCAPE)
        private val DEFAULT_PLAYLIST_URLS = listOf(
            "https://iptv-org.github.io/iptv/languages/ara.m3u",
            // "https://iptv-org.github.io/iptv/regions/arab.m3u",
            // "https://iptv-org.github.io/iptv/countries/sa.m3u",
            // "https://iptv-org.github.io/iptv/categories/news.m3u"
        )
    }
}
