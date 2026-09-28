package com.magickeyboard.keyboard

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.inputmethod.EditorInfo
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.magickeyboard.manager.AppLanguage
import com.magickeyboard.manager.data.ManagerRepository
import com.magickeyboard.manager.data.Template
import com.magickeyboard.plan.FreeLimits
import com.magickeyboard.sync.SupabaseSync
import com.magickeyboard.template.TemplateEntry
import com.magickeyboard.template.TemplateParser
import com.magickeyboard.template.TemplateRenderer
import com.magickeyboard.template.TemplateSearch
import com.magickeyboard.template.TimeSaved
import com.magickeyboard.template.TriggerMatcher
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Colors
import java.time.LocalDateTime
import java.util.concurrent.Executors

/** Inserts a rendered template in place of the trigger the user typed. Implemented by LatinIME. */
fun interface TemplateInserter {
    /**
     * Deletes [deleteLength] characters before the cursor, inserts [text], then moves the cursor
     * [cursorFromEnd] characters back from the end of [text] (0 keeps it at the end).
     */
    fun insertTemplate(deleteLength: Int, text: String, cursorFromEnd: Int)
}

/**
 * Template suggestions shown in the suggestion strip while the user types a trigger such as
 * `/ong`. Never logs or stores typed text; only template ids and character counts are recorded.
 */
class TemplateSuggestions(private val context: Context) {
    // Lazy: LatinIME creates this before its context is attached.
    private val repo by lazy { ManagerRepository.get(context) }
    private val io = Executors.newSingleThreadExecutor()
    private var cache: List<Template> = emptyList()
    private var cacheRevision = -1L

    /** Trigger text (e.g. `/ong`) currently shown, and one the user closed the strip for. */
    private var shownTrigger: String? = null
    private var dismissedTrigger: String? = null

    /** Hides suggestions until the user types something that changes the trigger. */
    fun dismiss() {
        dismissedTrigger = shownTrigger
    }

    /**
     * Returns a strip view listing templates that match the trigger at the end of
     * [textBeforeCursor], or null when there is no trigger, no match, or a password field.
     */
    fun createView(
        editorInfo: EditorInfo?,
        textBeforeCursor: CharSequence?,
        isPasswordField: Boolean,
        colors: Colors,
        inserter: TemplateInserter,
    ): View? {
        shownTrigger = null
        editorPackage = editorInfo?.packageName
        if (isPasswordField || textBeforeCursor.isNullOrEmpty() || isOwnEditor(editorInfo)) return null
        val match = TriggerMatcher.find(textBeforeCursor, repo.triggerChar()) ?: return null
        val trigger = textBeforeCursor.subSequence(textBeforeCursor.length - match.length, textBeforeCursor.length).toString()
        if (trigger == dismissedTrigger) return null
        dismissedTrigger = null
        val templates = templates()
        if (templates.isEmpty()) return null
        val byId = templates.associateBy { it.id }
        val ranked = TemplateSearch.rank(
            match.query,
            templates.map { TemplateEntry(it.id, it.shortcut, it.title, it.usageCount) },
        )
        if (ranked.isEmpty()) return null
        shownTrigger = trigger

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.setPadding(dp(4f), 0, dp(4f), 0)
        ranked.forEachIndexed { index, entry ->
            val template = byId.getValue(entry.id)
            row.addView(chip(template, colors, first = index == 0) { insert(template, match.length, inserter) })
        }
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    /**
     * A rounded card in the key colour with the template's name only (no trigger or shortcut: the
     * user already typed it). The first (best) match is bold with an accent outline, the default choice.
     */
    private fun chip(template: Template, colors: Colors, first: Boolean, onClick: () -> Unit): TextView {
        val accent = colors.get(ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND)
        val label = SpannableString(template.title.ifBlank { template.shortcut }).apply {
            if (first) setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return TextView(context).apply {
            text = label
            maxLines = 1
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f), 0, dp(14f), 0)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            KeyboardTypeface.applyToTextView(this)
            setTextColor(colors.get(ColorType.KEY_TEXT))
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(colors.get(ColorType.KEY_BACKGROUND))
                if (first) setStroke(dp(1.5f), accent)
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(dp(4f), dp(5f), dp(4f), dp(5f))
            }
            setOnClickListener { onClick() }
        }
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    /** Templates for [TemplatePanel]: most used first, then by name. */
    fun templatesByUse(): List<Template> =
        templates().sortedWith(compareByDescending<Template> { it.usageCount }.thenBy { it.title.ifBlank { it.shortcut }.lowercase() })

    /** Inserts [template] at the cursor, picked from [TemplatePanel] (no trigger to replace). */
    fun insertFromPanel(template: Template, inserter: TemplateInserter) = insert(template, 0, inserter)

    /** Reads the space-hold settings of the manager into [SpaceHold]; call when the keyboard opens. */
    fun refreshSpaceHold() {
        SpaceHold.enabled = repo.spaceHoldEnabled()
        SpaceHold.delayMs = repo.spaceHoldMs()
    }

    internal fun isOwnEditorForPanel(editorInfo: EditorInfo?): Boolean {
        editorPackage = editorInfo?.packageName
        return isOwnEditor(editorInfo)
    }

    private fun insert(template: Template, triggerLength: Int, inserter: TemplateInserter) {
        // The free plan's 30 uses (PRD §9): once used up, a template is not inserted; the keyboard
        // itself keeps working. Neutral info only, no call to buy (Google Play policy).
        val sync = SupabaseSync.get(context)
        val limited = sync.freeLimited()
        if (!FreeLimits.canInsert(limited, repo.freeUses())) {
            val message = if (sync.signedInEmail() == null) R.string.sg_limit_uses_done_signed_out else R.string.sg_limit_uses_done
            Toast.makeText(context, AppLanguage.string(context, message, FreeLimits.USES), Toast.LENGTH_LONG).show()
            return
        }
        val parsed = TemplateParser.parse(template.body)
        if (parsed.variables.isNotEmpty()) {
            // Ask for the variables first; the result comes back through onFormDone.
            TemplateFormActivity.start(context, template.id, triggerLength, editorPackage)
            return
        }
        val result = TemplateRenderer.render(parsed, contact = null, inputs = emptyMap(), now = LocalDateTime.now())
        val cursorFromEnd = result.cursor?.let { result.text.length - it } ?: 0
        inserter.insertTemplate(triggerLength, result.text, cursorFromEnd)
        io.execute { repo.recordUsage(template.id, result.text.length, limited) }
        showSaved(result.text.length)
    }

    private fun showSaved(chars: Int) {
        Toast.makeText(context, AppLanguage.string(context, R.string.sg_time_saved_toast, AppLanguage.duration(context, TimeSaved.seconds(chars.toLong()))), Toast.LENGTH_SHORT).show()
    }

    private class PendingInsert(val deleteLength: Int, val text: String, val cursorFromEnd: Int)

    private var pending: PendingInsert? = null

    /** Package of the app being typed into; the form uses it to fill in a new contact's channel. */
    private var editorPackage: String? = null

    /** Receives the form result sent by [TemplateFormActivity]; returns false for other intents. */
    fun onFormDone(intent: Intent?): Boolean {
        if (intent == null || intent.action != TemplateFormActivity.ACTION_DONE) return false
        val text = intent.getStringExtra(TemplateFormActivity.EXTRA_TEXT) ?: return true
        pending = PendingInsert(
            deleteLength = intent.getIntExtra(TemplateFormActivity.EXTRA_TRIGGER_LENGTH, 0),
            text = text,
            cursorFromEnd = intent.getIntExtra(TemplateFormActivity.EXTRA_CURSOR_FROM_END, 0),
        )
        return true
    }

    /**
     * Inserts a pending form result once the keyboard is back in the app the user was typing in
     * (not in this app's own form). Call after the input connection is ready.
     */
    fun insertPending(editorInfo: EditorInfo?, inserter: TemplateInserter) {
        val insert = pending ?: return
        if (editorInfo == null || isOwnEditor(editorInfo)) return
        pending = null
        inserter.insertTemplate(insert.deleteLength, insert.text, insert.cursorFromEnd)
        showSaved(insert.text.length)
    }

    /**
     * Text fields of this app (the manager and the variable form) never get template suggestions,
     * except the onboarding practice field marked with [PRACTICE_FIELD_OPTION].
     */
    private fun isOwnEditor(editorInfo: EditorInfo?): Boolean =
        editorInfo?.packageName == context.packageName && editorInfo?.privateImeOptions != PRACTICE_FIELD_OPTION

    companion object {
        /** `privateImeOptions` of the onboarding practice field, where templates must work. */
        const val PRACTICE_FIELD_OPTION = "com.magickeyboard.practice"
    }

    /** Templates from the manager's database, reloaded only after the manager changed something. */
    private fun templates(): List<Template> {
        val revision = repo.changes.value
        if (revision != cacheRevision) {
            cache = repo.templates()
            cacheRevision = revision
        }
        return cache
    }
}
