@file:Suppress("PackageDirectoryMismatch")

package com.se.simpletv

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

/** A list adapter over [items] that redraws whenever they are replaced. */
abstract class ItemsAdapter<T> : BaseAdapter() {

    var items: List<T> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()
}

class CategoryAdapter(private val context: Context) : ItemsAdapter<Category>() {

    var activePosition = 0
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getViewTypeCount() = 2
    override fun getItemViewType(position: Int) = if (items[position].kind == Category.Kind.SEPARATOR) 1 else 0
    override fun areAllItemsEnabled() = false
    override fun isEnabled(position: Int) = items[position].kind != Category.Kind.SEPARATOR

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = items[position]
        if (item.kind == Category.Kind.SEPARATOR) {
            return convertView ?: FrameLayout(context).apply {
                setPadding(context.dp(12), context.dp(6), context.dp(12), context.dp(6))
                addView(View(context).apply { setBackgroundColor(Color.argb(46, 167, 159, 191)) },
                    FrameLayout.LayoutParams(MATCH, context.dp(1)))
            }
        }

        val holder = (convertView?.tag as? Holder) ?: Holder()
        val isAdd = item.kind == Category.Kind.ADD
        val active = position == activePosition && !isAdd
        holder.row.background = if (active) holder.activeBackground else holder.normalBackground
        holder.bar.visibility = if (active) View.VISIBLE else View.INVISIBLE
        holder.name.text = item.label
        holder.name.setTextColor(
            when {
                active -> ACTIVE_TEXT
                isAdd -> ADD_TEXT
                else -> NAME_TEXT
            }
        )
        holder.count.visibility = if (isAdd) View.GONE else View.VISIBLE
        holder.count.text = item.count.toString()
        holder.count.setTextColor(if (active) ACTIVE_TEXT else COUNT_TEXT)
        holder.plus.visibility = if (isAdd) View.VISIBLE else View.GONE
        return holder.root
    }

    private inner class Holder {
        // One pair per row: a drawable holds its view's state, so rows can't share one.
        val normalBackground = rowBackground(context.dp(7).toFloat())
        val activeBackground = rowBackground(context.dp(7).toFloat(), Color.argb(15, 242, 196, 109))

        val bar = View(context).apply { background = roundRect(Palette.SAND, context.dp(2).toFloat()) }
        val name = context.label(sizeSp = 13f)
        val count = context.label(sizeSp = 10f).apply { fontFeatureSettings = "tnum" }
        val plus = ImageView(context).apply {
            setImageDrawable(IconDrawable(IconDrawable.Icon.PLUS, colorsOnSelect(Palette.TEXT), context.dp(13)))
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(0, 0, context.dp(12), 0)
            stateListAnimator = scaleOnSelect(1.03f)
            addView(bar, LinearLayout.LayoutParams(context.dp(2.5f), MATCH))
            addView(name, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = context.dp(10) })
            addView(count)
            addView(plus)
        }
        val root = context.rowFrame(row, heightDp = 32, padHDp = 4, padVDp = 2, holder = this)
    }

    private companion object {
        val ACTIVE_TEXT = colorsOnSelect(Palette.SAND)
        val ADD_TEXT = colorsOnSelect(Palette.TEXT)
        val NAME_TEXT = colorsOnSelect(Palette.MUTED)
        val COUNT_TEXT = colorsOnSelect(Palette.FAINT)
    }
}

class ChannelAdapter(
    private val context: Context,
    private val numberOf: (Channel) -> Int,
    private val isFavorite: (Channel) -> Boolean,
    private val isPlaying: (Channel) -> Boolean
) : ItemsAdapter<Channel>() {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val channel = items[position]
        val holder = (convertView?.tag as? Holder) ?: Holder()

        holder.number.text = numberOf(channel).toString()
        holder.name.text = channel.title
        holder.now.visibility = if (isPlaying(channel)) View.VISIBLE else View.GONE
        holder.warning.visibility = if (channel.geoBlocked || channel.notAlwaysOn) View.VISIBLE else View.GONE
        holder.warning.text = if (channel.geoBlocked) "قد يكون محجوب" else "بث متقطع"
        holder.groups.text = channel.groupsLabel
        holder.quality.visibility = if (channel.quality.isEmpty()) View.GONE else View.VISIBLE
        holder.quality.text = channel.quality
        holder.star.visibility = if (isFavorite(channel)) View.VISIBLE else View.GONE
        return holder.root
    }

    private inner class Holder {
        val number = context.label(sizeSp = 11f).apply {
            setTextColor(colorsOnSelect(Palette.FAINT))
            fontFeatureSettings = "tnum"
        }
        val name = context.label(sizeSp = 15f, typeface = TvFonts.medium).apply {
            setTextColor(colorsOnSelect(Palette.TEXT))
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        val now = context.liveLabel(
            "الآن", 9.5f, dotDp = 5, gapDp = 4, colors = colorsOnSelect(Palette.LIVE, Palette.LIVE_ON_SAND)
        )
        val warning = context.label(sizeSp = 9.5f).apply { setTextColor(colorsOnSelect(Palette.WARN)) }
        val groups = context.label(sizeSp = 9.5f).apply { setTextColor(colorsOnSelect(Palette.MUTED)) }
        val quality = context.label(sizeSp = 8f, typeface = TvFonts.medium).apply {
            val colors = colorsOnSelect(Palette.MUTED)
            setTextColor(colors)
            textDirection = View.TEXT_DIRECTION_LTR
            setPadding(context.dp(4), context.dp(2), context.dp(4), context.dp(2))
            background = GradientDrawable().apply {
                cornerRadius = context.dp(3).toFloat()
                setStroke(context.dp(0.75f).coerceAtLeast(1), colors)
            }
        }
        val star = ImageView(context).apply {
            setImageDrawable(IconDrawable(IconDrawable.Icon.STAR, colorsOnSelect(Palette.SAND), context.dp(13)))
        }
        val meta = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            listOf(now, warning, groups, quality, star).forEachIndexed { i, view ->
                addView(view, LinearLayout.LayoutParams(WRAP, WRAP).apply { if (i > 0) marginStart = context.dp(6) })
            }
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(context.dp(14), 0, context.dp(12), 0)
            background = rowBackground(context.dp(8).toFloat())
            stateListAnimator = scaleOnSelect(1.025f)
            addView(number, LinearLayout.LayoutParams(context.dp(38), WRAP))
            addView(name, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = context.dp(12) })
            addView(meta)
        }
        val root = context.rowFrame(row, heightDp = 42, padHDp = 6, padVDp = 3, holder = this)
    }
}

/** Wraps a list row with room around it for its scale-up when selected, and tags it with its [holder]. */
private fun Context.rowFrame(row: View, heightDp: Int, padHDp: Int, padVDp: Int, holder: Any) =
    FrameLayout(this).apply {
        setPadding(dp(padHDp), dp(padVDp), dp(padHDp), dp(padVDp))
        clipToPadding = false
        addView(row, FrameLayout.LayoutParams(MATCH, dp(heightDp)))
        tag = holder
    }
