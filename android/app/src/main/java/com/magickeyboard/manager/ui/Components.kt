package com.magickeyboard.manager.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.magickeyboard.manager.ui.theme.ScriptGo
import com.magickeyboard.manager.ui.theme.ScriptGoIcons
import helium314.keyboard.latin.R

/** The four tabs of the manager and how to switch between them. */
class TabNav(val current: Screen, val select: (Screen) -> Unit)

private class NavItem(val screen: Screen, val icon: ImageVector, @StringRes val label: Int)

private val NAV_ITEMS = listOf(
    NavItem(Screen.Templates, ScriptGoIcons.Templates, R.string.sg_tab_templates),
    NavItem(Screen.Contacts, ScriptGoIcons.Contacts, R.string.sg_tab_contacts),
    NavItem(Screen.Fields, ScriptGoIcons.Fields, R.string.sg_tab_fields),
    NavItem(Screen.Account, ScriptGoIcons.Account, R.string.sg_tab_account),
)

/**
 * Frame of a tab screen: sky/indigo glow, big bold title, and the floating nav bar with a round
 * "+" button ([onAdd], hidden when null). [content] gets padding that clears the header gutter
 * and the nav bar.
 */
@Composable
fun TabScaffold(
    title: String,
    nav: TabNav,
    onAdd: (() -> Unit)?,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val c = ScriptGo.colors
    Box(Modifier.fillMaxSize().background(c.bg)) {
        GlowBackdrop()
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                leading?.invoke()
                Text(
                    title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                actions()
            }
            // Room for the floating nav and the phone's gesture bar, so the last item can scroll clear.
            val gestureBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Box(Modifier.fillMaxWidth().weight(1f)) {
                content(PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp + gestureBar))
            }
        }
        // Content fades out under the nav instead of showing through the gap beside the + button.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().height(96.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, c.bg))),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()).background(c.bg),
        )
        FloatingNav(nav, onAdd, Modifier.align(Alignment.BottomCenter))
    }
}

/** Soft sky and indigo light at the top of a screen, like the home screen of the design. */
@Composable
fun GlowBackdrop(modifier: Modifier = Modifier) {
    val c = ScriptGo.colors
    val alpha = if (c.isDark) 0.22f else 0.42f
    Canvas(modifier.fillMaxWidth().height(320.dp)) {
        val radius = size.height * 0.62f
        drawRect(
            Brush.radialGradient(
                listOf(c.accentStart.copy(alpha = alpha), Color.Transparent),
                center = Offset(size.width * 0.22f, size.height * 0.28f),
                radius = radius,
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(c.accentEnd.copy(alpha = alpha), Color.Transparent),
                center = Offset(size.width * 0.86f, size.height * 0.18f),
                radius = radius,
            )
        )
    }
}

@Composable
private fun FloatingNav(nav: TabNav, onAdd: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = ScriptGo.colors
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).height(62.dp).softShadow(CircleShape).clip(CircleShape).background(c.card).darkOutline(CircleShape).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NAV_ITEMS.forEach { item ->
                val selected = nav.current == item.screen
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .background(if (selected) c.line else Color.Transparent)
                        .clickable { nav.select(item.screen) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(item.icon, contentDescription = stringResource(item.label), tint = if (selected) c.ink else c.muted)
                }
            }
        }
        if (onAdd != null) {
            Box(
                Modifier.size(62.dp).softShadow(CircleShape).clip(CircleShape).background(c.card).darkOutline(CircleShape).clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ScriptGoIcons.Plus, contentDescription = stringResource(R.string.sg_add), tint = c.ink)
            }
        }
    }
}

/** The design's barely-there card shadow; dark mode separates cards by colour instead. */
@Composable
private fun Modifier.softShadow(shape: androidx.compose.ui.graphics.Shape, elevation: Dp = 10.dp): Modifier {
    val c = ScriptGo.colors
    return if (c.isDark) this else shadow(elevation, shape, clip = false, ambientColor = SHADOW, spotColor = SHADOW)
}

private val SHADOW = Color(0x2E0E1116)

/** Dark mode has no shadows, so floating controls get a hairline edge to stand off the cards below. */
@Composable
private fun Modifier.darkOutline(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val c = ScriptGo.colors
    return if (c.isDark) border(1.dp, c.line, shape) else this
}

/** White (dark: slate) rounded card. */
@Composable
fun BCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    radius: Dp = 22.dp,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = ScriptGo.colors
    val shape = RoundedCornerShape(radius)
    Column(
        modifier.softShadow(shape, elevation = 6.dp).clip(shape).background(c.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** A list row as a card: optional leading badge, title, subtitle, optional trailing element. */
@Composable
fun ListCard(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = ScriptGo.colors
    BCard(modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrEmpty()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing?.invoke()
        }
    }
}

/** Rounded square with the first letter of [text]; gradient for templates, soft for the rest. */
@Composable
fun LetterBadge(text: String, gradient: Boolean = true, size: Dp = 44.dp) {
    val c = ScriptGo.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size(size).clip(shape).background(if (gradient) c.accent else SolidColor(c.glow), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.trim().take(1).uppercase().ifEmpty { "?" },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = if (gradient) c.onAccent else c.ink,
        )
    }
}

/** Rounded square with an icon, the same size and colour as a [LetterBadge]. */
@Composable
fun IconBadge(icon: ImageVector, gradient: Boolean = true, size: Dp = 44.dp) {
    val c = ScriptGo.colors
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size(size).clip(shape).background(if (gradient) c.accent else SolidColor(c.glow), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (gradient) c.onAccent else c.ink, modifier = Modifier.size(size * 0.45f))
    }
}

/** A contact field's icon ([com.magickeyboard.contacts.FieldIcons] name) as a gradient badge. */
@Composable
fun FieldIconBadge(icon: String, size: Dp = 44.dp) {
    IconBadge(com.magickeyboard.manager.ui.theme.FieldIconVectors[icon] ?: ScriptGoIcons.Type, size = size)
}

/** Round initial avatar for contacts. */
@Composable
fun InitialAvatar(name: String) {
    val c = ScriptGo.colors
    Box(Modifier.size(44.dp).clip(CircleShape).background(c.glow), contentAlignment = Alignment.Center) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = c.ink,
        )
    }
}

/** Outlined chip such as the `/shortcut` of a template. */
@Composable
fun OutlineChip(text: String, modifier: Modifier = Modifier) {
    val c = ScriptGo.colors
    val shape = RoundedCornerShape(12.dp)
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = c.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.border(1.5.dp, c.line, shape).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Round bordered icon button for header actions. */
@Composable
fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val c = ScriptGo.colors
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(c.card).border(1.dp, c.line, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = c.ink, modifier = Modifier.size(20.dp))
    }
}

/** Pill-shaped search field. */
@Composable
fun SearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
) {
    val c = ScriptGo.colors
    val textStyle = MaterialTheme.typography.bodyLarge
    Row(
        modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(c.card).border(1.dp, c.line, CircleShape)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(ScriptGoIcons.Search, contentDescription = null, tint = c.muted, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = textStyle, color = c.muted, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = textStyle.copy(color = c.ink),
                cursorBrush = SolidColor(c.accentSolid),
                modifier = fieldModifier.fillMaxWidth(),
            )
        }
    }
}

/** Text filled with the sky to indigo gradient. */
@Composable
fun GradientText(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(text, style = style.copy(brush = ScriptGo.colors.accent), modifier = modifier)
}

/** Small uppercase label above a group. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = ScriptGo.colors.muted, modifier = modifier)
}

/** A card holding a short centred message, used for empty lists. */
@Composable
fun EmptyCard(text: String) {
    BCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = ScriptGo.colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The ScriptGo logo (C2, glossy keycap), drawn in code so it works on every Android version. */
@Composable
fun ScriptGoMark(modifier: Modifier = Modifier.size(36.dp)) {
    Canvas(modifier) {
        val s = size.minDimension / 100f
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF4F46E5)), start = p(8f, 16f), end = p(92f, 92f)),
            topLeft = p(8f, 16f),
            size = Size(84f * s, 76f * s),
            cornerRadius = CornerRadius(24f * s),
        )
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFF0EA5E9), Color(0xFF6366F1)), start = p(14f, 8f), end = p(86f, 76f)),
            topLeft = p(14f, 8f),
            size = Size(72f * s, 68f * s),
            cornerRadius = CornerRadius(20f * s),
        )
        drawOval(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f)), startY = 12f * s, endY = 32f * s),
            topLeft = p(18f, 12f),
            size = Size(48f * s, 20f * s),
        )
        drawLine(Color.White, start = p(59f, 24f), end = p(43f, 60f), strokeWidth = 9f * s, cap = StrokeCap.Round)
    }
}
