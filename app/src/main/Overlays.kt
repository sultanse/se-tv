@file:Suppress("PackageDirectoryMismatch")

package com.se.simpletv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Number, name and groups shown at the top for a few seconds after a channel starts. */
class ChannelBanner(context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val autoHide = Runnable { hide(200) }

    private val number = context.label(sizeSp = 26f, color = Palette.SAND, typeface = TvFonts.semiBold).apply {
        fontFeatureSettings = "tnum"
    }
    private val name = context.label(sizeSp = 18f, typeface = TvFonts.medium).apply {
        maxWidth = context.dp(420)
        setSingleLine(true)
        ellipsize = TextUtils.TruncateAt.END
    }
    private val meta = context.label(sizeSp = 10f, color = Palette.MUTED)

    val view = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = roundRect(Palette.night(0.92f), context.dp(11).toFloat())
        setPaddingRelative(context.dp(18), context.dp(11), context.dp(22), context.dp(11))
        alpha = 0f
        addView(number)
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        // WRAP, not the vertical LinearLayout default MATCH_PARENT, or the name shrinks to the meta line's width.
        texts.addView(name, LinearLayout.LayoutParams(WRAP, WRAP))
        texts.addView(meta, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = context.dp(4) })
        addView(texts, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(14) })
    }

    fun show(channel: Channel, channelNumber: Int) {
        number.text = channelNumber.toString()
        name.text = channel.title
        meta.text = listOf(channel.groupsLabel, channel.quality)
            .filter { it.isNotEmpty() }
            .joinToString("   ")
        handler.removeCallbacks(autoHide)
        view.animate().alpha(1f).setDuration(200).start()
        handler.postDelayed(autoHide, 3000)
    }

    fun hide(durationMs: Long = 150) {
        handler.removeCallbacks(autoHide)
        view.animate().alpha(0f).setDuration(durationMs).start()
    }

    fun release() = handler.removeCallbacksAndMessages(null)
}

/** "Now playing" card at the bottom left while the menu is open. */
class NowPlayingCard(context: Context) {

    private val name = context.label(sizeSp = 20f, typeface = TvFonts.medium).apply {
        maxWidth = context.dp(230)
        setSingleLine(true)
        ellipsize = TextUtils.TruncateAt.END
    }

    val view = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = roundRect(Palette.night(0.88f), context.dp(11).toFloat())
        setPadding(context.dp(16), context.dp(11), context.dp(16), context.dp(11))
        addView(context.liveLabel("يُعرض الآن", 10f, dotDp = 6, gapDp = 5))
        addView(name, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = context.dp(4) })
    }

    fun setChannel(channel: Channel) {
        name.text = channel.title
    }

    fun show(fadeIn: Boolean) {
        view.visibility = View.VISIBLE
        if (fadeIn) {
            view.alpha = 0f
            view.animate().alpha(1f).setDuration(220).start()
        } else {
            view.alpha = 1f
        }
    }

    fun hide() {
        view.visibility = View.GONE
    }
}

/** Short message pill at the bottom of the screen, the app's own toast. */
class MessageToast(context: Context) {

    private val handler = Handler(Looper.getMainLooper())

    val view: TextView = context.label(sizeSp = 12f, color = Palette.INK, typeface = TvFonts.medium).apply {
        background = roundRect(Palette.TEXT, context.dp(20).toFloat())
        setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(8))
        alpha = 0f
    }

    private val hide = Runnable { view.animate().alpha(0f).setDuration(200).start() }

    fun show(text: String, long: Boolean = false) {
        view.text = text
        view.animate().alpha(1f).setDuration(150).start()
        handler.removeCallbacks(hide)
        handler.postDelayed(hide, if (long) 3500 else 2200)
    }

    fun release() = handler.removeCallbacksAndMessages(null)
}
