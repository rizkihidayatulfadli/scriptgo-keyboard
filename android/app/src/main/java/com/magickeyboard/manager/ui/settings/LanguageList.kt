package com.magickeyboard.manager.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.magickeyboard.manager.ui.SettingsGroup

/**
 * One keyboard language on HeliBoard's "Bahasa & tata letak" screen (`LanguageScreen`, see
 * docs/fork.md): language code tile, name, optional note, and the on/off switch.
 */
@Composable
fun LanguageRow(
    code: String,
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    divider: Boolean,
) {
    SettingsListRow(onClick, divider) {
        CodeTile(code)
        SettingsListText(title, description, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** A search result on its own card. */
@Composable
fun SettingsSearchResult(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        SettingsGroup { content() }
    }
}
