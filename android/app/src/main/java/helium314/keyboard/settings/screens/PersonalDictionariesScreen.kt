// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeSettings.getEnabledSubtypes
import helium314.keyboard.latin.utils.SubtypeSettings.getSystemLocales
import helium314.keyboard.latin.utils.getSecondaryLocales
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.settings.SearchScreen
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.latin.utils.Theme
import com.magickeyboard.manager.ui.settings.PersonalDictionaryLocaleRow
import com.magickeyboard.manager.ui.settings.PersonalDictionaryLocales
import com.magickeyboard.manager.ui.settings.SettingsSearchResult
import helium314.keyboard.settings.initPreview
import helium314.keyboard.latin.utils.previewDark
import java.util.Locale
import java.util.TreeSet

@Composable
fun PersonalDictionariesScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val locales: MutableList<Locale?> = getSortedDictionaryLocales().toMutableList()
    locales.add(0, null)
    SearchScreen(
        onClickBack = onClickBack,
        title = { Text(stringResource(R.string.edit_personal_dictionary)) },
        filteredItems = { term ->
            locales.filter { locale ->
                locale.getLocaleDisplayNameForUserDictSettings(ctx).replace("(", "")
                    .splitOnWhitespace().any { it.startsWith(term, true) }
            }
        },
        // magic-keyboard: ScriptGo rows on one card (search results on their own cards)
        itemContent = {
            SettingsSearchResult {
                PersonalDictionaryLocaleRow(it, it.getLocaleDisplayNameForUserDictSettings(ctx), divider = false) { openPersonalDictionary(it) }
            }
        },
        content = { PersonalDictionaryLocales(locales, { it.getLocaleDisplayNameForUserDictSettings(ctx) }, ::openPersonalDictionary) },
    )
}

private fun openPersonalDictionary(locale: Locale?) =
    SettingsDestination.navigateTo(SettingsDestination.PersonalDictionary + (locale?.toLanguageTag() ?: ""))

fun getSortedDictionaryLocales(): TreeSet<Locale> {
    val sortedLocales = sortedSetOf<Locale>(compareBy { it.toLanguageTag().lowercase() })

    // Add the main language selected in the "Language and Layouts" setting except "No language"
    for (mainSubtype in getEnabledSubtypes(true)) {
        val mainLocale = mainSubtype.locale()
        if (mainLocale.toLanguageTag() != SubtypeLocaleUtils.NO_LANGUAGE) {
            sortedLocales.add(mainLocale)
        }
        // Secondary language is added only if main language is selected
        val enabled = getEnabledSubtypes(false)
        for (subtype in enabled) {
            if (subtype.locale() == mainLocale) sortedLocales.addAll(getSecondaryLocales(subtype.extraValue))
        }
    }

    sortedLocales.addAll(getSystemLocales())
    return sortedLocales
}

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            PersonalDictionariesScreen { }
        }
    }
}
