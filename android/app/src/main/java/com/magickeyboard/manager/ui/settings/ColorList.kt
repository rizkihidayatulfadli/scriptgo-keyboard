package com.magickeyboard.manager.ui.settings

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import helium314.keyboard.latin.R

/**
 * One colour of a custom keyboard theme on HeliBoard's colours screen (`ColorsScreen`, see
 * docs/fork.md): swatch, name, "automatic" note, and the automatic switch when the colour has one.
 */
@Composable
fun ColorRow(name: String, color: Int, auto: Boolean?, divider: Boolean, onClick: () -> Unit, onAutoChange: (Boolean) -> Unit) {
    SettingsListRow(onClick, divider) {
        ColorSwatch(Color(color))
        SettingsListText(name, if (auto == true) stringResource(R.string.auto_user_color) else null, Modifier.weight(1f))
        if (auto != null) Switch(checked = auto, onCheckedChange = onAutoChange)
    }
}
