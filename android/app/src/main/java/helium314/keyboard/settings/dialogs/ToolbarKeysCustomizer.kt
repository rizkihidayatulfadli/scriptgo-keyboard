// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.OutlinedTextField
import com.magickeyboard.manager.ui.settings.ScriptGoFieldShape
import com.magickeyboard.manager.ui.settings.ToolbarKeyRow
import com.magickeyboard.manager.ui.settings.scriptGoFieldColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.checkAndConvertCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.getCodeForToolbarKey
import helium314.keyboard.latin.utils.getCodeForToolbarKeyLongClick
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.readCustomKeyCodes
import helium314.keyboard.latin.utils.writeCustomKeyCodes
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.settings.initPreview
import helium314.keyboard.latin.utils.previewDark
import androidx.core.content.edit
import helium314.keyboard.settings.GetIconOrEmpty

@Composable
fun ToolbarKeysCustomizer(
    key: String,
    onDismissRequest: () -> Unit
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    var showKeyCustomizer: ToolbarKey? by rememberSaveable { mutableStateOf(null) }
    var showDeletePrefConfirmDialog by rememberSaveable { mutableStateOf(false) }
    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        cancelButtonText = stringResource(R.string.dialog_close),
        confirmButtonText = null,
        onConfirmed = { },
        neutralButtonText = if (readCustomKeyCodes(prefs).isNotEmpty()) stringResource(R.string.button_default) else null,
        onNeutral = { showDeletePrefConfirmDialog = true },
        title = { Text(stringResource(R.string.customize_toolbar_key_codes)) },
        content = {
            LazyColumn { // magic-keyboard: ScriptGo rows (icon tile, name, chevron) with thin dividers
                itemsIndexed(ToolbarKey.entries) { index, key ->
                    ToolbarKeyRow(key.name.lowercase().getStringResourceOrName("", ctx), divider = index > 0, onClick = { showKeyCustomizer = key }) {
                        KeyboardIconsSet.instance.GetIconOrEmpty(key.name)
                    }
                }
            }
        },
    )
    if (showKeyCustomizer != null) {
        val shownKey = showKeyCustomizer
        if (shownKey != null)
            ToolbarKeyCustomizer(shownKey) { showKeyCustomizer = null }
    }
    if (showDeletePrefConfirmDialog)
        ConfirmationDialog(
            onDismissRequest = { showDeletePrefConfirmDialog = false },
            onConfirmed = {
                showDeletePrefConfirmDialog = false
                onDismissRequest()
                prefs.edit { remove(key) }
            },
            content = { Text(stringResource(R.string.customize_toolbar_key_code_reset_message)) }
        )
}

@Composable
private fun ToolbarKeyCustomizer(
    key: ToolbarKey,
    onDismissRequest: () -> Unit
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    var code by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(getCodeForToolbarKey(key).toString())) }
    var longPressCode by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(getCodeForToolbarKeyLongClick(key).toString())) }
    ThreeButtonAlertDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = {
            val codes = readCustomKeyCodes(prefs)
            codes[key] = checkCode(code) to checkCode(longPressCode, true)
            writeCustomKeyCodes(prefs, codes)
        },
        checkOk = { checkCode(code) != null && checkCode(longPressCode, true) != null },
        neutralButtonText = if (readCustomKeyCodes(prefs).containsKey(key))
                stringResource(R.string.button_default)
            else null,
        onNeutral = {
            onDismissRequest()
            val keys = readCustomKeyCodes(prefs)
            keys.remove(key)
            writeCustomKeyCodes(prefs, keys)
        },
        title = { Text(key.name.lowercase().getStringResourceOrName("", ctx)) },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { // magic-keyboard: labelled ScriptGo fields
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(stringResource(R.string.key_code)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = ScriptGoFieldShape,
                    colors = scriptGoFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = longPressCode,
                    onValueChange = { longPressCode = it },
                    label = { Text(stringResource(R.string.long_press_code)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = ScriptGoFieldShape,
                    colors = scriptGoFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
    )
}

private fun checkCode(code: TextFieldValue, longPress: Boolean = false) = runCatching {
    code.text.toIntOrNull()?.takeIf { it.checkAndConvertCode(longPress) <= Char.MAX_VALUE.code }
}.getOrNull()

@Preview
@Composable
private fun PreviewToolbarKeyCustomizer() {
    initPreview(LocalContext.current)
    ToolbarKeyCustomizer(ToolbarKey.CUT) { }
}

@Preview
@Composable
private fun PreviewToolbarKeysCustomizer() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        ToolbarKeysCustomizer("") { }
    }
}
