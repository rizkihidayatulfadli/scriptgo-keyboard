package com.magickeyboard.manager.ui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magickeyboard.manager.ui.SettingsGroup
import com.magickeyboard.manager.ui.theme.ScriptGo
import helium314.keyboard.settings.preferences.PreferenceCategory
import java.util.Locale

// Building blocks for HeliBoard's list screens (dictionaries, languages) in ScriptGo style: the same
// row shape as HeliBoard's `Preference` after the ScriptGo changes (docs/fork.md).

/** A clickable settings row: 14 dp gap between a leading tile, the text, and trailing controls. */
@Composable
internal fun SettingsListRow(onClick: () -> Unit, divider: Boolean = false, content: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (divider) HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 1.dp, color = ScriptGo.colors.line)
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(vertical = 14.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Bold 16 sp title and an optional 14 sp muted line under it. */
@Composable
internal fun SettingsListText(title: String, subtitle: String?, modifier: Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrEmpty()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Short text (a language code such as "EN", or a first letter) on a soft accent tile, like the icon tiles of other settings rows. */
@Composable
internal fun CodeTile(code: String) {
    val c = ScriptGo.colors
    Box(Modifier.size(40.dp).background(c.accentSoft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Text(
            code.uppercase(Locale.ROOT).take(3),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = c.accentInk,
        )
    }
}

/** A drawable icon on the same soft accent tile. */
@Composable
internal fun IconTile(@DrawableRes icon: Int) {
    val c = ScriptGo.colors
    Box(Modifier.size(40.dp).background(c.accentSoft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = c.accentInk, modifier = Modifier.size(22.dp))
    }
}

/** A round colour swatch with a hairline edge, so white and background colours still show. */
@Composable
internal fun ColorSwatch(color: Color) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(color).border(1.dp, ScriptGo.colors.line, CircleShape))
}

/** Short muted explanation at the top of a settings list. */
@Composable
internal fun SettingsListIntro(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp),
    )
}

/**
 * A long list on ScriptGo cards that stays lazy (hundreds of rows): [intro], then each non-empty
 * section as a small uppercase label (none when blank) and one card, drawn as LazyColumn items whose
 * first and last rows carry the card's rounded corners. [empty] shows when every section is empty;
 * [bottomSpace] keeps the last row clear of a floating button.
 */
@Composable
fun <T> LazyCardSections(
    intro: String?,
    sections: List<Pair<String, List<T>>>,
    empty: String? = null,
    bottomSpace: Dp = 0.dp,
    row: @Composable (item: T, divider: Boolean) -> Unit,
) {
    val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val card = ScriptGo.colors.card
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + bottom + bottomSpace)) {
        if (intro != null) item { SettingsListIntro(intro) }
        if (empty != null && sections.all { it.second.isEmpty() }) item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(card).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(empty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        sections.forEach { (label, items) ->
            if (items.isEmpty()) return@forEach
            if (label.isNotBlank()) item { PreferenceCategory(label) }
            itemsIndexed(items) { index, item ->
                val top = if (index == 0) 22.dp else 0.dp
                val end = if (index == items.lastIndex) 22.dp else 0.dp
                val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = end, bottomEnd = end)
                Box(Modifier.fillMaxWidth().clip(shape).background(card)) { row(item, index > 0) }
            }
        }
    }
}

/**
 * A labelled ScriptGo card for settings built from custom controls (HeliBoard's `SubtypeScreen`):
 * the small uppercase label, then the controls on one card.
 */
@Composable
fun SettingsCardSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    PreferenceCategory(label)
    SettingsGroup {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), content = content)
    }
}

/** One titled control inside a [SettingsCardSection], with a thin line above it when [divider]. */
@Composable
fun SettingsCardItem(title: String, divider: Boolean, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (divider) HorizontalDivider(Modifier.padding(horizontal = 8.dp), thickness = 1.dp, color = ScriptGo.colors.line)
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp),
        )
        content()
    }
}
