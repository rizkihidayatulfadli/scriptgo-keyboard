@file:OptIn(ExperimentalMaterial3Api::class)

package com.magickeyboard.manager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.magickeyboard.manager.ui.theme.ScriptGo
import com.magickeyboard.manager.ui.theme.ScriptGoIcons
import helium314.keyboard.latin.R

/**
 * A small ⓘ button that opens a short explanation from the bottom. Screens keep one line of text
 * and move the longer "why / how" here, so they stay easy to scan. (Phones have no hover, so a
 * tap-to-open sheet stands in for a tooltip.)
 */
@Composable
fun InfoTip(title: String, text: String, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.size(36.dp)) {
        Icon(
            ScriptGoIcons.Info,
            contentDescription = stringResource(R.string.sg_info_more, title),
            tint = ScriptGo.colors.muted,
            modifier = Modifier.size(18.dp),
        )
    }
    if (open) {
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = ScriptGo.colors.card,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = ScriptGo.colors.muted)
            }
        }
    }
}

/** [SectionLabel] with an [InfoTip] after it. */
@Composable
fun SectionLabelWithInfo(label: String, infoTitle: String, infoText: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(label)
        InfoTip(infoTitle, infoText)
    }
}
