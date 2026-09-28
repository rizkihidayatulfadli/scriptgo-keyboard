// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.content.Intent
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getDictionaryLocales
import helium314.keyboard.latin.utils.htmlToAnnotated
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.withHtmlLink
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.DictionaryDialog
import helium314.keyboard.settings.dictionaryFilePicker
import helium314.keyboard.settings.initPreview
import helium314.keyboard.latin.utils.previewDark
import com.magickeyboard.manager.ui.settings.DictionaryList
import com.magickeyboard.manager.ui.settings.DictionarySearchResult
import com.magickeyboard.manager.ui.settings.dictionaryEntries
import java.io.File
import java.util.Locale

@Composable
fun DictionaryScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val enabledLanguages = SubtypeSettings.getEnabledSubtypes(true).map { it.locale().language }
    val cachedDictFolders = DictionaryInfoUtils.getCacheDirectories(ctx).map { it.name }
    val comparer = compareBy<Locale>({ it.language !in enabledLanguages }, { it.toLanguageTag() !in cachedDictFolders }, { it.displayName })
    val dictionaryLocales = listOf<Locale?>(null) + getDictionaryLocales(ctx).sortedWith(comparer)
    var selectedLocale: Locale? by remember { mutableStateOf(null) }
    var showAddDictDialog by remember { mutableStateOf(false) }
    val dictPicker = dictionaryFilePicker(selectedLocale)
    // magic-keyboard: ScriptGo cards (active languages first), see DictionaryList.kt
    val entries = dictionaryEntries(ctx, dictionaryLocales.filterNotNull(), enabledLanguages, stringResource(R.string.internal_dictionary_summary))
    SearchScreen(
        onClickBack = onClickBack,
        title = { Text(stringResource(R.string.dictionary_settings_category)) },
        filteredItems = { term ->
            entries.filter { entry ->
                entry.title.replace("(", "").splitOnWhitespace().any { it.startsWith(term, true) }
            }
        },
        itemContent = { entry -> DictionarySearchResult(entry) { selectedLocale = it.locale } },
        content = { DictionaryList(entries, onAdd = { showAddDictDialog = true }, onOpen = { selectedLocale = it.locale }) },
    )
    if (showAddDictDialog) {
        ConfirmationDialog(
            onDismissRequest = { showAddDictDialog = false },
            onConfirmed = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/octet-stream")
                dictPicker.launch(intent)
            },
            title = { Text(stringResource(R.string.add_new_dictionary_title)) },
            content = {
                val link = stringResource(R.string.dictionary_link_text).withHtmlLink(Links.DICTIONARY_URL)
                val addDictString = stringResource(R.string.add_dictionary, link)
                Text(addDictString.htmlToAnnotated())
            }
        )
    }
    if (selectedLocale != null) {
        DictionaryDialog(
            onDismissRequest = { selectedLocale = null },
            locale = selectedLocale!!
        )
    }
}

/** @return list of user dictionary files and whether an internal dictionary exists */
fun getUserAndInternalDictionaries(context: Context, locale: Locale): Pair<List<File>, Boolean> {
    val userDicts = mutableListOf<File>()
    var hasInternalDict = false
    val userLocaleDir = DictionaryInfoUtils.getCacheDirectoryForLocale(locale, context)?.let { File(it) }
    if (userLocaleDir?.exists() == true && userLocaleDir.isDirectory) {
        userLocaleDir.listFiles()?.forEach {
            if (it.name.endsWith(DictionaryInfoUtils.USER_DICTIONARY_SUFFIX))
                userDicts.add(it)
            else if (it.name.startsWith(DictionaryInfoUtils.MAIN_DICT_PREFIX))
                hasInternalDict = true
        }
    }
    if (hasInternalDict)
        return userDicts to true
    val internalDicts = DictionaryInfoUtils.getAssetsDictionaryList(context) ?: return userDicts to false
    val best = LocaleUtils.getBestMatch(locale, internalDicts.toList()) {
        DictionaryInfoUtils.extractLocaleFromAssetsDictionaryFile(it)
    }
    return userDicts to (best != null)
}

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            DictionaryScreen { }
        }
    }
}
