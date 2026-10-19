package com.sultan.simpletv

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
import android.widget.TextView

data class Category(
    val kind: Kind,
    val label: String = "",
    val count: Int = 0,
    val group: String = ""
) {
    enum class Kind { ALL, FAVORITES, GROUP, SEPARATOR, ADD }
}

class CategoryAdapter(private val context: Context) : BaseAdapter() {

    var items: List<Category> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var activePosition = 0
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()
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
                    FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, context.dp(1)))
            }
        }

        val holder = (convertView?.tag as? Holder) ?: Holder()
        val active = position == activePosition && item.kind != Category.Kind.ADD
        holder.row.background = context.rowBackground(
            context.dp(7).toFloat(),
            if (active) Color.argb(15, 242, 196, 109) else Color.TRANSPARENT
        )
        holder.bar.visibility = if (active) View.VISIBLE else View.INVISIBLE
        holder.name.text = item.label
        holder.name.setTextColor(
            colorsOnSelect(
                when {
                    active -> Palette.SAND
                    item.kind == Category.Kind.ADD -> Palette.TEXT
                    else -> Palette.MUTED
                }
            )
        )
        holder.count.visibility = if (item.kind == Category.Kind.ADD) View.GONE else View.VISIBLE
        holder.count.text = item.count.toString()
        holder.count.setTextColor(colorsOnSelect(if (active) Palette.SAND else Palette.FAINT))
        holder.plus.visibility = if (item.kind == Category.Kind.ADD) View.VISIBLE else View.GONE
        return holder.root
    }

    private inner class Holder {
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
            addView(bar, LinearLayout.LayoutParams(context.dp(2.5f), LinearLayout.LayoutParams.MATCH_PARENT))
            addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = context.dp(10)
            })
            addView(count)
            addView(plus)
        }
        val root = FrameLayout(context).apply {
            setPadding(context.dp(4), context.dp(2), context.dp(4), context.dp(2))
            clipToPadding = false
            addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, context.dp(32)))
            tag = this@Holder
        }
    }
}

class ChannelAdapter(
    private val context: Context,
    private val numberOf: (Channel) -> Int,
    private val isFavorite: (Channel) -> Boolean,
    private val isPlaying: (Channel) -> Boolean
) : BaseAdapter() {

    var items: List<Channel> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val channel = items[position]
        val holder = (convertView?.tag as? Holder) ?: Holder()

        holder.number.text = numberOf(channel).toString()
        holder.name.text = channel.title
        holder.now.visibility = if (isPlaying(channel)) View.VISIBLE else View.GONE
        holder.warning.visibility = if (channel.geoBlocked || channel.notAlwaysOn) View.VISIBLE else View.GONE
        holder.warning.text = if (channel.geoBlocked) "قد يكون محجوب" else "بث متقطع"
        holder.groups.text = channel.groups.joinToString("، ") { groupLabel(it) }
        holder.quality.visibility = if (channel.quality.isEmpty()) View.GONE else View.VISIBLE
        holder.quality.text = channel.quality
        holder.star.visibility = if (isFavorite(channel)) View.VISIBLE else View.GONE
        return holder.root
    }

    private inner class Holder {
        val number = context.label(sizeSp = 11f, typeface = TvFonts.regular).apply {
            setTextColor(colorsOnSelect(Palette.FAINT))
            fontFeatureSettings = "tnum"
        }
        val name = context.label(sizeSp = 15f, typeface = TvFonts.medium).apply {
            setTextColor(colorsOnSelect(Palette.TEXT))
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        val now = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colorsOnSelect(Palette.LIVE, Palette.LIVE_ON_SAND))
                }
            }, LinearLayout.LayoutParams(context.dp(5), context.dp(5)))
            addView(context.label("الآن", 9.5f, typeface = TvFonts.medium).apply {
                setTextColor(colorsOnSelect(Palette.LIVE, Palette.LIVE_ON_SAND))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = context.dp(4)
            })
        }
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
                addView(view, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (i > 0) marginStart = context.dp(6) })
            }
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(context.dp(14), 0, context.dp(12), 0)
            background = context.rowBackground(context.dp(8).toFloat())
            stateListAnimator = scaleOnSelect(1.025f)
            addView(number, LinearLayout.LayoutParams(context.dp(38), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = context.dp(12)
            })
            addView(meta)
        }
        val root = FrameLayout(context).apply {
            setPadding(context.dp(6), context.dp(3), context.dp(6), context.dp(3))
            clipToPadding = false
            addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, context.dp(42)))
            tag = this@Holder
        }
    }
}
