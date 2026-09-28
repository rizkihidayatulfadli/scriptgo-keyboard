// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import androidx.compose.runtime.Composable
import com.magickeyboard.manager.AppTheme
import com.magickeyboard.manager.ui.theme.ScriptGoTheme

// magic-keyboard: keyboard settings use the ScriptGo colours, Urbanist, and rounded shapes
// instead of dynamic Material colours, so they match the manager app, and follow Akun → Tema.
@Composable
fun Theme(dark: Boolean = AppTheme.isDark(), content: @Composable () -> Unit) {
    ScriptGoTheme(dark = dark, content = content)
}

const val previewDark = true
