package com.magickeyboard.manager.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.magickeyboard.manager.AppLanguage
import com.magickeyboard.manager.ui.theme.ScriptGoTheme
import com.magickeyboard.sync.SupabaseSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import helium314.keyboard.settings.SettingsActivity2

/** Entry point of the manager app: templates, contacts, and custom fields. */
class ManagerActivity : ComponentActivity() {
    /** Counts sign-ins finished in AuthCallbackActivity; each one opens the Akun tab. */
    private var signIns by mutableIntStateOf(0)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent?.getBooleanExtra(EXTRA_SIGNED_IN, false) == true) signIns++
        setContent {
            ManagerTheme {
                // SettingsActivity2 stays enabled even when HeliBoard's launcher icon setting disables SettingsActivity.
                ManagerApp(openKeyboardSettings = { startActivity(Intent(this, SettingsActivity2::class.java)) }, signIns = signIns)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SIGNED_IN, false)) signIns++
    }

    companion object {
        const val EXTRA_SIGNED_IN = "com.magickeyboard.SIGNED_IN"
    }
}

/** The ScriptGo theme (design/tokens.json), shared with the variable form above the keyboard. */
@Composable
fun ManagerTheme(content: @Composable () -> Unit) = ScriptGoTheme(content = content)

/** Screens of the manager. Tabs sit at the bottom of the back stack; details are pushed on top. */
sealed interface Screen {
    data object Templates : Screen
    data object Contacts : Screen
    data object Fields : Screen
    data object Account : Screen
    data class EditTemplate(val id: String?) : Screen
    data class EditContact(val id: String?) : Screen
    data object ImportCsv : Screen
    data object ImportPhone : Screen
    data object SignInEmail : Screen
    data class SignInCode(val email: String) : Screen
    data object BusinessProfile : Screen
    data object PersonalData : Screen
    data object Password : Screen
    data object Passkeys : Screen
    data object Help : Screen
}

@Composable
fun ManagerApp(openKeyboardSettings: () -> Unit, signIns: Int = 0) {
    val repo = rememberRepository()
    var onboarding by remember { mutableStateOf(!repo.onboardingDone()) }
    var afterOnboarding by remember { mutableStateOf<Screen?>(null) }
    if (onboarding) {
        OnboardingScreen(onDone = { next ->
            repo.setOnboardingDone()
            afterOnboarding = next
            onboarding = false
        })
        return
    }

    // Sync in the background when the app opens, if signed in; the Akun tab shows errors.
    val context = LocalContext.current
    // The business profile is required (business analysis); users from before it was required answer it once.
    var profileNeeded by remember { mutableStateOf(SupabaseSync.get(context).needsProfilePrompt()) }
    if (profileNeeded) {
        BusinessProfileScreen(required = true, onDone = { profileNeeded = false })
        return
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val sync = SupabaseSync.get(context)
            // The keyboard reads the free limits from these caches, so refresh them on each start.
            runCatching { sync.refreshLimitsActive() }
            if (sync.signedInEmail() != null) {
                runCatching { sync.loadPlan() }
                runCatching { sync.syncNow() }
            }
        }
    }

    val stack = remember { mutableStateListOf<Screen>(Screen.Templates) }
    val navigate: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    BackHandler(enabled = stack.size > 1, onBack = back)

    val nav = TabNav(current = stack.first(), select = { tab ->
        stack.clear()
        stack.add(tab)
    })

    /** After any sign-in: the Akun tab, plus the business profile the first time. */
    fun showAccountAfterSignIn() {
        stack.clear()
        stack.add(Screen.Account)
        if (SupabaseSync.get(context).needsProfilePrompt()) stack.add(Screen.BusinessProfile)
    }
    LaunchedEffect(signIns) { if (signIns > 0) showAccountAfterSignIn() }
    // Onboarding's "Lanjut dengan email" continues into the email sign-in.
    LaunchedEffect(Unit) {
        afterOnboarding?.let { navigate(it) }
        afterOnboarding = null
    }

    when (val screen = stack.last()) {
        Screen.Templates -> TemplatesScreen(navigate, nav, openKeyboardSettings)
        Screen.Contacts -> ContactsScreen(navigate, nav)
        Screen.Fields -> FieldsScreen(nav)
        Screen.Account -> key(signIns) { AccountScreen(nav, navigate, openKeyboardSettings, onSignedIn = { showAccountAfterSignIn() }) }
        is Screen.EditTemplate -> TemplateEditorScreen(screen.id, back)
        is Screen.EditContact -> ContactEditorScreen(screen.id, back)
        Screen.ImportCsv -> ImportCsvScreen(back)
        Screen.ImportPhone -> ImportPhoneScreen(back)
        Screen.SignInEmail -> EmailSignInScreen(onBack = back, onSent = { email -> navigate(Screen.SignInCode(email)) }, onSignedIn = { showAccountAfterSignIn() })
        is Screen.SignInCode -> CodeScreen(screen.email, onBack = back, onSignedIn = { showAccountAfterSignIn() })
        Screen.BusinessProfile -> BusinessProfileScreen(onBack = back, onDone = back)
        Screen.PersonalData -> PersonalDataScreen(onBack = back)
        Screen.Password -> PasswordScreen(onBack = back)
        Screen.Passkeys -> PasskeysScreen(onBack = back)
        Screen.Help -> HelpScreen(onBack = back)
    }
}
