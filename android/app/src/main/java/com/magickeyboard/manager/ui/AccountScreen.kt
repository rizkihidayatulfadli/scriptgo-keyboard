@file:OptIn(ExperimentalMaterial3Api::class)

package com.magickeyboard.manager.ui

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.magickeyboard.manager.AppLanguage
import com.magickeyboard.manager.ui.theme.ScriptGoIcons
import com.magickeyboard.manager.data.ManagerRepository
import com.magickeyboard.plan.AccountPlan
import com.magickeyboard.plan.FreeLimits
import com.magickeyboard.plan.PlanKind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.magickeyboard.manager.ui.theme.ScriptGo
import com.magickeyboard.sync.PasskeyCeremony
import com.magickeyboard.sync.SupabaseSync
import helium314.keyboard.latin.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.magickeyboard.manager.AppTheme

/**
 * Akun tab (mockup 3c): sign-in choices when signed out; otherwise the business, sync, and account
 * actions. Sign-in itself happens on [Screen.SignInEmail] / [Screen.SignInCode] or in the browser.
 */
@Composable
fun AccountScreen(nav: TabNav, navigate: (Screen) -> Unit, openKeyboardSettings: () -> Unit, onSignedIn: () -> Unit = {}) {
    val context = LocalContext.current
    val sync = remember { SupabaseSync.get(context) }
    val scope = rememberCoroutineScope()
    val c = ScriptGo.colors
    var email by remember { mutableStateOf(sync.signedInEmail()) }
    var lastSync by remember { mutableStateOf(sync.lastSyncAt()) }
    var profile by remember { mutableStateOf(sync.cachedProfile()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var referral by remember { mutableStateOf(sync.cachedReferral()) }
    var plan by remember { mutableStateOf(sync.cachedPlan()) }

    // The "Ajak teman" link: created on first use, and its friend count refreshed on each visit.
    LaunchedEffect(email) {
        if (email != null) withContext(Dispatchers.IO) { runCatching { sync.loadReferral() } }.onSuccess { referral = it }
    }
    // The plan (beta, trial, pro, free), refreshed on each visit; the cached one shows meanwhile.
    LaunchedEffect(email) {
        if (email != null) withContext(Dispatchers.IO) { runCatching { sync.loadPlan() } }.onSuccess { plan = it }
    }

    // Picks up a sign-in finished in the browser or a profile saved on another screen.
    LifecycleResumeEffect(Unit) {
        email = sync.signedInEmail()
        lastSync = sync.lastSyncAt()
        profile = sync.cachedProfile()
        onPauseOrDispose { }
    }

    /** Runs [action] off the main thread, showing its result in [message] or its error in [error]. */
    fun perform(action: () -> String?) {
        busy = true
        message = null
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { action() } }
            result.onSuccess { message = it }.onFailure { error = it.message ?: context.getString(R.string.sg_error_generic) }
            email = sync.signedInEmail()
            lastSync = sync.lastSyncAt()
            profile = sync.cachedProfile()
            busy = false
        }
    }

    TabScaffold(title = stringResource(R.string.sg_tab_account), nav = nav, onAdd = null) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val signedIn = email
            if (signedIn == null) {
                SignInCard(onEmail = { navigate(Screen.SignInEmail) }, message = error, onError = { error = it }, onSignedIn = onSignedIn)
                SectionLabel(stringResource(R.string.sg_account_app))
                BCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                    AccountRow(icon = ScriptGoIcons.Keyboard, title = stringResource(R.string.sg_keyboard_settings), subtitle = stringResource(R.string.sg_keyboard_settings_sub), onClick = openKeyboardSettings)
                    HorizontalDivider(color = c.line)
                    LanguageRow()
                    HorizontalDivider(color = c.line)
                    ThemeRow()
                    HorizontalDivider(color = c.line)
                    AccountRow(icon = ScriptGoIcons.Store, title = stringResource(R.string.sg_business_profile), subtitle = profile?.summary(context)?.ifBlank { null } ?: stringResource(R.string.sg_business_profile_complete), onClick = { navigate(Screen.BusinessProfile) })
                }
                HelpSection(onOpenHelp = { navigate(Screen.Help) })
                return@Column
            }
            // Who is signed in: the name and email on the account (edited in Data pribadi below).
            val fullName = sync.fullName().ifBlank { null }
            BCard(Modifier.fillMaxWidth(), radius = 28.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LetterBadge(fullName ?: signedIn, size = 52.dp)
                    Column(Modifier.weight(1f)) {
                        Text(fullName ?: signedIn.substringBefore('@'), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(
                            signedIn + when (sync.signInMethod()) { "google" -> " · Google"; "email" -> " · Email"; "passkey" -> " · Passkey"; else -> "" },
                            style = MaterialTheme.typography.bodySmall,
                            color = c.muted,
                            maxLines = 1,
                        )
                    }
                }
            }
            val freeUses = remember(plan) { ManagerRepository.get(context).freeUses() }
            plan?.let { PlanCard(it.at(java.time.Instant.now()), freeUsesLeft = FreeLimits.usesLeft(freeUses)) }
            referral?.let { ReferralCard(it) }
            SectionLabel(stringResource(R.string.sg_tab_account))
            BCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                AccountRow(
                    icon = ScriptGoIcons.User,
                    title = stringResource(R.string.sg_personal_data),
                    subtitle = sync.fullName().ifBlank { stringResource(R.string.sg_personal_data_sub) },
                    onClick = { navigate(Screen.PersonalData) },
                )
                HorizontalDivider(color = c.line)
                AccountRow(
                    icon = ScriptGoIcons.KeyRound,
                    title = stringResource(R.string.sg_password),
                    subtitle = stringResource(if (sync.hasPassword()) R.string.sg_password_change else R.string.sg_password_create_sub),
                    onClick = { navigate(Screen.Password) },
                )
                if (PasskeyCeremony.supported) {
                    HorizontalDivider(color = c.line)
                    AccountRow(
                        icon = ScriptGoIcons.Fingerprint,
                        title = stringResource(R.string.sg_passkey),
                        subtitle = stringResource(R.string.sg_passkey_sub),
                        onClick = { navigate(Screen.Passkeys) },
                    )
                }
            }
            SectionLabel(stringResource(R.string.sg_account_business))
            BCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                AccountRow(
                    icon = ScriptGoIcons.Store,
                    title = stringResource(R.string.sg_business_profile),
                    subtitle = profile?.summary(context)?.ifBlank { null } ?: stringResource(R.string.sg_business_profile_sub),
                    onClick = { navigate(Screen.BusinessProfile) },
                )
            }
            SectionLabel(stringResource(R.string.sg_account_app))
            BCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                AccountRow(icon = ScriptGoIcons.Keyboard, title = stringResource(R.string.sg_keyboard_settings), subtitle = stringResource(R.string.sg_keyboard_settings_sub), onClick = openKeyboardSettings)
                    HorizontalDivider(color = c.line)
                    LanguageRow()
                    HorizontalDivider(color = c.line)
                    ThemeRow()
                HorizontalDivider(color = c.line)
                AccountRow(
                    icon = ScriptGoIcons.Sync,
                    title = stringResource(R.string.sg_sync),
                    subtitle = if (busy) stringResource(R.string.sg_syncing) else stringResource(R.string.sg_sync_last, lastSync?.let {
                        DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().lowercase()
                    } ?: stringResource(R.string.sg_sync_never)),
                    trailing = {
                        OutlinedButton(enabled = !busy, onClick = {
                            perform {
                                val pulled = sync.syncNow()
                                if (pulled > 0) context.getString(R.string.sg_sync_done_n, pulled) else context.getString(R.string.sg_sync_done)
                            }
                        }) { Text(stringResource(R.string.sg_sync_now)) }
                    },
                )
            }
            HelpSection(onOpenHelp = { navigate(Screen.Help) })
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.ink) }
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.danger) }
            Text(
                stringResource(R.string.sg_sync_note),
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = { perform { sync.signOut(); context.getString(R.string.sg_signed_out) } },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(ScriptGoIcons.LogOut, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sg_sign_out), maxLines = 1)
                }
                TextButton(enabled = !busy, onClick = { confirmDelete = true }, modifier = Modifier.weight(1f).height(48.dp)) {
                    Icon(ScriptGoIcons.Trash, contentDescription = null, tint = c.danger, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sg_delete_account), color = c.danger, maxLines = 1)
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.sg_delete_account_q)) },
            text = {
                Text(
                    stringResource(R.string.sg_delete_account_body)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    perform { sync.deleteAccount(); context.getString(R.string.sg_account_deleted) }
                }) { Text(stringResource(R.string.sg_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.sg_cancel)) } },
        )
    }
}

/** A row inside an account card: tinted icon, title, subtitle, and a trailing control or chevron. */
@Composable
private fun AccountRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TintIcon(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ScriptGo.colors.muted)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(ScriptGoIcons.ChevronRight, contentDescription = null, tint = ScriptGo.colors.muted, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * The account's plan, as neutral info only: Google Play forbids buttons, links, or calls to buy
 * outside the app (PRD §9), so there is no "upgrade" here.
 */
@Composable
private fun PlanCard(plan: AccountPlan, freeUsesLeft: Int) {
    val context = LocalContext.current
    val date = { end: java.time.Instant ->
        DateUtils.formatDateTime(context, end.toEpochMilli(), DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR)
    }
    val (title, subtitle) = when (plan.kind) {
        PlanKind.BETA -> stringResource(R.string.sg_plan_beta) to stringResource(R.string.sg_plan_beta_sub)
        PlanKind.TRIAL -> {
            val days = plan.daysLeft(java.time.Instant.now()) ?: 0
            stringResource(R.string.sg_plan_trial) to
                if (days > 0) stringResource(R.string.sg_plan_trial_sub, days, date(plan.trialEndsAt!!)) else stringResource(R.string.sg_plan_trial_today)
        }
        PlanKind.PRO -> stringResource(R.string.sg_plan_pro) to
            (plan.proUntil?.let { stringResource(R.string.sg_plan_pro_until, date(it)) } ?: stringResource(R.string.sg_plan_pro_sub))
        PlanKind.FREE -> stringResource(R.string.sg_plan_free) to
            stringResource(R.string.sg_plan_free_left, freeUsesLeft, FreeLimits.USES) + " " + stringResource(R.string.sg_plan_free_sub)
    }
    BCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
        AccountRow(icon = ScriptGoIcons.Sparkles, title = "${stringResource(R.string.sg_plan_label)}: $title", subtitle = subtitle)
    }
}

/** "Ajak teman": the account's referral link with Copy and Share (the Android share sheet). */
@Composable
private fun ReferralCard(referral: SupabaseSync.Referral) {
    val context = LocalContext.current
    val c = ScriptGo.colors
    var copied by remember(referral.code) { mutableStateOf(false) }
    BCard(Modifier.fillMaxWidth(), radius = 28.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TintIcon(ScriptGoIcons.Contacts)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.sg_referral_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.sg_referral_sub), style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
            Text(
                referral.link,
                style = MaterialTheme.typography.bodyMedium,
                color = c.accentInk,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.accentSoft).padding(horizontal = 16.dp, vertical = 12.dp),
            )
            if (referral.joined > 0) {
                Text(stringResource(R.string.sg_referral_joined, referral.joined), style = MaterialTheme.typography.bodySmall, color = c.ink)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText(context.getString(R.string.sg_referral_title), referral.link))
                        copied = true
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text(stringResource(if (copied) R.string.sg_referral_copied else R.string.sg_referral_copy), maxLines = 1)
                }
                Button(
                    onClick = {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_TEXT, context.getString(R.string.sg_referral_share_text, referral.link))
                        context.startActivity(android.content.Intent.createChooser(send, context.getString(R.string.sg_referral_share)))
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text(stringResource(R.string.sg_referral_share), maxLines = 1)
                }
            }
        }
    }
}

/** Bantuan: contact and the privacy policy. */
@Composable
private fun HelpSection(onOpenHelp: () -> Unit) {
    val context = LocalContext.current
    val c = ScriptGo.colors
    SectionLabel(stringResource(R.string.sg_help))
    BCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
        AccountRow(
            icon = ScriptGoIcons.MessageCircle,
            title = stringResource(R.string.sg_help_center),
            subtitle = stringResource(R.string.sg_help_center_sub),
            onClick = onOpenHelp,
        )
        HorizontalDivider(color = c.line)
        AccountRow(
            icon = ScriptGoIcons.LifeBuoy,
            title = stringResource(R.string.sg_contact_us),
            subtitle = "halo@scriptgo.id",
            onClick = {
                runCatching {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:halo@scriptgo.id")))
                }
            },
        )
        HorizontalDivider(color = c.line)
        AccountRow(
            icon = ScriptGoIcons.Shield,
            title = stringResource(R.string.sg_privacy_policy),
            subtitle = "scriptgo.id/privacy",
            onClick = {
                runCatching {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://scriptgo.id/privacy.html")))
                }
            },
        )
    }
}


/** Akun → Tema: light, dark, or following the phone, as three segments that apply at once. */
@Composable
private fun ThemeRow() {
    val context = LocalContext.current
    val c = ScriptGo.colors
    val current = AppTheme.current(context)
    val choices = listOf(
        AppTheme.LIGHT to R.string.sg_theme_light,
        AppTheme.DARK to R.string.sg_theme_dark,
        AppTheme.SYSTEM to R.string.sg_theme_system,
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TintIcon(ScriptGoIcons.Palette)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sg_theme), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.sg_theme_sub), style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.bg).border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            choices.forEach { (code, label) ->
                val on = code == current
                Box(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (on) c.card else c.bg)
                        .semantics { selected = on; role = Role.RadioButton }
                        .clickable { AppTheme.set(context, code) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = if (on) c.ink else c.muted)
                }
            }
        }
    }
}

/** Aplikasi → Bahasa: Indonesian (default), English, or the phone's language; restarts the screen. */
@Composable
private fun LanguageRow() {
    val context = LocalContext.current
    var choosing by remember { mutableStateOf(false) }
    val current = remember { AppLanguage.current(context) }
    val choices = listOf(
        AppLanguage.INDONESIAN to R.string.sg_language_indonesian,
        AppLanguage.ENGLISH to R.string.sg_language_english,
        AppLanguage.SYSTEM to R.string.sg_language_system,
    )
    AccountRow(
        icon = ScriptGoIcons.Globe,
        title = stringResource(R.string.sg_language),
        subtitle = stringResource(choices.first { it.first == current }.second),
        onClick = { choosing = true },
    )
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.sg_language)) },
            text = {
                Column {
                    choices.forEach { (code, label) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                choosing = false
                                if (code != current) {
                                    AppLanguage.set(context, code)
                                    context.findActivity()?.recreate()
                                }
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = code == current, onClick = null)
                            Text("  " + stringResource(label), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.sg_close)) } },
        )
    }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
