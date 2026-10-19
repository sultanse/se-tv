@file:Suppress("PackageDirectoryMismatch")

package com.se.simpletv

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout

/** "Add channels" picker: open an M3U file from the TV or USB, or type a playlist link. */
class AddChannelsDialog(
    private val context: Context,
    private val onOpenFile: () -> Unit,
    private val onLoadUrl: (String) -> Unit
) {

    fun show() {
        val dialog = Dialog(context)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = roundRect(Palette.NIGHT, context.dp(14).toFloat())
            setPadding(context.dp(24), context.dp(24), context.dp(24), context.dp(20))
            addView(context.label("إضافة قنوات", 18f, typeface = TvFonts.medium))
            addView(context.label("أضف قائمة M3U، والقنوات الجديدة تنضم للموجودة بدون تكرار.", 11f, Palette.MUTED).apply {
                setLineSpacing(0f, 1.4f)
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply {
                topMargin = context.dp(6)
                bottomMargin = context.dp(14)
            })
        }
        fun option(icon: IconDrawable.Icon, title: String, subtitle: String, action: () -> Unit) =
            optionView(icon, title, subtitle).also { view ->
                view.setOnClickListener {
                    dialog.dismiss()
                    action()
                }
                content.addView(view, LinearLayout.LayoutParams(MATCH, context.dp(44)).apply {
                    topMargin = context.dp(4)
                })
            }

        option(IconDrawable.Icon.FOLDER, "فتح ملف من الجهاز", "من ذاكرة الشاشة أو فلاشة USB", onOpenFile)
            .requestFocus()
        option(IconDrawable.Icon.LINK, "تحميل من رابط", "الصق رابط قائمة M3U", ::showUrlInput)

        dialog.setContentView(content, ViewGroup.LayoutParams(context.dp(380), WRAP))
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun showUrlInput() {
        val input = EditText(context).apply {
            hint = "https://example.com/list.m3u"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
        }

        AlertDialog.Builder(context)
            .setTitle("رابط قائمة M3U")
            .setView(input)
            .setPositiveButton("تحميل") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotBlank()) onLoadUrl(url)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun optionView(icon: IconDrawable.Icon, title: String, subtitle: String) =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setPaddingRelative(context.dp(14), 0, context.dp(14), 0)
            val radius = context.dp(9).toFloat()
            background = focusBackground(roundRect(Palette.SAND, radius), roundRect(Color.TRANSPARENT, radius))
            addView(ImageView(context).apply {
                isDuplicateParentStateEnabled = true
                setImageDrawable(IconDrawable(icon, colorsOnFocus(Palette.TEXT, Palette.INK), context.dp(16)))
            })
            val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(context.label(title, 14f).apply {
                isDuplicateParentStateEnabled = true
                setSingleLine(true)
                setTextColor(colorsOnFocus(Palette.TEXT, Palette.INK))
            })
            texts.addView(context.label(subtitle, 9f).apply {
                isDuplicateParentStateEnabled = true
                setTextColor(colorsOnFocus(Palette.MUTED, Palette.INK))
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = context.dp(3) })
            texts.isDuplicateParentStateEnabled = true
            addView(texts, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = context.dp(11) })
        }
}
