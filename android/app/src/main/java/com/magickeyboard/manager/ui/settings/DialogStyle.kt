package com.magickeyboard.manager.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magickeyboard.manager.ui.theme.ScriptGo

// ScriptGo look for HeliBoard's keyboard settings dialogs (layout editor, toolbar keys, reorder
// lists), used at a few integration points (docs/fork.md).

/** Rounded corners of text fields in keyboard settings dialogs. */
val ScriptGoFieldShape = RoundedCornerShape(16.dp)

/** Outlined text field colours: hairline border, accent when focused. */
@Composable
fun scriptGoFieldColors(): TextFieldColors {
    val c = ScriptGo.colors
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accentSolid,
        unfocusedBorderColor = c.line,
        cursorColor = c.accentSolid,
        focusedLabelColor = c.accentInk,
    )
}

/** Monospace text for code-like input such as a layout's JSON. */
val CodeTextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)

/**
 * A keyboard key icon (HeliBoard draws these at 40 dp) shrunk onto the soft accent tile, like the
 * icon tiles of the settings rows.
 */
@Composable
fun KeyIconTile(icon: @Composable () -> Unit) {
    val c = ScriptGo.colors
    Box(Modifier.size(40.dp).background(c.accentSoft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides c.accentInk) {
            Box(Modifier.scale(0.6f), contentAlignment = Alignment.Center) { icon() }
        }
    }
}

/** One toolbar key in the key code list: icon tile, name, and a chevron to its codes. */
@Composable
fun ToolbarKeyRow(name: String, divider: Boolean, onClick: () -> Unit, icon: @Composable () -> Unit) {
    SettingsListRow(onClick, divider) {
        KeyIconTile(icon)
        SettingsListText(name, null, Modifier.weight(1f))
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { helium314.keyboard.latin.utils.NextScreenIcon() }
    }
}
