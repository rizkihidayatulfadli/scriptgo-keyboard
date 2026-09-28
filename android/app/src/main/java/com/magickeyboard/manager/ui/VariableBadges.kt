package com.magickeyboard.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magickeyboard.manager.ui.theme.ScriptGo
import com.magickeyboard.manager.ui.theme.ScriptGoColors
import com.magickeyboard.manager.ui.theme.ScriptGoIcons
import helium314.keyboard.latin.R

/** Human names for built-in variables; custom fields use their own label. */
private val BUILT_IN = mapOf(
    "nama" to R.string.sg_var_name,
    "nomor" to R.string.sg_var_phone,
    "@tanggal" to R.string.sg_var_date,
    "@jam" to R.string.sg_var_time,
    "@hari" to R.string.sg_var_day,
    "@kursor" to R.string.sg_var_cursor,
)

/** "kota" → "Kota", "no_resi" → "No resi", "@tanggal" → "Tanggal"; [labels] maps field keys to labels. */
@Composable
fun variableLabel(key: String, labels: Map<String, String> = emptyMap()): String {
    val base = key.substringBefore('|').substringBefore(':').trim()
    labels[base]?.let { return it }
    BUILT_IN[base]?.let { return stringResource(it) }
    val words = base.removePrefix("@").replace('_', ' ').replace('-', ' ').trim()
    return words.replaceFirstChar { it.uppercaseChar() }.ifEmpty { key }
}

private val VARIABLE = Regex("\\{([^{}\\n]+)\\}")

/**
 * Shows `{nama}` in the message editor as a sky-blue badge reading "Nama", without the braces. The
 * text itself keeps the braces (that is the template syntax), so offsets map one to one: the
 * braces are drawn transparent and hair-thin, and the key is replaced by nothing but styling.
 */
class VariableBadgeTransformation(private val colors: ScriptGoColors) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = buildAnnotatedString {
            append(text)
            for (match in VARIABLE.findAll(text.text)) {
                val open = match.range.first
                val close = match.range.last
                val hidden = SpanStyle(color = Color.Transparent, fontSize = 1.sp, letterSpacing = 0.sp)
                addStyle(hidden, open, open + 1)
                addStyle(hidden, close, close + 1)
                addStyle(
                    SpanStyle(background = colors.accentSoft, color = colors.accentInk, fontWeight = FontWeight.Bold),
                    open + 1,
                    close,
                )
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

/** A tappable badge that inserts a variable ("+ Nama"). */
@Composable
fun VariableChip(label: String, onClick: () -> Unit) {
    val c = ScriptGo.colors
    Row(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp)).background(c.accentSoft)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(ScriptGoIcons.Plus, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.accentInk)
    }
}

/** A small badge naming a variable, used as the label above its input in the template form. */
@Composable
fun VariableBadge(label: String, modifier: Modifier = Modifier) {
    val c = ScriptGo.colors
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = c.accentInk,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(c.accentSoft).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
