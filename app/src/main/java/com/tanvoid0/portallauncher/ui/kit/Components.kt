package com.tanvoid0.portallauncher.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The shared layout kit.
 *
 * Everything here exists because two or more screens were building it by hand and
 * drifting apart while they did — different gutters, different corner radii, different
 * ideas of what a list row looks like. Nothing here wraps a Material component just to
 * rename it; if `Button` is the right answer, screens still call `Button`.
 */

/** Gap between rows inside a [PortalGroup]. Small enough to read as one panel. */
private val GROUP_ROW_GAP = 2.dp

/** Leading icon chip inside a [PortalRow]. */
private val ROW_ICON_BOX = 40.dp
private val ROW_ICON = 20.dp

/** Illustration icon in an [EmptyState]. */
private val EMPTY_STATE_ICON = 40.dp

/**
 * Standard screen frame: large title, optional subtitle, status-bar inset, optional
 * floating action.
 *
 * [content] is a `ColumnScope`, so a scrolling body takes `Modifier.weight(1f)` exactly
 * as it would in a hand-rolled `Column`.
 *
 * The action is a real overlay. Profiles previously placed its FAB *after* a weighted
 * `LazyColumn` inside a `Column`, which is a static button pinned to the bottom-left,
 * not a floating one.
 */
@Composable
fun PortalScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    floatingAction: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Column(
                modifier = Modifier.padding(
                    start = Spacing.gutter,
                    end = Spacing.gutter,
                    top = Spacing.xl,
                    bottom = Spacing.lg
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
        if (floatingAction != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(Spacing.lg)
            ) {
                floatingAction()
            }
        }
    }
}

/** Label above a [PortalGroup]. Horizontal placement comes from [modifier]. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = Spacing.lg, bottom = Spacing.sm)
    )
}

/**
 * An inset group of rows that reads as one rounded panel.
 *
 * This replaces `ListItem` + `HorizontalDivider`, which is the single strongest "this
 * app is from 2021" signal in the old screens: full-bleed rows separated by hairlines,
 * running edge to edge.
 *
 * The rounded ends come from clipping the whole column rather than per-row shape
 * bookkeeping, and the 2dp gaps let the page background through — so a group works with
 * any children and needs no index maths.
 */
@Composable
fun PortalGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    /** Page margin. Pass 0.dp where the parent already provides one. */
    gutter: Dp = Spacing.gutter,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) SectionHeader(title, Modifier.padding(horizontal = gutter))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = gutter)
                .clip(MaterialTheme.shapes.large),
            verticalArrangement = Arrangement.spacedBy(GROUP_ROW_GAP),
            content = content
        )
    }
}

/**
 * One row inside a [PortalGroup]. Square-cornered on purpose — the group does the
 * rounding.
 *
 * Pass no [onClick] for a row that only reports something; it then exposes no click
 * action rather than a disabled one, which is what a screen reader should hear.
 */
@Composable
fun PortalRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .then(
                    if (onClick != null) {
                        Modifier
                            .clickable(role = Role.Button, onClick = onClick)
                            .semantics(mergeDescendants = true) {}
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(ROW_ICON_BOX)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(ROW_ICON)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs / 2)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

/**
 * A [PortalRow] that is one option in a set. The whole row is the target and the radio
 * button is a state indicator only, so a screen reader hears one selectable thing.
 *
 * Wrap a set of these in `Modifier.selectableGroup()` at the call site.
 */
@Composable
fun SelectableRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Row(
            modifier = Modifier
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // No onClick of its own: the row carries the click.
            RadioButton(selected = selected, onClick = null)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs / 2)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

/**
 * Pick one of a small set. A wrapped grid of chips rather than a stack of full-width
 * buttons — Profile editing used the latter, which made eight equal options look like
 * eight separate commands.
 *
 * Chunked by hand instead of `FlowRow` so every chip in a row is the same width and no
 * experimental layout API is involved.
 */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    perRow: Int = 3
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        options.chunked(perRow).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                rowOptions.forEach { option ->
                    FilterChip(
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        label = {
                            Text(
                                text = label(option),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Keeps the last, short row's chips the same width as the rest.
                repeat(perRow - rowOptions.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Nothing to show, and why.
 *
 * Set [onWallpaper] on the home screen and leave it false anywhere with an opaque
 * background — see [OnWallpaperTextStyle].
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onWallpaper: Boolean = false,
    action: @Composable (() -> Unit)? = null
) {
    val titleStyle = MaterialTheme.typography.titleLarge.let {
        if (onWallpaper) it.merge(OnWallpaperTextStyle) else it
    }
    val bodyStyle = MaterialTheme.typography.bodyMedium.let {
        if (onWallpaper) it.merge(OnWallpaperTextStyle) else it
    }
    Column(
        modifier = modifier.padding(horizontal = Spacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (onWallpaper) Color.White else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(EMPTY_STATE_ICON)
            )
            Spacer(Modifier.size(Spacing.md))
        }
        Text(
            text = title,
            style = titleStyle,
            color = if (onWallpaper) Color.Unspecified else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(Spacing.sm))
        Text(
            text = body,
            style = bodyStyle,
            color = if (onWallpaper) {
                Color.Unspecified
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.size(Spacing.xl))
            action()
        }
    }
}

/**
 * Frosted panel for anything drawn over the wallpaper — search field, glance card,
 * profile chips. One material, one alpha, instead of four components each guessing.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    alpha: Float = GLASS_ALPHA,
    content: @Composable () -> Unit
) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.semantics(mergeDescendants = true) {},
            shape = shape,
            color = glassColor(alpha),
            content = content
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = glassColor(alpha),
            content = content
        )
    }
}
