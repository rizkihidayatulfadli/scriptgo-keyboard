package com.magickeyboard.manager.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.magickeyboard.manager.ui.SettingsGroup
import com.magickeyboard.manager.ui.settingsPagePadding
import com.magickeyboard.manager.ui.theme.ScriptGo
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.NextScreenIcon
import java.util.Locale

// ScriptGo look for HeliBoard's personal dictionary screens (`PersonalDictionariesScreen` and
// `PersonalDictionaryScreen`, see docs/fork.md).

/** The languages that have a personal dictionary, on one card; null is "all languages". */
@Composable
fun PersonalDictionaryLocales(locales: List<Locale?>, name: (Locale?) -> String, onOpen: (Locale?) -> Unit) {
    Column(
        Modifier.verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .settingsPagePadding(),
    ) {
        SettingsListIntro(stringResource(R.string.sg_pdict_intro))
        SettingsGroup {
            locales.forEachIndexed { index, locale -> PersonalDictionaryLocaleRow(locale, name(locale), index > 0) { onOpen(locale) } }
        }
    }
}

/** One language of the personal dictionary; "all languages" gets the dictionary icon. */
@Composable
fun PersonalDictionaryLocaleRow(locale: Locale?, name: String, divider: Boolean, onClick: () -> Unit) {
    SettingsListRow(onClick, divider) {
        if (locale == null) IconTile(R.drawable.sg_ic_dictionary) else CodeTile(locale.language)
        SettingsListText(name, null, Modifier.weight(1f))
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { NextScreenIcon() }
    }
}

/** One saved word: first letter tile, the word, its shortcut and frequency, and an edit icon. */
@Composable
fun PersonalWordRow(word: String, shortcut: String?, weight: Int?, divider: Boolean, onClick: () -> Unit) {
    val details = listOfNotNull(
        shortcut?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.sg_pdict_shortcut, it) },
        weight?.let { stringResource(R.string.sg_pdict_weight, it) },
    ).joinToString(" · ")
    SettingsListRow(onClick, divider) {
        CodeTile(word.take(1))
        SettingsListText(word, details, Modifier.weight(1f))
        Icon(
            painterResource(R.drawable.ic_edit),
            contentDescription = stringResource(R.string.user_dict_settings_edit_dialog_title),
            tint = ScriptGo.colors.muted,
            modifier = Modifier.size(20.dp),
        )
    }
}
