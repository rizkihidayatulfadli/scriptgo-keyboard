// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.previewDark

// taken from StreetComplete
@Composable
fun <T: Any> ListPickerDialog(
    onDismissRequest: () -> Unit,
    items: List<T>,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    selectedItem: T? = null,
    getItemName: (@Composable (T) -> String) = { it.toString() },
    confirmImmediately: Boolean = true,
    showRadioButtons: Boolean = true,
    onDefault: (() -> Unit)? = null,
) {
    var selected by remember { mutableStateOf(selectedItem) }
    val state = rememberLazyListState()

    LaunchedEffect(selectedItem) {
        val index = items.indexOf(selectedItem)
        if (index != -1) state.scrollToItem(index, -state.layoutInfo.viewportSize.height / 3)
    }
    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = { selected?.let { onItemSelected(it) } },
        confirmButtonText = if (confirmImmediately) null else stringResource(android.R.string.ok),
        checkOk = { selected != null },
        onNeutral = { onDefault?.invoke(); onDismissRequest(); },
        neutralButtonText = onDefault?.let { stringResource(R.string.button_default) },
        modifier = modifier,
        title = title,
        content = {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodyLarge
            ) {
                LazyColumn(state = state) {
                    items(items) { item ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            // magic-keyboard: rounded rows, the selected one highlighted like the manager app's choices
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected == item) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable {
                                    if (confirmImmediately) {
                                        onDismissRequest()
                                        onItemSelected(item)
                                    }
                                    selected = item
                                }
                                .padding(horizontal = if (showRadioButtons) 8.dp else 16.dp)
                                .heightIn(min = 48.dp)
                        ) {
                            if (showRadioButtons)
                                RadioButton(
                                    selected = selected == item,
                                    onClick = {
                                        if (confirmImmediately) {
                                            onDismissRequest()
                                            onItemSelected(item)
                                        }
                                        selected = item
                                    }
                                )
                            Text(
                                text = getItemName(item),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        },
    )
}

@Preview
@Composable
private fun PreviewListPickerDialog() {
    val items = remember { (0..<5).toList() }
    Theme(previewDark) {
        ListPickerDialog(
            onDismissRequest = {},
            items = items,
            onItemSelected = {},
            title = { Text("Select something") },
            selectedItem = 2,
            getItemName = { "Item $it" },
        )
    }
}
