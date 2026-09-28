package com.magickeyboard.manager.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.magickeyboard.manager.ui.SettingsGroup
import com.magickeyboard.manager.ui.settingsPagePadding
import com.magickeyboard.manager.ui.theme.ScriptGo
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.NextScreenIcon
import helium314.keyboard.settings.preferences.PreferenceCategory
import helium314.keyboard.settings.screens.getUserAndInternalDictionaries
import java.util.Locale

/** One language on the keyboard's dictionary screen (HeliBoard's `DictionaryScreen`, see docs/fork.md). */
data class DictionaryEntry(
    val locale: Locale,
    val title: String,
    /** Dictionaries it has, such as "Kamus utama internal, emoji". */
    val subtitle: String,
    /** A language of one of the keyboard's enabled layouts. */
    val active: Boolean,
)

/** Entries for [locales] (already sorted by HeliBoard: enabled languages first), read from disk. */
fun dictionaryEntries(context: Context, locales: List<Locale>, enabledLanguages: Collection<String>, internalLabel: String): List<DictionaryEntry> =
    locales.map { locale ->
        val (dicts, hasInternal) = getUserAndInternalDictionaries(context, locale)
        val types = dicts.mapTo(mutableListOf()) { it.name.substringBefore("_${DictionaryInfoUtils.USER_DICTIONARY_SUFFIX}") }
        if (hasInternal && !types.contains(Dictionary.TYPE_MAIN)) types.add(0, internalLabel)
        DictionaryEntry(locale, locale.localizedDisplayName(context.resources), types.joinToString(", "), locale.language in enabledLanguages)
    }

/**
 * The dictionary screen in ScriptGo style: a short explanation, the "add from file" action on its
 * own card, then the languages on cards, active ones first.
 */
@Composable
fun DictionaryList(entries: List<DictionaryEntry>, onAdd: () -> Unit, onOpen: (DictionaryEntry) -> Unit) {
    val (active, other) = entries.partition { it.active }
    Column(
        Modifier.verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .settingsPagePadding(),
    ) {
        SettingsListIntro(stringResource(R.string.sg_dict_intro))
        SettingsGroup { AddDictionaryRow(onAdd) }
        DictionaryGroup(stringResource(R.string.sg_dict_active), active, onOpen)
        DictionaryGroup(stringResource(R.string.sg_dict_other), other, onOpen)
    }
}

/** A search result: one language on its own card. */
@Composable
fun DictionarySearchResult(entry: DictionaryEntry, onOpen: (DictionaryEntry) -> Unit) {
    Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        SettingsGroup { DictionaryRow(entry, divider = false, onClick = { onOpen(entry) }) }
    }
}

@Composable
private fun DictionaryGroup(label: String, entries: List<DictionaryEntry>, onOpen: (DictionaryEntry) -> Unit) {
    if (entries.isEmpty()) return
    PreferenceCategory(label)
    SettingsGroup {
        entries.forEachIndexed { index, entry -> DictionaryRow(entry, divider = index > 0, onClick = { onOpen(entry) }) }
    }
}

@Composable
private fun AddDictionaryRow(onClick: () -> Unit) {
    val c = ScriptGo.colors
    val shape = RoundedCornerShape(14.dp)
    SettingsListRow(onClick) {
        Box(Modifier.size(40.dp).clip(shape).background(c.accent, shape), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = c.onAccent, modifier = Modifier.size(22.dp))
        }
        SettingsListText(stringResource(R.string.add_new_dictionary_title), stringResource(R.string.sg_dict_add_sub), Modifier.weight(1f))
    }
}

@Composable
private fun DictionaryRow(entry: DictionaryEntry, divider: Boolean, onClick: () -> Unit) {
    SettingsListRow(onClick, divider) {
        CodeTile(entry.locale.language)
        SettingsListText(entry.title, entry.subtitle, Modifier.weight(1f))
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { NextScreenIcon() }
    }
}
