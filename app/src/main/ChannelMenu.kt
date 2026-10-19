@file:Suppress("PackageDirectoryMismatch")

package com.se.simpletv

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView

/**
 * The channel menu panel: categories on the right, search and channels beside them, remote hints below.
 * It owns what the viewer is browsing (category, search); playback stays with the activity.
 */
class ChannelMenu(
    private val context: Context,
    private val library: ChannelLibrary,
    private val playingUrl: () -> String?,
    private val onChannelSelected: (Channel) -> Unit,
    private val onAddChannels: () -> Unit
) {

    private var categories: List<Category> = emptyList()
    private var activeCategory = 0
    private var query = ""

    /** The channels currently listed; channel up/down while watching moves through these. */
    var visibleChannels: List<Channel> = emptyList()
        private set

    var isOpen = true
        private set

    private val categoryAdapter = CategoryAdapter(context)
    private val channelAdapter = ChannelAdapter(
        context,
        numberOf = library::numberOf,
        isFavorite = library::isFavorite,
        isPlaying = { it.url == playingUrl() }
    )

    private val totalView = context.label(sizeSp = 11f, color = Palette.MUTED)
    private val searchField = buildSearchField()
    private val categoryList = makeList().apply {
        adapter = categoryAdapter
        setOnItemClickListener { _, _, position, _ -> onCategoryClicked(position) }
    }
    private val channelList = makeList().apply {
        adapter = channelAdapter
        setOnItemClickListener { _, _, position, _ -> visibleChannels.getOrNull(position)?.let(onChannelSelected) }
    }
    private val emptyView = context.label(sizeSp = 14f, color = Palette.MUTED).apply {
        setLineSpacing(0f, 1.5f)
        setPadding(context.dp(12), context.dp(40), context.dp(12), 0)
    }
    private val hintsRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, context.dp(12), 0, 0)
    }

    val view: LinearLayout = buildPanel()

    val channelListHasFocus: Boolean get() = channelList.hasFocus()

    /** The channel under the highlight in the channel list. */
    val selectedChannel: Channel? get() = visibleChannels.getOrNull(channelList.selectedItemPosition)

    // ---------------------------------------------------------------- layout

    private fun buildPanel(): LinearLayout {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // The extra end padding is the fade area, so the lists sit clear of it.
            setPaddingRelative(context.dp(36), context.dp(28), context.dp(FADE_DP + 14), context.dp(20))
            background = EdgeFadeDrawable(Palette.night(0.96f), context.dp(FADE_DP))
        }

        val body = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        box.addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // categories column (right side in RTL)
        val categoryColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val brand = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(context.label("SE TV", 23f, typeface = TvFonts.semiBold).apply {
                textDirection = View.TEXT_DIRECTION_LTR
            })
            addView(totalView, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(8) })
        }
        categoryColumn.addView(brand, LinearLayout.LayoutParams(MATCH, context.dp(36)))
        categoryColumn.addView(categoryList, LinearLayout.LayoutParams(MATCH, 0, 1f).apply {
            topMargin = context.dp(16)
        })
        body.addView(categoryColumn, LinearLayout.LayoutParams(context.dp(180), MATCH))

        // channels column
        val channelColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        channelColumn.addView(searchField, LinearLayout.LayoutParams(MATCH, context.dp(36)))
        val listFrame = FrameLayout(context)
        listFrame.addView(channelList, FrameLayout.LayoutParams(MATCH, MATCH))
        listFrame.addView(emptyView, FrameLayout.LayoutParams(MATCH, WRAP))
        channelList.emptyView = emptyView
        channelColumn.addView(listFrame, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = context.dp(10) })
        body.addView(channelColumn, LinearLayout.LayoutParams(0, MATCH, 1f).apply { marginStart = context.dp(20) })

        // explicit remote paths between the three focus zones
        listOf(searchField, categoryList, channelList).forEach { it.id = View.generateViewId() }
        channelList.nextFocusRightId = categoryList.id
        channelList.nextFocusUpId = searchField.id
        searchField.nextFocusDownId = channelList.id
        searchField.nextFocusRightId = categoryList.id
        categoryList.nextFocusLeftId = channelList.id

        box.addView(hintsRow, LinearLayout.LayoutParams(MATCH, WRAP))
        return box
    }

    private fun makeList() = ListView(context).apply {
        divider = null
        selector = ColorDrawable(Color.TRANSPARENT)
        cacheColorHint = Color.TRANSPARENT
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        isVerticalFadingEdgeEnabled = true
        setFadingEdgeLength(context.dp(24))
        clipToPadding = false
        isFocusable = true
        itemsCanFocus = false
    }

    private fun buildSearchField() = EditText(context).apply {
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
        setPaddingRelative(context.dp(14), 0, context.dp(14), 0)
        compoundDrawablePadding = context.dp(8)
        setCompoundDrawablesRelativeWithIntrinsicBounds(
            IconDrawable(IconDrawable.Icon.SEARCH, colorsOnFocus(Palette.MUTED), context.dp(15)), null, null, null
        )
        background = StateListDrawable().apply {
            val radius = context.dp(18).toFloat()
            addState(
                intArrayOf(android.R.attr.state_focused),
                roundRect(Palette.NIGHT_RAISED, radius, context.dp(1.5f), Palette.SAND)
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

    /** Remote hints for whichever part of the menu has focus. */
    fun updateHints(focused: View?) {
        val back = if (playingUrl() != null) "متابعة المشاهدة" else "خروج"
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
            val chip = context.label(key, 8.5f, typeface = TvFonts.medium).apply {
                textDirection = View.TEXT_DIRECTION_LTR
                gravity = Gravity.CENTER
                minWidth = context.dp(20)
                setPadding(context.dp(5), context.dp(3), context.dp(5), context.dp(3))
                background = roundRect(
                    Color.TRANSPARENT, context.dp(5).toFloat(), context.dp(0.75f).coerceAtLeast(1), Palette.FAINT
                )
            }
            hintsRow.addView(chip, LinearLayout.LayoutParams(WRAP, WRAP).apply {
                if (i > 0) marginStart = context.dp(22)
            })
            hintsRow.addView(context.label(text, 10f, Palette.MUTED), LinearLayout.LayoutParams(WRAP, WRAP).apply {
                marginStart = context.dp(6)
            })
        }
    }

    // ---------------------------------------------------------------- state

    /** Call after the library's channels or favorites change. */
    fun refresh(keepPosition: Boolean = false) {
        totalView.text = "${library.channels.size} قناة"
        rebuildCategories()
        applyFilter(keepPosition)
    }

    /** Call after playback moved to another channel, so the "now" marker and hints follow. */
    fun onPlayingChanged(focused: View?) {
        channelAdapter.notifyDataSetChanged()
        updateHints(focused)
    }

    private fun rebuildCategories() {
        val previous = categories.getOrNull(activeCategory)
        categories = library.categories()
        activeCategory = categories.indexOfFirst { it.kind == previous?.kind && it.group == previous.group }
            .takeIf { it >= 0 } ?: 0
        categoryAdapter.items = categories
        categoryAdapter.activePosition = activeCategory
    }

    private fun applyFilter(keepPosition: Boolean = false) {
        val category = categories.getOrNull(activeCategory)
        visibleChannels = library.filter(category, query)
        val position = channelList.selectedItemPosition
        channelAdapter.items = visibleChannels
        emptyView.text = if (library.channels.isEmpty()) {
            "ما فيه قنوات بعد.\nاختر «إضافة قنوات» من آخر قائمة التصنيفات."
        } else if (category?.kind == Category.Kind.FAVORITES && query.isEmpty()) {
            "ما عندك قنوات مفضلة.\nوقف على أي قناة واضغط OK ضغطة طويلة عشان تضيفها هنا."
        } else {
            "ما فيه قناة اسمها «$query» في هذا التصنيف.\nجرّب «كل القنوات» أو اكتب جزء من الاسم."
        }
        channelList.setSelection(
            if (keepPosition) position.coerceIn(0, (visibleChannels.size - 1).coerceAtLeast(0)) else 0
        )
    }

    private fun onCategoryClicked(position: Int) {
        val category = categories.getOrNull(position) ?: return
        if (category.kind == Category.Kind.ADD) {
            onAddChannels()
            return
        }
        activeCategory = position
        categoryAdapter.activePosition = position
        applyFilter()
        if (visibleChannels.isNotEmpty()) channelList.requestFocus()
    }

    // ---------------------------------------------------------------- open / close

    /** Opens the panel with the playing channel centred and focused. */
    fun show(focused: () -> View?) {
        isOpen = true
        view.visibility = View.VISIBLE
        view.animate().alpha(1f).translationX(0f).setDuration(220).start()

        channelAdapter.notifyDataSetChanged()
        categoryList.setSelection(activeCategory)
        val playingIndex = visibleChannels.indexOfFirst { it.url == playingUrl() }
        if (playingIndex >= 0) {
            channelList.post {
                channelList.setSelectionFromTop(
                    playingIndex, ((channelList.height - context.dp(48)) / 2).coerceAtLeast(0)
                )
            }
        }
        if (visibleChannels.isNotEmpty()) channelList.requestFocus() else categoryList.requestFocus()
        updateHints(focused())
    }

    fun hide() {
        isOpen = false
        hideKeyboard()
        view.animate().alpha(0f).translationX(context.dp(40).toFloat()).setDuration(220)
            .withEndAction { if (!isOpen) view.visibility = View.GONE }
            .start()
    }

    private fun hideKeyboard() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(searchField.windowToken, 0)
    }

    companion object {
        private const val FADE_DP = 176

        /** Panel width: 570dp of content and padding plus the fade on its left edge. */
        const val WIDTH_DP = 570 + FADE_DP + 14
    }
}
