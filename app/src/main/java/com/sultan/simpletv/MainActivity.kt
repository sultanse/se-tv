package com.sultan.simpletv

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {

    private val channels = mutableListOf<Channel>()
    private val favorites = mutableSetOf<String>()
    private val numberByUrl = mutableMapOf<String, Int>()
    private var visibleChannels: List<Channel> = emptyList()
    private var categories: List<Category> = emptyList()
    private var activeCategory = 0
    private var query = ""
    private var playingUrl: String? = null
    private var menuOpen = true
    private var okHeld = false
    private var okLongPressed = false
    private var lastExitPress = 0L
    private var exitPresses = 0

    private val handler = Handler(Looper.getMainLooper())
    private val hideBanner = Runnable { banner.animate().alpha(0f).setDuration(200).start() }
    private val hideMessage = Runnable { messageView.animate().alpha(0f).setDuration(200).start() }

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var menu: LinearLayout
    private lateinit var totalView: TextView
    private lateinit var searchField: EditText
    private lateinit var categoryList: ListView
    private lateinit var channelList: ListView
    private lateinit var emptyView: TextView
    private lateinit var hintsRow: LinearLayout
    private lateinit var nowCard: LinearLayout
    private lateinit var nowName: TextView
    private lateinit var banner: LinearLayout
    private lateinit var bannerNumber: TextView
    private lateinit var bannerName: TextView
    private lateinit var bannerMeta: TextView
    private lateinit var messageView: TextView
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var channelAdapter: ChannelAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadSavedChannels()
        loadFavorites()

        player = ExoPlayer.Builder(this).build()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val channel = channels.firstOrNull { it.url == playingUrl } ?: return
                showMessage("تعذّر تشغيل ${channel.title}، جرّب قناة ثانية")
            }
        })
        playerView = PlayerView(this).apply {
            this.player = this@MainActivity.player
            useController = false
            setBackgroundColor(Color.BLACK)
            isFocusable = true
        }

        setContentView(buildRoot())
        hideSystemUi()
        refreshChannelList()

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        prefs.getString(KEY_LAST_URL, null)
            ?.let { url -> channels.firstOrNull { it.url == url } }
            ?.let { startPlayback(it) }
        showMenu()

        if (!prefs.getBoolean(KEY_DEFAULT_PLAYLISTS_LOADED, false)) loadDefaultPlaylists()
    }

    /**
     * Fetches the default lists one after another and only marks them loaded once every list
     * arrived, so a failed download is retried on the next launch instead of being lost.
     */
    private fun loadDefaultPlaylists() {
        showMessage("جاري تحميل القوائم…")
        Thread {
            val imported = mutableListOf<Channel>()
            var failed = 0
            DEFAULT_PLAYLIST_URLS.forEach { url ->
                try {
                    imported += fetchPlaylist(url)
                } catch (e: Exception) {
                    failed++
                    Log.w(TAG, "Default playlist failed: $url", e)
                }
            }
            runOnUiThread {
                if (failed == 0) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putBoolean(KEY_DEFAULT_PLAYLISTS_LOADED, true)
                        .apply()
                }
                if (imported.isNotEmpty()) applyImportedChannels(imported)
                if (failed > 0) showError("تعذّر تحميل $failed من القوائم، بتتحمّل تلقائيًا في التشغيل الجاي")
            }
        }.start()
    }

    // ---------------------------------------------------------------- layout

    private fun buildRoot(): FrameLayout {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(playerView, FrameLayout.LayoutParams(MATCH, MATCH))

        // The menu's fade reaches into the left half, so the now-playing card is added after it to stay on top.
        menu = buildMenu()
        root.addView(menu, FrameLayout.LayoutParams(dp(MENU_WIDTH_DP), MATCH).apply { gravity = Gravity.RIGHT })

        nowCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundRect(Palette.night(0.88f), dp(11).toFloat())
            setPadding(dp(16), dp(11), dp(16), dp(11))
            addView(liveLabel("يُعرض الآن", 10f))
            nowName = label(sizeSp = 20f, typeface = TvFonts.medium).apply {
                maxWidth = dp(230)
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            addView(nowName, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(4) })
        }
        root.addView(nowCard, FrameLayout.LayoutParams(WRAP, WRAP).apply {
            gravity = Gravity.LEFT or Gravity.BOTTOM
            leftMargin = dp(36)
            bottomMargin = dp(32)
        })

        banner = buildBanner()
        root.addView(banner, FrameLayout.LayoutParams(WRAP, WRAP).apply {
            gravity = Gravity.RIGHT or Gravity.TOP
            rightMargin = dp(36)
            topMargin = dp(28)
        })

        messageView = label(sizeSp = 12f, color = Palette.INK, typeface = TvFonts.medium).apply {
            background = roundRect(Palette.TEXT, dp(20).toFloat())
            setPadding(dp(16), dp(8), dp(16), dp(8))
            alpha = 0f
        }
        root.addView(messageView, FrameLayout.LayoutParams(WRAP, WRAP).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(30)
        })

        root.viewTreeObserver.addOnGlobalFocusChangeListener { _, focused -> updateHints(focused) }
        return root
    }

    private fun buildMenu(): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // The extra end padding is the fade area, so the lists sit where they did before.
            setPaddingRelative(dp(36), dp(28), dp(MENU_FADE_DP + 14), dp(20))
            background = EdgeFadeDrawable(Palette.night(0.96f), dp(MENU_FADE_DP))
        }

        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        box.addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // categories column (right side in RTL)
        val categoryColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label("SE TV", 23f, typeface = TvFonts.semiBold).apply {
                textDirection = View.TEXT_DIRECTION_LTR
            })
            totalView = label(sizeSp = 11f, color = Palette.MUTED)
            addView(totalView, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(8) })
        }
        categoryColumn.addView(brand, LinearLayout.LayoutParams(MATCH, dp(36)))

        categoryAdapter = CategoryAdapter(this)
        categoryList = makeList().apply {
            adapter = categoryAdapter
            setOnItemClickListener { _, _, position, _ -> onCategoryClicked(position) }
        }
        categoryColumn.addView(categoryList, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(16) })
        body.addView(categoryColumn, LinearLayout.LayoutParams(dp(180), MATCH))

        // channels column
        val channelColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        searchField = buildSearchField()
        channelColumn.addView(searchField, LinearLayout.LayoutParams(MATCH, dp(36)))

        val listFrame = FrameLayout(this)
        channelAdapter = ChannelAdapter(
            this,
            numberOf = { numberByUrl[it.url] ?: 0 },
            isFavorite = { it.url in favorites },
            isPlaying = { it.url == playingUrl }
        )
        channelList = makeList().apply {
            adapter = channelAdapter
            setOnItemClickListener { _, _, position, _ -> visibleChannels.getOrNull(position)?.let(::selectChannel) }
        }
        listFrame.addView(channelList, FrameLayout.LayoutParams(MATCH, MATCH))
        emptyView = label(sizeSp = 14f, color = Palette.MUTED).apply {
            setLineSpacing(0f, 1.5f)
            setPadding(dp(12), dp(40), dp(12), 0)
        }
        listFrame.addView(emptyView, FrameLayout.LayoutParams(MATCH, WRAP))
        channelList.emptyView = emptyView
        channelColumn.addView(listFrame, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = dp(10) })

        body.addView(channelColumn, LinearLayout.LayoutParams(0, MATCH, 1f).apply { marginStart = dp(20) })

        // explicit remote paths between the three focus zones
        listOf(searchField, categoryList, channelList).forEach { it.id = View.generateViewId() }
        channelList.nextFocusRightId = categoryList.id
        channelList.nextFocusUpId = searchField.id
        searchField.nextFocusDownId = channelList.id
        searchField.nextFocusRightId = categoryList.id
        categoryList.nextFocusLeftId = channelList.id

        hintsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, 0)
        }
        box.addView(hintsRow, LinearLayout.LayoutParams(MATCH, WRAP))
        return box
    }

    private fun makeList() = ListView(this).apply {
        divider = null
        selector = ColorDrawable(Color.TRANSPARENT)
        cacheColorHint = Color.TRANSPARENT
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        isVerticalFadingEdgeEnabled = true
        setFadingEdgeLength(dp(24))
        clipToPadding = false
        isFocusable = true
        itemsCanFocus = false
    }

    private fun buildSearchField() = EditText(this).apply {
        hint = "ابحث باسم القناة"
        textSize = 13f
        typeface = TvFonts.regular
        includeFontPadding = false
        setTextColor(Palette.TEXT)
        setHintTextColor(Palette.FAINT)
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        setPaddingRelative(dp(14), 0, dp(14), 0)
        compoundDrawablePadding = dp(8)
        setCompoundDrawablesRelativeWithIntrinsicBounds(
            IconDrawable(IconDrawable.Icon.SEARCH, colorsOnFocus(Palette.MUTED), dp(15)), null, null, null
        )
        background = StateListDrawable().apply {
            val radius = dp(18).toFloat()
            addState(
                intArrayOf(android.R.attr.state_focused),
                roundRect(Palette.NIGHT_RAISED, radius, dp(1.5f), Palette.SAND)
            )
            addState(intArrayOf(), roundRect(Palette.NIGHT_RAISED, radius))
        }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim().orEmpty()
                applyFilter()
            }
        })
        setOnEditorActionListener { _, _, _ ->
            hideKeyboard()
            if (visibleChannels.isNotEmpty()) channelList.requestFocus()
            true
        }
    }

    private fun buildBanner() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = roundRect(Palette.night(0.92f), dp(11).toFloat())
        setPaddingRelative(dp(18), dp(11), dp(22), dp(11))
        alpha = 0f
        bannerNumber = label(sizeSp = 26f, color = Palette.SAND, typeface = TvFonts.semiBold).apply {
            fontFeatureSettings = "tnum"
        }
        addView(bannerNumber)
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        bannerName = label(sizeSp = 18f, typeface = TvFonts.medium).apply {
            maxWidth = dp(420)
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        bannerMeta = label(sizeSp = 10f, color = Palette.MUTED)
        // WRAP, not the vertical LinearLayout default MATCH_PARENT, or the name shrinks to the meta line's width.
        texts.addView(bannerName, LinearLayout.LayoutParams(WRAP, WRAP))
        texts.addView(bannerMeta, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(4) })
        addView(texts, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(14) })
    }

    private fun liveLabel(text: String, sizeSp: Float) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Palette.LIVE)
            }
        }, LinearLayout.LayoutParams(dp(6), dp(6)))
        addView(label(text, sizeSp, Palette.LIVE, TvFonts.medium), LinearLayout.LayoutParams(WRAP, WRAP).apply {
            marginStart = dp(5)
        })
    }

    private fun updateHints(focused: View?) {
        val back = if (playingUrl != null) "متابعة المشاهدة" else "خروج"
        val hints = when (focused) {
            searchField -> listOf("OK" to "لوحة المفاتيح", "↓" to "النتائج", "رجوع" to back)
            categoryList -> listOf("OK" to "اختيار", "←" to "القنوات", "رجوع" to back)
            else -> listOf(
                "OK" to "تشغيل",
                "OK مرة ثانية" to "إخفاء القائمة",
                "OK مطوّل" to "مفضلة",
                "→" to "التصنيفات",
                "رجوع" to back
            )
        }
        hintsRow.removeAllViews()
        hints.forEachIndexed { i, (key, text) ->
            val chip = label(key, 8.5f, typeface = TvFonts.medium).apply {
                textDirection = View.TEXT_DIRECTION_LTR
                gravity = Gravity.CENTER
                minWidth = dp(20)
                setPadding(dp(5), dp(3), dp(5), dp(3))
                background = roundRect(Color.TRANSPARENT, dp(5).toFloat(), dp(0.75f).coerceAtLeast(1), Palette.FAINT)
            }
            hintsRow.addView(chip, LinearLayout.LayoutParams(WRAP, WRAP).apply { if (i > 0) marginStart = dp(22) })
            hintsRow.addView(label(text, 10f, Palette.MUTED), LinearLayout.LayoutParams(WRAP, WRAP).apply {
                marginStart = dp(6)
            })
        }
    }

    // ---------------------------------------------------------------- menu state

    private fun refreshChannelList() {
        numberByUrl.clear()
        channels.forEachIndexed { i, channel -> numberByUrl[channel.url] = i + 1 }
        totalView.text = "${channels.size} قناة"
        rebuildCategories()
        applyFilter()
    }

    private fun rebuildCategories() {
        val previous = categories.getOrNull(activeCategory)
        val counts = mutableMapOf<String, Int>()
        channels.forEach { channel -> channel.groups.forEach { counts[it] = (counts[it] ?: 0) + 1 } }

        categories = buildList {
            add(Category(Category.Kind.ALL, "كل القنوات", channels.size))
            add(Category(Category.Kind.FAVORITES, "المفضلة", channels.count { it.url in favorites }))
            add(Category(Category.Kind.SEPARATOR))
            counts.entries.sortedByDescending { it.value }.forEach { (group, count) ->
                add(Category(Category.Kind.GROUP, groupLabel(group), count, group))
            }
            add(Category(Category.Kind.SEPARATOR))
            add(Category(Category.Kind.ADD, "إضافة قنوات"))
        }
        activeCategory = categories.indexOfFirst { it.kind == previous?.kind && it.group == previous.group }
            .takeIf { it >= 0 } ?: 0
        categoryAdapter.items = categories
        categoryAdapter.activePosition = activeCategory
    }

    private fun applyFilter(keepPosition: Boolean = false) {
        val category = categories.getOrNull(activeCategory)
        visibleChannels = channels.filter { channel ->
            val inCategory = when (category?.kind) {
                Category.Kind.FAVORITES -> channel.url in favorites
                Category.Kind.GROUP -> category.group in channel.groups
                else -> true
            }
            inCategory && (query.isEmpty() || channel.title.contains(query, ignoreCase = true))
        }
        val position = channelList.selectedItemPosition
        channelAdapter.items = visibleChannels
        emptyView.text = if (channels.isEmpty()) {
            "ما فيه قنوات بعد.\nاختر «إضافة قنوات» من آخر قائمة التصنيفات."
        } else if (category?.kind == Category.Kind.FAVORITES && query.isEmpty()) {
            "ما عندك قنوات مفضلة.\nوقف على أي قناة واضغط OK ضغطة طويلة عشان تضيفها هنا."
        } else {
            "ما فيه قناة اسمها «$query» في هذا التصنيف.\nجرّب «كل القنوات» أو اكتب جزء من الاسم."
        }
        channelList.setSelection(if (keepPosition) position.coerceIn(0, (visibleChannels.size - 1).coerceAtLeast(0)) else 0)
    }

    private fun onCategoryClicked(position: Int) {
        val category = categories.getOrNull(position) ?: return
        if (category.kind == Category.Kind.ADD) {
            showAddChannelsDialog()
            return
        }
        activeCategory = position
        categoryAdapter.activePosition = position
        applyFilter()
        if (visibleChannels.isNotEmpty()) channelList.requestFocus()
    }

    private fun toggleFavorite(channel: Channel) {
        if (!favorites.add(channel.url)) {
            favorites.remove(channel.url)
            showMessage("شلت ${channel.title} من المفضلة")
        } else {
            showMessage("أضفت ${channel.title} للمفضلة")
        }
        saveFavorites()
        rebuildCategories()
        applyFilter(keepPosition = true)
    }

    private fun showMenu() {
        menuOpen = true
        handler.removeCallbacks(hideBanner)
        banner.animate().alpha(0f).setDuration(150).start()
        menu.visibility = View.VISIBLE
        menu.animate().alpha(1f).translationX(0f).setDuration(220).start()
        nowCard.visibility = if (playingUrl != null) View.VISIBLE else View.GONE
        nowCard.alpha = 0f
        nowCard.animate().alpha(1f).setDuration(220).start()

        channelAdapter.notifyDataSetChanged()
        categoryList.setSelection(activeCategory)
        val playingIndex = visibleChannels.indexOfFirst { it.url == playingUrl }
        if (playingIndex >= 0) {
            channelList.post {
                channelList.setSelectionFromTop(playingIndex, ((channelList.height - dp(48)) / 2).coerceAtLeast(0))
            }
        }
        if (visibleChannels.isNotEmpty()) channelList.requestFocus() else categoryList.requestFocus()
        updateHints(currentFocus)
    }

    private fun hideMenu() {
        menuOpen = false
        hideKeyboard()
        menu.animate().alpha(0f).translationX(dp(40).toFloat()).setDuration(220)
            .withEndAction { if (!menuOpen) menu.visibility = View.GONE }
            .start()
        nowCard.visibility = View.GONE
        playerView.requestFocus()
    }

    // ---------------------------------------------------------------- playback

    private fun startPlayback(channel: Channel) {
        playingUrl = channel.url
        player.setMediaItem(MediaItem.fromUri(channel.url))
        player.prepare()
        player.playWhenReady = true
        nowName.text = channel.title
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LAST_URL, channel.url).apply()
    }

    private fun playChannel(channel: Channel) {
        startPlayback(channel)
        hideMenu()
        showBanner(channel)
    }

    /**
     * OK in the menu: the first press starts the channel behind the open menu,
     * a second press on the channel that is already playing closes the menu.
     */
    private fun selectChannel(channel: Channel) {
        if (channel.url == playingUrl) {
            hideMenu()
            showBanner(channel)
            return
        }
        startPlayback(channel)
        channelAdapter.notifyDataSetChanged()
        nowCard.visibility = View.VISIBLE
        nowCard.alpha = 1f
        updateHints(currentFocus)
    }

    /** Channel up/down stays inside the category or search the viewer picked. */
    private fun changeChannel(step: Int) {
        val list = visibleChannels.ifEmpty { channels }
        if (list.isEmpty()) return
        val current = list.indexOfFirst { it.url == playingUrl }
        val next = if (current < 0) 0 else (current + step + list.size) % list.size
        playChannel(list[next])
    }

    private fun showBanner(channel: Channel) {
        bannerNumber.text = (numberByUrl[channel.url] ?: 0).toString()
        bannerName.text = channel.title
        bannerMeta.text = listOf(channel.groups.joinToString("، ") { groupLabel(it) }, channel.quality)
            .filter { it.isNotEmpty() }
            .joinToString("   ")
        handler.removeCallbacks(hideBanner)
        banner.animate().alpha(1f).setDuration(200).start()
        handler.postDelayed(hideBanner, 3000)
    }

    private fun showMessage(text: String, long: Boolean = false) {
        messageView.text = text
        messageView.animate().alpha(1f).setDuration(150).start()
        handler.removeCallbacks(hideMessage)
        handler.postDelayed(hideMessage, if (long) 3500 else 2200)
    }

    // ---------------------------------------------------------------- add / clear channels

    private fun showAddChannelsDialog() {
        val dialog = Dialog(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = roundRect(Palette.NIGHT, dp(14).toFloat())
            setPadding(dp(24), dp(24), dp(24), dp(20))
            addView(label("إضافة قنوات", 18f, typeface = TvFonts.medium))
            addView(label("أضف قائمة M3U، والقنوات الجديدة تنضم للموجودة بدون تكرار.", 11f, Palette.MUTED).apply {
                setLineSpacing(0f, 1.4f)
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply {
                topMargin = dp(6)
                bottomMargin = dp(14)
            })
        }
        fun option(icon: IconDrawable.Icon, title: String, subtitle: String, color: Int, action: () -> Unit) =
            dialogOption(icon, title, subtitle, color).also { view ->
                view.setOnClickListener {
                    dialog.dismiss()
                    action()
                }
                content.addView(view, LinearLayout.LayoutParams(MATCH, dp(44)).apply { topMargin = dp(4) })
            }

        option(IconDrawable.Icon.FOLDER, "فتح ملف من الجهاز", "من ذاكرة الشاشة أو فلاشة USB", Palette.TEXT) {
            openM3uFile()
        }.requestFocus()
        option(IconDrawable.Icon.LINK, "تحميل من رابط", "الصق رابط قائمة M3U", Palette.TEXT) {
            showM3uUrlDialog()
        }

        dialog.setContentView(content, ViewGroup.LayoutParams(dp(380), WRAP))
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun dialogOption(icon: IconDrawable.Icon, title: String, subtitle: String, color: Int) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setPaddingRelative(dp(14), 0, dp(14), 0)
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), roundRect(Palette.SAND, dp(9).toFloat()))
                addState(intArrayOf(), roundRect(Color.TRANSPARENT, dp(9).toFloat()))
            }
            addView(ImageView(context).apply {
                isDuplicateParentStateEnabled = true
                setImageDrawable(IconDrawable(icon, colorsOnFocus(color, Palette.INK), dp(16)))
            })
            val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(label(title, 14f).apply {
                isDuplicateParentStateEnabled = true
                setSingleLine(true)
                setTextColor(colorsOnFocus(color, Palette.INK))
            })
            texts.addView(label(subtitle, 9f).apply {
                isDuplicateParentStateEnabled = true
                setTextColor(colorsOnFocus(Palette.MUTED, Palette.INK))
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(3) })
            texts.isDuplicateParentStateEnabled = true
            addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(11) })
        }

    private fun openM3uFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            startActivityForResult(intent, REQUEST_M3U_FILE)
        } catch (_: android.content.ActivityNotFoundException) {
            showMessage("ما فيه تطبيق ملفات على الشاشة، استخدم «تحميل من رابط»", long = true)
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
        showMessage("جاري تحميل القائمة…")

        Thread {
            try {
                val imported = fetchPlaylist(url)
                runOnUiThread { applyImportedChannels(imported) }
            } catch (e: Exception) {
                Log.w(TAG, "Playlist failed: $url", e)
                runOnUiThread {
                    showError("تعذر تحميل القائمة: ${e.message ?: "خطأ غير معروف"}")
                }
            }
        }.start()
    }

    /** Downloads and parses one playlist. Runs on a background thread; throws on failure. */
    private fun fetchPlaylist(url: String): List<Channel> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "SultanTV/2.0")

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code")
            }

            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val channels = if (looksLikeHlsMediaPlaylist(text)) {
                listOf(Channel("قناة مباشرة", url))
            } else {
                parseM3u(text)
            }
            if (channels.isEmpty()) Log.w(TAG, "No channels in $url, starts with: ${text.take(200)}")
            return channels
        } finally {
            connection.disconnect()
        }
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
        var added = 0
        imported.forEach { channel ->
            if (existingUrls.add(channel.url)) {
                channels += channel
                added++
            }
        }
        saveChannels()
        refreshChannelList()
        showMessage("تمت إضافة $added قناة، الإجمالي ${channels.size}", long = true)
    }

    // ---------------------------------------------------------------- storage

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
                // Older versions saved a demo stream alongside the real channels; drop it.
                if (name.isNotBlank() && url.isNotBlank() && url != OLD_DEMO_URL) {
                    channels += Channel(name, url, group)
                }
            }
            if (channels.size != array.length()) saveChannels()
        } catch (_: Exception) {
            channels.clear()
        }
    }

    private fun saveFavorites() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_FAVORITES, favorites.toSet())
            .apply()
    }

    private fun loadFavorites() {
        favorites += getSharedPreferences(PREFS, MODE_PRIVATE)
            .getStringSet(KEY_FAVORITES, emptySet())
            .orEmpty()
    }

    private fun showError(message: String) {
        showMessage(message, long = true)
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
        if (!isOk || !menuOpen || !channelList.hasFocus()) return super.dispatchKeyEvent(event)

        val channel = visibleChannels.getOrNull(channelList.selectedItemPosition)
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
        val now = android.os.SystemClock.uptimeMillis()
        exitPresses = if (now - lastExitPress <= EXIT_CONFIRM_MS) exitPresses + 1 else 1
        lastExitPress = now

        val left = EXIT_PRESSES - exitPresses
        when {
            left <= 0 -> {
                player.stop()
                finishAndRemoveTask()
            }
            left == 1 -> showMessage("Press Exit once more to close")
            else -> showMessage("Press Exit $left more times to close")
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!menuOpen) {
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
            !menuOpen -> showMenu()
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
        handler.removeCallbacksAndMessages(null)
        player.release()
        super.onDestroy()
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(searchField.windowToken, 0)
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
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val REQUEST_M3U_FILE = 7001
        private const val LONG_PRESS_MS = 600L
        private const val OLD_DEMO_URL = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        private const val EXIT_CONFIRM_MS = 2000L
        private const val EXIT_PRESSES = 3
        private val EXIT_KEYS = setOf(KeyEvent.KEYCODE_ESCAPE)
        private const val PREFS = "sultan_tv_prefs"
        private const val KEY_CHANNELS = "channels"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_LAST_URL = "last_url"
        private const val TAG = "SeTV"
        private const val MENU_FADE_DP = 176
        private const val MENU_WIDTH_DP = 570 + MENU_FADE_DP + 14
        private const val KEY_DEFAULT_PLAYLISTS_LOADED = "default_playlists_loaded"
        private val DEFAULT_PLAYLIST_URLS = listOf(
            "https://iptv-org.github.io/iptv/languages/ara.m3u",
            // "https://iptv-org.github.io/iptv/regions/arab.m3u",
            // "https://iptv-org.github.io/iptv/countries/sa.m3u",
            // "https://iptv-org.github.io/iptv/categories/news.m3u"
        )
    }
}
