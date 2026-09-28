package com.magickeyboard.manager.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.magickeyboard.keyboard.TemplateSuggestions
import com.magickeyboard.manager.ui.theme.ScriptGo
import com.magickeyboard.manager.ui.theme.ScriptGoIcons
import com.magickeyboard.sync.SupabaseSync
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private enum class Page { Welcome, Enable, Select, Try, Profile, Done }

/**
 * First-run guide (PRD 6.4), one screen per step with an animation that shows what to tap (mockup
 * 1a to 1e): welcome, enable the keyboard, select it, try a starter template, then offer an account.
 * Steps move on by themselves when Android reports them done. [onDone] gets the screen to open next
 * (the email sign-in) or null.
 */
@Composable
fun OnboardingScreen(onDone: (next: Screen?) -> Unit) {
    val context = LocalContext.current
    val repo = rememberRepository()
    val imm = remember { context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }
    var enabled by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }
    var practice by remember { mutableStateOf("") }
    var page by remember { mutableStateOf(Page.Welcome) }
    val revision by repo.changes.collectAsState()

    fun refresh() {
        enabled = UncachedInputMethodManagerUtils.isThisImeEnabled(context, imm)
        current = UncachedInputMethodManagerUtils.isThisImeCurrent(context, imm)
    }
    fun firstOpenStep(): Page = when {
        !enabled -> Page.Enable
        !current -> Page.Select
        !tried -> Page.Try
        SupabaseSync.get(context).needsProfilePrompt() -> Page.Profile
        else -> Page.Done
    }

    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { repo.seedStarterTemplates() } }
    LaunchedEffect(revision) { tried = withContext(Dispatchers.IO) { repo.usageTotals().uses > 0 } }
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }
    // The system keyboard picker does not pause this screen, so poll while waiting for it.
    LaunchedEffect(page) {
        while (page == Page.Select && !current) {
            delay(700)
            refresh()
        }
    }
    LaunchedEffect(practice) { refresh() }

    // Move on by itself once Android (or the keyboard) reports the step done.
    LaunchedEffect(enabled, current, tried) {
        val next = when {
            page == Page.Enable && enabled -> firstOpenStep()
            page == Page.Select && current -> firstOpenStep()
            page == Page.Try && tried -> Page.Profile
            else -> null
        }
        if (next != null) {
            delay(450) // let the checkmark land before the page slides away
            page = next
        }
    }

    BackHandler(enabled = page != Page.Welcome) { page = Page.values()[page.ordinal - 1] }
    val back = { page = Page.values()[(page.ordinal - 1).coerceAtLeast(0)] }

    Box(Modifier.fillMaxSize().background(ScriptGo.colors.bg)) {
        GlowBackdrop()
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(tween(320)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(320)))
                    .togetherWith(slideOutHorizontally(tween(240)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(200)))
            },
            label = "onboarding",
        ) { shown ->
            when (shown) {
                Page.Welcome -> WelcomePage(onStart = { page = firstOpenStep() })
                Page.Enable -> StepPage(
                    step = 1,
                    onBack = back,
                    title = stringResource(R.string.sg_onb_enable_title),
                    lead = stringResource(R.string.sg_onb_enable_lead),
                    illustration = { EnableIllustration(done = enabled) },
                    note = {
                        Note(
                            stringResource(R.string.sg_onb_enable_note),
                        )
                    },
                    action = stringResource(R.string.sg_onb_open_settings),
                    onAction = { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) },
                )
                Page.Select -> StepPage(
                    step = 2,
                    onBack = back,
                    title = stringResource(R.string.sg_onb_select_title),
                    lead = stringResource(R.string.sg_onb_select_lead),
                    illustration = { SelectIllustration(done = current) },
                    action = stringResource(R.string.sg_onb_pick_keyboard),
                    onAction = { imm.showInputMethodPicker() },
                )
                Page.Try -> TryPage(
                    onBack = back,
                    practice = practice,
                    onPractice = { practice = it },
                    onSkip = { page = Page.Profile },
                )
                // Required: the business profile feeds business analysis, so it has no skip.
                Page.Profile -> BusinessProfileScreen(required = true, onDone = { page = Page.Done })
                Page.Done -> DonePage(practiced = tried, onDone = onDone)
            }
        }
    }
}

/** Mockup 1a: the promise in one line and a demo that plays by itself. */
@Composable
private fun WelcomePage(onStart: () -> Unit) {
    OnboardingPage(
        top = { ScriptGoMark(Modifier.padding(top = 12.dp).size(56.dp)) },
        bottom = {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.sg_onb_start)) }
        },
    ) {
        Text(stringResource(R.string.sg_onb_welcome_title), style = MaterialTheme.typography.headlineLarge)
        Text(
            buildAnnotatedString {
                append(stringResource(R.string.sg_onb_type))
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = ScriptGo.colors.accentInk)) { append("/ongkir") }
                append(stringResource(R.string.sg_onb_welcome_lead))
            },
            style = MaterialTheme.typography.bodyLarge,
            color = ScriptGo.colors.muted,
        )
        ChatDemo(
            question = "Kak, ongkir ke Bandung berapa ya?",
            trigger = "/ongkir",
            chip = "Info ongkir",
            reply = "Halo Kak Rina! Ongkir ke Bandung Rp18.000, sampai 2 hari ya.",
        )
    }
}

/** Mockup 1b and 1c: progress, an animated picture of what to tap, and one action. */
@Composable
private fun StepPage(
    step: Int,
    onBack: () -> Unit,
    title: String,
    lead: String,
    illustration: @Composable () -> Unit,
    action: String,
    onAction: () -> Unit,
    note: @Composable () -> Unit = {},
) {
    OnboardingPage(
        top = { StepTopBar(step, onBack) },
        bottom = { Button(onClick = onAction, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(action) } },
    ) {
        illustration()
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(lead, style = MaterialTheme.typography.bodyLarge, color = ScriptGo.colors.muted)
        note()
    }
}

/** Mockup 1d: practice in a real field with the ScriptGo keyboard. */
@Composable
private fun TryPage(onBack: () -> Unit, practice: String, onPractice: (String) -> Unit, onSkip: () -> Unit) {
    OnboardingPage(
        top = { StepTopBar(3, onBack) },
        bottom = { TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.sg_onb_skip)) } },
    ) {
        Text(stringResource(R.string.sg_onb_try_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            buildAnnotatedString {
                append(stringResource(R.string.sg_onb_type))
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, color = ScriptGo.colors.accentInk)) { append("/salam") }
                append(stringResource(R.string.sg_onb_try_lead))
            },
            style = MaterialTheme.typography.bodyLarge,
            color = ScriptGo.colors.muted,
        )
        ChatDemo(
            question = null,
            trigger = "/salam",
            chip = "Salam pembuka",
            reply = "Halo Kak, terima kasih sudah menghubungi kami!",
        )
        OutlinedTextField(
            value = practice,
            onValueChange = onPractice,
            placeholder = { Text(stringResource(R.string.sg_onb_try_placeholder)) },
            keyboardOptions = KeyboardOptions(platformImeOptions = PlatformImeOptions(TemplateSuggestions.PRACTICE_FIELD_OPTION)),
            minLines = 2,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.sg_onb_starters),
            style = MaterialTheme.typography.bodySmall,
            color = ScriptGo.colors.muted,
        )
    }
}

/** Mockup 1e: celebrate the first insert, then offer an account (never required). */
@Composable
private fun DonePage(practiced: Boolean, onDone: (Screen?) -> Unit) {
    val c = ScriptGo.colors
    var error by remember { mutableStateOf<String?>(null) }
    val google = rememberGoogleSignIn { error = it }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val badgeScale by animateFloatAsState(
        if (shown) 1f else 0.4f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "badge",
    )
    OnboardingPage(
        top = {},
        bottom = {
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.danger) }
            GoogleButton(onClick = {
                onDone(null) // the browser returns through AuthCallbackActivity to the Akun tab
                google()
            })
            Button(onClick = { onDone(Screen.SignInEmail) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(ScriptGoIcons.Mail, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.sg_continue_email))
            }
            TextButton(onClick = { onDone(null) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text(stringResource(R.string.sg_later)) }
        },
    ) {
        Box(
            Modifier.padding(top = 24.dp).size(72.dp).scale(badgeScale).clip(RoundedCornerShape(24.dp)).background(c.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(ScriptGoIcons.Check, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(36.dp))
        }
        Text(
            stringResource(if (practiced) R.string.sg_onb_done_practiced else R.string.sg_onb_done_title),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            stringResource(if (practiced) R.string.sg_onb_done_lead_practiced else R.string.sg_onb_done_lead),
            style = MaterialTheme.typography.bodyLarge,
            color = c.muted,
        )
        BCard(Modifier.fillMaxWidth(), radius = 24.dp, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
            Benefit(ScriptGoIcons.Sync, stringResource(R.string.sg_benefit_sync), stringResource(R.string.sg_benefit_sync_sub), 0, shown)
            HorizontalDivider(color = c.line)
            Benefit(ScriptGoIcons.Shield, stringResource(R.string.sg_benefit_backup), stringResource(R.string.sg_benefit_backup_sub), 1, shown)
        }
    }
}

@Composable
private fun Benefit(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String, index: Int, shown: Boolean) {
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(300, delayMillis = 250 + index * 120)) +
            slideInVertically(tween(300, delayMillis = 250 + index * 120)) { it / 3 },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TintIcon(icon)
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = ScriptGo.colors.muted)
            }
        }
    }
}

/** Page frame: a top slot, scrolling content, and actions pinned to the bottom. */
@Composable
private fun OnboardingPage(
    top: @Composable () -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            top()
            content()
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = bottom,
        )
    }
}

@Composable
private fun StepTopBar(step: Int, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircleIconButton(ScriptGoIcons.Back, stringResource(R.string.sg_back), onClick = onBack)
        val progress by animateFloatAsState(step.toFloat(), tween(400), label = "progress")
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    val fill = (progress - i).coerceIn(0f, 1f)
                    Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(ScriptGo.colors.line)) {
                        Box(Modifier.fillMaxWidth(fill).height(6.dp).clip(CircleShape).background(ScriptGo.colors.accentSolid))
                    }
                }
            }
            Text(stringResource(R.string.sg_onb_step, step, 3), style = MaterialTheme.typography.labelLarge, color = ScriptGo.colors.muted)
        }
    }
}

@Composable
private fun Note(text: String) {
    val c = ScriptGo.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.accentSoft).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(ScriptGoIcons.Shield, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.ink)
    }
}

/** True when the phone's "remove animations" setting is on; demos then show their end state. */
@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** A finger dot that presses down while [pressed]. */
@Composable
private fun FingerDot(pressed: Boolean, visible: Boolean, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(if (pressed) 0.8f else 1.15f, tween(180), label = "finger")
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(220), label = "fingerAlpha")
    Box(
        modifier.size(30.dp).scale(scale).alpha(alpha).clip(CircleShape)
            .background(ScriptGo.colors.ink.copy(alpha = 0.18f)),
    )
}

/** Mockup 1b: the ScriptGo switch turns on under a tapping finger, on a loop until it is really on. */
@Composable
private fun EnableIllustration(done: Boolean) {
    val reduce = rememberReduceMotion()
    var on by remember { mutableStateOf(done || reduce) }
    var finger by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(done, reduce) {
        if (done || reduce) { on = true; finger = false; return@LaunchedEffect }
        while (true) {
            on = false; finger = false; pressed = false
            delay(900)
            finger = true
            delay(450)
            pressed = true
            delay(160)
            on = true
            delay(200)
            pressed = false
            delay(600)
            finger = false
            delay(1300)
        }
    }
    BCard(Modifier.fillMaxWidth(), radius = 24.dp) {
        Text(stringResource(R.string.sg_onb_onscreen_keyboards), style = MaterialTheme.typography.labelMedium, color = ScriptGo.colors.muted)
        SystemRow("Gboard") { Switch(checked = true, onCheckedChange = null) }
        HorizontalDivider(color = ScriptGo.colors.line)
        SystemRow("ScriptGo") {
            Box(contentAlignment = Alignment.Center) {
                Switch(checked = on, onCheckedChange = null)
                FingerDot(pressed, finger)
            }
        }
    }
}

/** Mockup 1c: the choice moves from Gboard to ScriptGo in the system keyboard picker. */
@Composable
private fun SelectIllustration(done: Boolean) {
    val reduce = rememberReduceMotion()
    var picked by remember { mutableStateOf(done || reduce) }
    var finger by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(done, reduce) {
        if (done || reduce) { picked = true; finger = false; return@LaunchedEffect }
        while (true) {
            picked = false; finger = false; pressed = false
            delay(900)
            finger = true
            delay(450)
            pressed = true
            delay(160)
            picked = true
            delay(200)
            pressed = false
            delay(600)
            finger = false
            delay(1300)
        }
    }
    BCard(Modifier.fillMaxWidth(), radius = 24.dp) {
        Text(stringResource(R.string.sg_onb_pick_keyboard), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
        SystemRow("Gboard", leading = { RadioButton(selected = !picked, onClick = null) })
        HorizontalDivider(color = ScriptGo.colors.line)
        SystemRow("ScriptGo", leading = {
            Box(contentAlignment = Alignment.Center) {
                RadioButton(selected = picked, onClick = null)
                FingerDot(pressed, finger)
            }
        })
    }
}

@Composable
private fun SystemRow(name: String, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/**
 * The product in one loop: a customer asks, the seller types a shortcut, taps the suggestion, and
 * the full reply appears. With animations off it shows the finished reply.
 */
@Composable
private fun ChatDemo(question: String?, trigger: String, chip: String, reply: String) {
    val c = ScriptGo.colors
    val reduce = rememberReduceMotion()
    var typed by remember { mutableStateOf("") }
    var chipVisible by remember { mutableStateOf(false) }
    var chipPressed by remember { mutableStateOf(false) }
    var replied by remember { mutableStateOf(reduce) }
    LaunchedEffect(reduce) {
        if (reduce) return@LaunchedEffect
        while (true) {
            typed = ""; chipVisible = false; chipPressed = false; replied = false
            delay(1100)
            for (i in 1..trigger.length) {
                typed = trigger.take(i)
                if (i == 2) chipVisible = true
                delay(140)
            }
            delay(650)
            chipPressed = true
            delay(300)
            typed = ""; chipVisible = false; chipPressed = false; replied = true
            delay(3400)
        }
    }
    val replyAlpha by animateFloatAsState(if (replied) 1f else 0f, tween(350), label = "reply")
    val replyShift by animateFloatAsState(if (replied) 0f else 6f, tween(350), label = "replyShift")
    val chipBg by animateColorAsState(if (chipPressed) c.accentSoft else c.card, tween(150), label = "chip")
    BCard(Modifier.fillMaxWidth(), radius = 24.dp, contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        if (question != null) Bubble(question, mine = false)
        Box(Modifier.fillMaxWidth().alpha(replyAlpha).padding(top = replyShift.dp)) {
            Bubble(reply, mine = true, modifier = Modifier.align(Alignment.CenterEnd))
        }
        AnimatedVisibility(visible = chipVisible, enter = fadeIn(tween(150)), exit = fadeOut(tween(150))) {
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).background(c.line).padding(6.dp),
            ) {
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(chipBg).padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(trigger, style = MaterialTheme.typography.labelLarge, color = c.accentInk, fontWeight = FontWeight.ExtraBold)
                    Text(chip, style = MaterialTheme.typography.labelLarge, color = c.ink)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(CircleShape).background(c.bg).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (typed.isEmpty()) {
                Text(stringResource(R.string.sg_onb_type_message), style = MaterialTheme.typography.bodyMedium, color = c.muted)
            } else {
                Text(typed, style = MaterialTheme.typography.bodyMedium, color = c.ink)
                Box(Modifier.padding(start = 1.dp).width(2.dp).height(18.dp).background(c.accentSolid))
            }
        }
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean, modifier: Modifier = Modifier) {
    val c = ScriptGo.colors
    val shape = if (mine) RoundedCornerShape(18.dp, 18.dp, 6.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp)
    Box(
        modifier.widthIn(max = 280.dp).clip(shape)
            .then(if (mine) Modifier.background(c.accent) else Modifier.background(c.bg))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (mine) c.onAccent else c.ink,
            fontWeight = if (mine) FontWeight.SemiBold else null,
        )
    }
}
