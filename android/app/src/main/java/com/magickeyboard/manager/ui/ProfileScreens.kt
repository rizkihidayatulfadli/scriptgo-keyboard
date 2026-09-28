package com.magickeyboard.manager.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.magickeyboard.profile.Province
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.magickeyboard.manager.ui.theme.ScriptGo
import com.magickeyboard.manager.ui.theme.ScriptGoIcons
import com.magickeyboard.profile.BusinessProfile
import com.magickeyboard.profile.BusinessType
import com.magickeyboard.profile.Channel
import com.magickeyboard.profile.ChatsPerDay
import com.magickeyboard.profile.MonthlyRevenue
import com.magickeyboard.profile.ProfileOption
import com.magickeyboard.profile.Role as ProfileRole
import com.magickeyboard.profile.TeamSize
import com.magickeyboard.sync.SupabaseSync
import helium314.keyboard.latin.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Business profile (mockup 3a, 3b), three short pages. Every question must be answered: the data
 * feeds business analysis, so there is no skip ([required] also blocks leaving with Back, as in
 * onboarding). Location is detected from the phone's approximate position when allowed.
 */
@Composable
fun BusinessProfileScreen(required: Boolean = false, onBack: () -> Unit = {}, onDone: () -> Unit) {
    val context = LocalContext.current
    val sync = remember { SupabaseSync.get(context) }
    val scope = rememberCoroutineScope()
    val c = ScriptGo.colors
    var draft by remember { mutableStateOf(sync.cachedProfile() ?: BusinessProfile()) }
    var whatsappInput by remember { mutableStateOf(draft.whatsapp.removePrefix("+62")) }
    var step by remember { mutableIntStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showMissing by remember { mutableStateOf(false) }

    // Fresh copy from the server when there is one (another phone may have filled it in).
    LaunchedEffect(Unit) {
        if (sync.signedInEmail() == null) return@LaunchedEffect
        withContext(Dispatchers.IO) { runCatching { sync.loadProfile() }.getOrNull() }?.let {
            draft = it
            whatsappInput = it.whatsapp.removePrefix("+62")
        }
    }

    val current = draft.copy(whatsapp = whatsappInput)
    val stepFields = mapOf(
        1 to listOf("businessName", "businessType", "businessTypeOther", "role"),
        2 to listOf("province", "city"),
        3 to listOf("channels", "teamSize", "chatsPerDay", "monthlyRevenue", "whatsapp"),
    )
    val missingHere = current.missing().filter { it in stepFields.getValue(step) }
    fun isMissing(field: String) = showMissing && field in missingHere

    fun next() {
        if (missingHere.isNotEmpty()) {
            showMissing = true
            error = context.getString(
                if ("whatsapp" in missingHere && whatsappInput.isNotBlank()) R.string.sg_profile_bad_whatsapp else R.string.sg_profile_missing,
            )
            return
        }
        error = null
        showMissing = false
        if (step < 3) {
            step++
            return
        }
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { sync.saveProfile(current) } }
            busy = false
            // Saved on the phone either way; an upload that failed is retried after the next sign-in or edit.
            result.onSuccess { onDone() }.onFailure {
                if (sync.cachedProfile()?.isComplete() == true) onDone() else error = it.message ?: context.getString(R.string.sg_save_failed)
            }
        }
    }

    BackHandler {
        when {
            step > 1 -> step--
            !required -> onBack()
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg).imePadding()) {
        Row(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Top + androidx.compose.foundation.layout.WindowInsetsSides.Horizontal))
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (step > 1) CircleIconButton(ScriptGoIcons.Back, stringResource(R.string.sg_back)) { step-- }
            else if (!required) CircleIconButton(ScriptGoIcons.Back, stringResource(R.string.sg_back), onClick = onBack)
            StepProgress(step = step, total = 3, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when (step) {
                1 -> {
                    Text(stringResource(R.string.sg_profile_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.sg_profile_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.muted,
                    )
                    // The role comes first: it decides what the name below is the name of.
                    Question(stringResource(R.string.sg_profile_role), isMissing("role")) {
                        Segmented(ProfileRole.options, ProfileRole.migrate(draft.role)) { draft = draft.copy(role = it) }
                    }
                    OutlinedTextField(
                        value = draft.businessName,
                        onValueChange = { draft = draft.copy(businessName = it.take(BusinessProfile.MAX_NAME)) },
                        label = { Text(stringResource(nameLabel(ProfileRole.migrate(draft.role)))) },
                        isError = isMissing("businessName"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PickerField(
                        label = stringResource(R.string.sg_profile_type),
                        value = BusinessType.options.firstOrNull { it.code == draft.businessType }?.text(context),
                        options = BusinessType.options.map { it.text(context) },
                        isError = isMissing("businessType"),
                        searchHint = stringResource(R.string.sg_profile_type_search),
                    ) { index -> draft = draft.copy(businessType = BusinessType.options[index].code) }
                    if (draft.businessType == BusinessType.OTHER) {
                        OutlinedTextField(
                            value = draft.businessTypeOther,
                            onValueChange = { draft = draft.copy(businessTypeOther = it.take(BusinessProfile.MAX_TYPE_OTHER)) },
                            label = { Text(stringResource(R.string.sg_profile_type_other)) },
                            placeholder = { Text(stringResource(R.string.sg_profile_type_other_hint)) },
                            isError = isMissing("businessTypeOther"),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                2 -> LocationStep(
                    city = draft.city,
                    province = draft.province,
                    missingCity = isMissing("city"),
                    missingProvince = isMissing("province"),
                    onChange = { city, province -> draft = draft.copy(city = city.take(BusinessProfile.MAX_CITY), province = province) },
                )
                else -> {
                    Text(stringResource(R.string.sg_profile_selling), style = MaterialTheme.typography.headlineMedium)
                    Question(stringResource(R.string.sg_profile_channels), isMissing("channels")) {
                        ChoiceChips(Channel.options, selected = draft.channels.toSet()) { code ->
                            draft = draft.copy(channels = if (code in draft.channels) draft.channels - code else draft.channels + code)
                        }
                    }
                    Question(stringResource(R.string.sg_profile_team), isMissing("teamSize")) {
                        Segmented(TeamSize.options, draft.teamSize) { draft = draft.copy(teamSize = it) }
                    }
                    Question(stringResource(R.string.sg_profile_chats), isMissing("chatsPerDay")) {
                        Segmented(ChatsPerDay.options, draft.chatsPerDay) { draft = draft.copy(chatsPerDay = it) }
                    }
                    Question(stringResource(R.string.sg_profile_revenue), isMissing("monthlyRevenue")) {
                        ChoiceChips(MonthlyRevenue.options, selected = setOfNotNull(draft.monthlyRevenue)) { code ->
                            draft = draft.copy(monthlyRevenue = code)
                        }
                    }
                    OutlinedTextField(
                        value = whatsappInput,
                        onValueChange = { whatsappInput = it.filter { ch -> ch.isDigit() || ch == ' ' || ch == '-' }.take(20) },
                        label = { Text(stringResource(R.string.sg_profile_whatsapp)) },
                        prefix = { Text("+62 ") },
                        isError = isMissing("whatsapp"),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .clickable { draft = draft.copy(whatsappOptIn = !draft.whatsappOptIn) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = draft.whatsappOptIn, onCheckedChange = { draft = draft.copy(whatsappOptIn = it) })
                        Text(stringResource(R.string.sg_profile_optin), style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        Modifier.fillMaxWidth().background(c.accentSoft, RoundedCornerShape(16.dp)).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(ScriptGoIcons.Info, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(R.string.sg_profile_whatsapp_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.ink,
                        )
                    }
                }
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.danger) }
        }
        Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(20.dp)) {
            Button(onClick = { next() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(if (step < 3) R.string.sg_next else if (busy) R.string.sg_saving else R.string.sg_save))
            }
        }
    }
}

/**
 * Page 2: detect the city and province from the phone's approximate location, or pick them.
 * Only the names are kept; the position itself is never saved or sent.
 */
@Composable
private fun LocationStep(
    city: String,
    province: String,
    missingCity: Boolean,
    missingProvince: Boolean,
    onChange: (city: String, province: String) -> Unit,
) {
    val context = LocalContext.current
    val c = ScriptGo.colors
    val scope = rememberCoroutineScope()
    var detecting by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    fun detect() {
        detecting = true
        note = null
        scope.launch {
            val place = runCatching { detectPlace(context) }.getOrNull()
            detecting = false
            if (place != null) {
                onChange(place.city.ifBlank { city }, place.province)
                note = context.getString(R.string.sg_location_detected)
            } else {
                note = context.getString(R.string.sg_location_failed)
            }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) detect() else note = context.getString(R.string.sg_location_denied)
    }

    Text(stringResource(R.string.sg_location_title), style = MaterialTheme.typography.headlineMedium)
    Text(
        stringResource(R.string.sg_location_note),
        style = MaterialTheme.typography.bodyMedium,
        color = c.muted,
    )
    OutlinedButton(
        onClick = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (granted) detect() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        },
        enabled = !detecting,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (detecting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.accentSolid)
        else Icon(ScriptGoIcons.MapPin, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(if (detecting) R.string.sg_location_detecting else R.string.sg_location_detect))
    }
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted) }
    PickerField(
        label = stringResource(R.string.sg_province),
        value = province.ifEmpty { null },
        options = Province.names,
        isError = missingProvince,
        searchHint = stringResource(R.string.sg_province_search),
    ) { index -> onChange(city, Province.names[index]) }
    OutlinedTextField(
        value = city,
        onValueChange = { onChange(it, province) },
        label = { Text(stringResource(R.string.sg_city)) },
        placeholder = { Text(stringResource(R.string.sg_city_example)) },
        isError = missingCity,
        leadingIcon = { Icon(ScriptGoIcons.MapPin, contentDescription = null, modifier = Modifier.size(20.dp)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A read-only field that opens a searchable list (a dropdown that stays usable with 30+ options).
 * [onPick] receives the index in [options].
 */
@Composable
fun PickerField(
    label: String,
    value: String?,
    options: List<String>,
    isError: Boolean = false,
    searchHint: String? = null,
    onPick: (Int) -> Unit,
) {
    val c = ScriptGo.colors
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = value.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text(stringResource(R.string.sg_pick)) },
            isError = isError,
            trailingIcon = { Icon(ScriptGoIcons.ChevronDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // The whole field opens the list (a read-only text field would otherwise just take focus).
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)).clickable { open = true })
    }
    if (open) {
        var query by remember { mutableStateOf("") }
        val shown = options.withIndex().filter { query.isBlank() || it.value.contains(query.trim(), ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text(searchHint ?: stringResource(R.string.sg_search)) },
                        leadingIcon = { Icon(ScriptGoIcons.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(shown, key = { it.index }) { (index, option) ->
                            val selected = option == value
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) c.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable { onPick(index); open = false }
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(option, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                if (selected) Icon(ScriptGoIcons.Check, contentDescription = stringResource(R.string.sg_selected), tint = c.accentInk, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.sg_close)) } },
        )
    }
}

/** A question label above its answer control; red when it still needs an answer. */
@Composable
private fun Question(title: String, missing: Boolean = false, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = if (missing) ScriptGo.colors.danger else ScriptGo.colors.ink)
        content()
    }
}

/** Progress bars for a short multi-page form, with "1 dari 2" below. */
@Composable
fun StepProgress(step: Int, total: Int, modifier: Modifier = Modifier) {
    val c = ScriptGo.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { i ->
                Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(if (i < step) c.accentSolid else c.line))
            }
        }
        Text(stringResource(R.string.sg_step_of, step, total), style = MaterialTheme.typography.labelLarge, color = c.muted)
    }
}

/** The name question for a role: a business, a company, a brand, or a school. */
private fun nameLabel(role: String?): Int = when (role) {
    ProfileRole.EMPLOYEE -> R.string.sg_profile_name_employee
    ProfileRole.FREELANCE -> R.string.sg_profile_name_freelance
    ProfileRole.STUDENT -> R.string.sg_profile_name_student
    else -> R.string.sg_profile_name
}

/** Wrapping chips; selected ones are filled with ink. [onToggle] receives the tapped code. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceChips(options: List<ProfileOption>, selected: Set<String>, onToggle: (String) -> Unit) {
    val c = ScriptGo.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val on = option.code in selected
            Box(
                Modifier.heightIn(min = 44.dp).clip(CircleShape)
                    .background(if (on) c.ink else c.card)
                    .border(1.5.dp, if (on) c.ink else c.line, CircleShape)
                    .semantics { this.selected = on; role = Role.Checkbox }
                    .clickable { onToggle(option.code) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(option.text(LocalContext.current), style = MaterialTheme.typography.labelLarge, color = if (on) c.onInk else c.ink)
            }
        }
    }
}

/** A segmented single choice; tapping the selected segment clears it. */
@Composable
fun Segmented(options: List<ProfileOption>, selected: String?, onSelect: (String?) -> Unit) {
    val c = ScriptGo.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val on = option.code == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) c.card else c.bg)
                    .semantics { this.selected = on; role = Role.RadioButton }
                    .clickable { onSelect(option.code.takeIf { !on }) }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(option.text(LocalContext.current), style = MaterialTheme.typography.labelLarge, color = c.ink, textAlign = TextAlign.Center)
            }
        }
    }
}
