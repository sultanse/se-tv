@file:Suppress("PackageDirectoryMismatch")

package com.se.simpletv

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.StateListAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

object Palette {
    const val NIGHT = 0xFF16122B.toInt()
    const val NIGHT_RAISED = 0xFF241D40.toInt()
    const val SAND = 0xFFF2C46D.toInt()
    const val INK = 0xFF1B1530.toInt()
    const val TEXT = 0xFFF4EFE6.toInt()
    const val MUTED = 0xFFA79FBF.toInt()
    const val FAINT = 0xFF6F6790.toInt()
    const val LIVE = 0xFFFF5D52.toInt()
    const val LIVE_ON_SAND = 0xFFB3241B.toInt()
    const val WARN = 0xFFD9A25E.toInt()

    fun night(alpha: Float): Int = Color.argb((alpha * 255).toInt(), 0x16, 0x12, 0x2B)
}

/** The TV's own system font, in the three weights the menu uses. */
object TvFonts {
    val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val medium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val semiBold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
}

fun Context.dp(value: Number): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

fun Context.label(
    text: CharSequence = "",
    sizeSp: Float,
    color: Int = Palette.TEXT,
    typeface: Typeface = TvFonts.regular
): TextView = TextView(this).apply {
    this.text = text
    textSize = sizeSp
    setTextColor(color)
    this.typeface = typeface
    includeFontPadding = false
}

/** Text that turns to [selected] while its row is the focused item. */
fun colorsOnSelect(normal: Int, selected: Int = Palette.INK): ColorStateList = ColorStateList(
    arrayOf(
        intArrayOf(android.R.attr.state_selected),
        intArrayOf(android.R.attr.state_pressed),
        intArrayOf()
    ),
    intArrayOf(selected, selected, normal)
)

fun colorsOnFocus(normal: Int, focused: Int = Palette.SAND): ColorStateList = ColorStateList(
    arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
    intArrayOf(focused, normal)
)

fun roundRect(color: Int, radius: Float, strokeWidth: Int = 0, strokeColor: Int = Color.TRANSPARENT) =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
    }

fun Context.rowBackground(radius: Float, normal: Int = Color.TRANSPARENT): StateListDrawable =
    StateListDrawable().apply {
        val focused = roundRect(Palette.SAND, radius)
        addState(intArrayOf(android.R.attr.state_selected), focused)
        addState(intArrayOf(android.R.attr.state_pressed), focused)
        addState(intArrayOf(), roundRect(normal, radius))
    }

fun scaleOnSelect(scale: Float): StateListAnimator = StateListAnimator().apply {
    fun to(value: Float) = ObjectAnimator.ofPropertyValuesHolder(
        null as Any?,
        PropertyValuesHolder.ofFloat(View.SCALE_X, value),
        PropertyValuesHolder.ofFloat(View.SCALE_Y, value)
    ).setDuration(120)
    addState(intArrayOf(android.R.attr.state_selected), to(scale))
    addState(intArrayOf(), to(1f))
}

/**
 * A solid panel colour that fades out over [fadeWidthPx] towards its left edge.
 * The fade follows a smoothstep curve instead of a straight line, so neither end of it
 * shows as an edge over the video.
 */
class EdgeFadeDrawable(private val color: Int, private val fadeWidthPx: Int) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)

    override fun onBoundsChange(bounds: Rect) {
        val steps = 24
        val positions = FloatArray(steps + 1) { it / steps.toFloat() }
        val colors = IntArray(steps + 1) { i ->
            val t = positions[i]
            val eased = t * t * (3 - 2 * t)
            Color.argb((Color.alpha(color) * eased).toInt(), Color.red(color), Color.green(color), Color.blue(color))
        }
        val left = bounds.left.toFloat()
        // CLAMP keeps the last stop (the solid colour) for everything right of the fade.
        paint.shader = LinearGradient(left, 0f, left + fadeWidthPx, 0f, colors, positions, Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Small line icons drawn on a 24×24 grid, so the app needs no image assets. */
class IconDrawable(
    private val icon: Icon,
    private val colors: ColorStateList,
    private val sizePx: Int
) : Drawable() {

    enum class Icon { SEARCH, PLUS, STAR, FOLDER, LINK }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = colors.defaultColor
    }
    private val path = Path()

    override fun getIntrinsicWidth() = sizePx
    override fun getIntrinsicHeight() = sizePx
    override fun isStateful() = colors.isStateful

    override fun onStateChange(state: IntArray): Boolean {
        val next = colors.getColorForState(state, colors.defaultColor)
        if (next == paint.color) return false
        paint.color = next
        invalidateSelf()
        return true
    }

    override fun draw(canvas: Canvas) {
        val unit = bounds.width() / 24f
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(unit, unit)
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        path.reset()
        when (icon) {
            Icon.SEARCH -> {
                canvas.drawCircle(11f, 11f, 7f, paint)
                canvas.drawLine(16.5f, 16.5f, 20f, 20f, paint)
            }
            Icon.PLUS -> {
                paint.strokeWidth = 2.4f
                canvas.drawLine(12f, 5f, 12f, 19f, paint)
                canvas.drawLine(5f, 12f, 19f, 12f, paint)
            }
            Icon.STAR -> {
                val points = floatArrayOf(
                    12f, 3f, 14.8f, 8.8f, 21f, 9.7f, 16.5f, 14.1f, 17.5f, 20.4f,
                    12f, 17.4f, 6.5f, 20.4f, 7.5f, 14.1f, 3f, 9.7f, 9.2f, 8.8f
                )
                path.moveTo(points[0], points[1])
                for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
                path.close()
                paint.style = Paint.Style.FILL
                canvas.drawPath(path, paint)
            }
            Icon.FOLDER -> {
                path.moveTo(4f, 6f)
                path.lineTo(10f, 6f)
                path.lineTo(12f, 8.5f)
                path.lineTo(20f, 8.5f)
                path.lineTo(20f, 19f)
                path.lineTo(4f, 19f)
                path.close()
                canvas.drawPath(path, paint)
            }
            Icon.LINK -> {
                canvas.rotate(-45f, 12f, 12f)
                canvas.drawRoundRect(2.5f, 9f, 13f, 15f, 3f, 3f, paint)
                canvas.drawRoundRect(11f, 9f, 21.5f, 15f, 3f, 3f, paint)
            }
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Android")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
