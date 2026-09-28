// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.os.Build
import android.view.inputmethod.InputMethodSubtype
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.MissingDictionaryDialog
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.settings.initPreview
import helium314.keyboard.latin.utils.previewDark
import com.magickeyboard.manager.ui.settings.LanguageRow
import com.magickeyboard.manager.ui.settings.LazyCardSections
import com.magickeyboard.manager.ui.settings.SettingsSearchResult
import java.util.Locale

@Composable
fun LanguageScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val sortedSubtypes by remember { mutableStateOf(getSortedSubtypes(ctx)) }
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val enabledSubtypes = SubtypeSettings.getEnabledSubtypes()
    // magic-keyboard: ScriptGo cards; rows stay in the section they had when the screen opened
    val sections = remember { sortedSubtypes.partition { it in enabledSubtypes } }
    SearchScreen(
        onClickBack = onClickBack,
        title = { Text(stringResource(R.string.language_and_layouts_title)) },
        filteredItems = { term ->
            sortedSubtypes.filter { subtype ->
                subtype.displayName().replace("(", "")
                    .splitOnWhitespace().any { it.startsWith(term, true) }
            }
        },
        itemContent = { SettingsSearchResult { SubtypeRow(it, it in enabledSubtypes, divider = false) } },
        content = {
            LazyCardSections(
                intro = stringResource(R.string.sg_lang_intro),
                sections = listOf(
                    stringResource(R.string.sg_lang_enabled) to sections.first,
                    stringResource(R.string.sg_lang_other) to sections.second,
                ),
            ) { subtype, divider -> SubtypeRow(subtype, subtype in enabledSubtypes, divider) }
        },
    )
}

@Composable
private fun SubtypeRow(subtype: InputMethodSubtype, isEnabled: Boolean, divider: Boolean) {
    val ctx = LocalContext.current
    var showNoDictDialog by remember { mutableStateOf(false) }
    val description = if (SubtypeSettings.isAdditionalSubtype(subtype)) {
        val secondaryLocales = subtype.getExtraValueOf(ExtraValue.SECONDARY_LOCALES)?.split(Separators.KV)
            ?.joinToString(", ") { it.constructLocale().localizedDisplayName(ctx.resources) }
        stringResource(R.string.custom_subtype) + (secondaryLocales?.let { "\n$it" } ?: "")
    } else null
    // magic-keyboard: ScriptGo row (language code tile, bold name, switch)
    LanguageRow(
        code = subtype.locale().language.let { if (it == SubtypeLocaleUtils.NO_LANGUAGE) "Abc" else it },
        title = subtype.displayName(),
        description = description,
        checked = isEnabled,
        onCheckedChange = {
            if (it && !dictsAvailable(subtype.locale(), ctx))
                showNoDictDialog = true
            if (it) SubtypeSettings.addEnabledSubtype(ctx.prefs(), subtype)
            else SubtypeSettings.removeEnabledSubtype(ctx, subtype)
        },
        onClick = {
            SettingsDestination.navigateTo(SettingsDestination.Subtype + subtype.toSettingsSubtype().toPref())
        },
        divider = divider,
    )
    if (showNoDictDialog)
        MissingDictionaryDialog({ showNoDictDialog = false }, subtype.locale())
}

private fun dictsAvailable(locale: Locale, context: Context): Boolean {
    if (locale.language == SubtypeLocaleUtils.NO_LANGUAGE) return true // incorrect, but we don't want to show the dialog for "no language"
    val (dicts, hasInternal) = getUserAndInternalDictionaries(context, locale)
    return hasInternal || dicts.isNotEmpty()
}

// sorting by display name is still slow, even with the cache... but probably good enough
private fun getSortedSubtypes(context: Context): List<InputMethodSubtype> {
    val systemLocales = SubtypeSettings.getSystemLocales()
    val enabledSubtypes = SubtypeSettings.getEnabledSubtypes(true)
    val localesWithDictionary = DictionaryInfoUtils.getCacheDirectories(context).mapNotNull { dir ->
        if (dir.list()?.any { it.endsWith(DictionaryInfoUtils.USER_DICTIONARY_SUFFIX) } == true)
            dir.name.constructLocale()
        else null
    }

    val defaultAdditionalSubtypes = Defaults.PREF_ADDITIONAL_SUBTYPES.split(Separators.SETS).map {
        it.substringBefore(Separators.SET) to (it.substringAfter(Separators.SET) + ",AsciiCapable,EmojiCapable,isAdditionalSubtype")
    }
    fun isDefaultSubtype(subtype: InputMethodSubtype): Boolean =
        defaultAdditionalSubtypes.any { it.first == subtype.locale().language && it.second == subtype.extraValue }

    val subtypeSortComparator = compareBy<InputMethodSubtype>(
        { it !in enabledSubtypes },
        { it.locale() !in localesWithDictionary },
        { it.locale() !in systemLocales},
        { !(SubtypeSettings.isAdditionalSubtype(it) && !isDefaultSubtype(it) ) },
        {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) it.languageTag == SubtypeLocaleUtils.NO_LANGUAGE
            else it.locale == SubtypeLocaleUtils.NO_LANGUAGE
        },
        { it.displayName() }
    )
    return SubtypeSettings.getAllAvailableSubtypes().sortedWith(subtypeSortComparator)
}

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            LanguageScreen { }
        }
    }
}
