package com.magickeyboard.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * ScriptGo look for HeliBoard's keyboard settings: preferences between two category headers sit on
 * one white card, the way the manager app groups its rows. Used at a few integration points in
 * `helium314.keyboard.settings` (see docs/fork.md).
 */
@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    BCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp), content = content)
    }
}

/** Page padding around settings groups. */
fun Modifier.settingsPagePadding(): Modifier = padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp)

/**
 * Splits HeliBoard's flat settings list (category string ids as [Int], preference keys, or null for
 * hidden preferences) into sections: the category header (null before the first one) and the items
 * that follow it.
 */
fun groupSettings(settings: List<Any?>): List<Pair<Int?, List<Any?>>> {
    val groups = mutableListOf<Pair<Int?, MutableList<Any?>>>()
    for (item in settings) {
        if (item is Int) groups.add(item to mutableListOf())
        else {
            if (groups.isEmpty()) groups.add(null to mutableListOf())
            groups.last().second.add(item)
        }
    }
    return groups
}
