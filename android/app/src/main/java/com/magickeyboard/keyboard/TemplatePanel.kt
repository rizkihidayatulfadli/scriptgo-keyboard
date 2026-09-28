package com.magickeyboard.keyboard

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.magickeyboard.manager.AppLanguage
import com.magickeyboard.manager.data.Template
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors

/**
 * Holding the space bar opens [TemplatePanel]. PointerTracker (HeliBoard) reads the hold time here
 * and calls [trigger] from its long-press handler; the values come from the manager's settings.
 */
object SpaceHold {
    fun interface Opener {
        /** Opens the template panel; false when it can't (password field, no keyboard view). */
        fun open(): Boolean
    }

    const val DEFAULT_MS = 1000
    const val MIN_MS = 500
    const val MAX_MS = 3000

    @JvmField @Volatile var enabled = true
    @JvmField @Volatile var delayMs = DEFAULT_MS
    @JvmField @Volatile var opener: Opener? = null

    /** Long-press timeout of the space bar: the configured hold time, or [fallback] when off. */
    @JvmStatic
    fun timeoutOr(fallback: Int): Int = if (enabled && opener != null) delayMs else fallback

    /** Called when the space bar was held long enough; true when the panel opened. */
    @JvmStatic
    fun trigger(): Boolean = enabled && opener?.open() == true
}

/**
 * The template panel: covers the keys with the user's templates as key-like tiles. Opening
 * fades the keys out while the tiles pop in one after another, as if the keys turned into
 * templates; closing reverses it. Picking a tile inserts the template at the cursor (or opens the
 * variable form first), through the same path as the suggestion strip.
 */
class TemplatePanel(private val context: Context, private val suggestions: TemplateSuggestions) {
    private var panel: View? = null
    private var keys: View? = null

    val isShowing: Boolean get() = panel != null

    /** Shows the panel over [keyboardView] inside [wrapper]; returns false when nothing was shown. */
    fun show(wrapper: ViewGroup, keyboardView: View?, editorInfo: EditorInfo?, colors: Colors, inserter: TemplateInserter): Boolean {
        if (panel != null) return true
        if (keyboardView == null || keyboardView.height == 0 || suggestions.isOwnEditorForPanel(editorInfo)) return false
        val templates = suggestions.templatesByUse()
        val view = build(templates, colors, keyboardView.height, inserter)
        wrapper.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, keyboardView.height))
        panel = view
        keys = keyboardView
        animateIn(view, keyboardView)
        return true
    }

    /** Removes the panel and brings the keys back; [animate] false for an instant reset. */
    fun hide(animate: Boolean = true) {
        val view = panel ?: return
        val keyboardView = keys
        panel = null
        keys = null
        val remove = { (view.parent as? ViewGroup)?.removeView(view) }
        keyboardView?.let {
            it.animate().cancel()
            it.visibility = View.VISIBLE
        }
        if (!animate) {
            keyboardView?.apply { alpha = 1f; scaleX = 1f; scaleY = 1f }
            remove()
            return
        }
        keyboardView?.animate()?.alpha(1f)?.scaleX(1f)?.scaleY(1f)?.setDuration(180)?.setInterpolator(DecelerateInterpolator())?.setListener(null)?.start()
        view.animate().alpha(0f).translationY(dp(12f).toFloat()).setDuration(150).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) { remove() }
        }).start()
    }

    private fun build(templates: List<Template>, colors: Colors, height: Int, inserter: TemplateInserter): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true // keeps touches from reaching the keys underneath
            colors.setBackground(this, ColorType.MAIN_BACKGROUND)
        }
        root.addView(header(colors), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44f)))

        if (templates.isEmpty()) {
            root.addView(TextView(context).apply {
                text = localized().getString(R.string.sg_panel_empty)
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTextColor(colors.get(ColorType.KEY_HINT_TEXT))
                KeyboardTypeface.applyToTextView(this)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            return root
        }

        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        list.setPadding(dp(4f), 0, dp(4f), dp(6f))
        templates.chunked(COLUMNS).forEachIndexed { rowIndex, row ->
            val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEachIndexed { column, template ->
                val tile = tile(template, colors) {
                    hide(animate = false)
                    suggestions.insertFromPanel(template, inserter)
                }
                tile.tag = rowIndex * COLUMNS + column
                line.addView(tile, LinearLayout.LayoutParams(0, dp(58f), 1f).apply { setMargins(dp(3f), dp(3f), dp(3f), dp(3f)) })
            }
            // keeps a lone last tile at half width, like the others
            repeat(COLUMNS - row.size) { line.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f)) }
            list.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(list)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.minimumHeight = height
        return root
    }

    private fun header(colors: Colors): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.setPadding(dp(14f), 0, dp(6f), 0)
        row.addView(TextView(context).apply {
            text = localized().getString(R.string.sg_panel_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            KeyboardTypeface.applyToTextView(this)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(colors.get(ColorType.KEY_TEXT))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(context).apply {
            text = "ABC"
            gravity = Gravity.CENTER
            contentDescription = localized().getString(R.string.sg_panel_back)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            KeyboardTypeface.applyToTextView(this)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(colors.get(ColorType.KEY_TEXT))
            setPadding(dp(16f), 0, dp(16f), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(16f).toFloat()
                setColor(colors.get(ColorType.FUNCTIONAL_KEY_BACKGROUND))
            }
            setOnClickListener { hide() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32f)))
        return row
    }

    /** A key-shaped tile: the template's name in bold and the start of its text below. */
    private fun tile(template: Template, colors: Colors, onClick: () -> Unit): View {
        val tile = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), 0, dp(12f), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(10f).toFloat()
                setColor(colors.get(ColorType.KEY_BACKGROUND))
            }
            setOnClickListener { onClick() }
        }
        tile.addView(TextView(context).apply {
            text = template.title.ifBlank { template.shortcut }
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            KeyboardTypeface.applyToTextView(this)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(colors.get(ColorType.KEY_TEXT))
        })
        tile.addView(TextView(context).apply {
            text = template.body.replace('\n', ' ')
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            KeyboardTypeface.applyToTextView(this)
            setTextColor(colors.get(ColorType.KEY_HINT_TEXT))
        })
        return tile
    }

    /** Keys shrink and fade while the tiles pop in with a short cascade, row by row. */
    private fun animateIn(view: View, keyboardView: View) {
        keyboardView.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f).setDuration(170)
            .setInterpolator(DecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (keys === keyboardView) keyboardView.visibility = View.INVISIBLE
                }
            }).start()
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(140).setListener(null).start()
        forEachTile(view) { tile, index ->
            val row = index / COLUMNS
            val column = index % COLUMNS
            tile.alpha = 0f
            tile.scaleX = 0.6f
            tile.scaleY = 0.6f
            tile.translationY = dp(18f).toFloat()
            tile.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setStartDelay(60L + (row * 55L + column * 30L).coerceAtMost(400L))
                .setDuration(260)
                .setInterpolator(OvershootInterpolator(1.3f))
                .start()
        }
    }

    private fun forEachTile(view: View, action: (View, Int) -> Unit) {
        if (view.tag is Int) {
            action(view, view.tag as Int)
            return
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) forEachTile(view.getChildAt(i), action)
    }

    /** Resources in the ScriptGo app language (the keyboard's own context follows the phone). */
    private fun localized(): Context = AppLanguage.wrap(context)

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    private companion object {
        const val COLUMNS = 2
    }
}
