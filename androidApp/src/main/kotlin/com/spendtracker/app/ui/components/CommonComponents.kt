package com.spendtracker.app.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.spendtracker.app.R
import androidx.compose.ui.res.stringResource
import com.spendtracker.app.ui.theme.SpendTrackerSpacing
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.app.ui.theme.SpendTrackerWidths

/**
 * Visual tone for compact status surfaces.
 * Every non-neutral tone also renders a recognizable icon so its meaning is
 * not carried by color alone.
 */
enum class StatusTone {
    NEUTRAL,
    WARNING,
    ERROR,
    DESTRUCTIVE,
}

@Composable
fun ContentPane(
    modifier: Modifier = Modifier,
    maxWidth: Dp = SpendTrackerWidths.LedgerContent,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .testTag("content_pane"),
            content = content,
        )
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.TightGap),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        subtitle?.takeIf(String::isNotBlank)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    metadata: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SpendTrackerSpacing.MinimumTouchTarget),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        metadata?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun StatusBanner(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.NEUTRAL,
) {
    val colors = statusColors(tone)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.container,
        contentColor = colors.content,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(SpendTrackerSpacing.CompactGroupPadding),
            horizontalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.RelatedGap),
            verticalAlignment = Alignment.Top,
        ) {
            colors.icon?.let { Icon(it, contentDescription = null) }
            Column(verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.TightGap)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun GroupedRow(
    title: String,
    modifier: Modifier = Modifier,
    metadata: String? = null,
    trailing: String? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SpendTrackerSpacing.MinimumTouchTarget)
                .padding(
                    horizontal = SpendTrackerSpacing.GroupPadding,
                    vertical = SpendTrackerSpacing.RelatedGap,
                ),
            horizontalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.RelatedGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.TightGap),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                metadata?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(modifier = Modifier.padding(start = SpendTrackerSpacing.GroupPadding))
        }
    }
}

/** Material colors and non-color icon cue resolved for one status tone. */
private data class StatusColors(
    val container: Color,
    val content: Color,
    val icon: ImageVector?,
)

@Composable
private fun statusColors(tone: StatusTone): StatusColors = when (tone) {
    StatusTone.NEUTRAL -> StatusColors(
        MaterialTheme.colorScheme.surfaceContainer,
        MaterialTheme.colorScheme.onSurface,
        null,
    )
    StatusTone.WARNING -> StatusColors(
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer,
        Icons.Outlined.WarningAmber,
    )
    StatusTone.ERROR -> StatusColors(
        MaterialTheme.colorScheme.errorContainer,
        MaterialTheme.colorScheme.onErrorContainer,
        Icons.Outlined.ErrorOutline,
    )
    StatusTone.DESTRUCTIVE -> StatusColors(
        MaterialTheme.colorScheme.errorContainer,
        MaterialTheme.colorScheme.onErrorContainer,
        Icons.Outlined.DeleteOutline,
    )
}

@Composable
fun InfoCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    content: @Composable (() -> Unit)? = null,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(SpendTrackerSpacing.GroupPadding),
            verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.RelatedGap),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(body, style = MaterialTheme.typography.bodyMedium)
            content?.invoke()
        }
    }
}

@Composable
fun DemoBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = SpendTrackerSpacing.GroupPadding,
                vertical = SpendTrackerSpacing.CompactGroupPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.RelatedGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Science, contentDescription = null)
            Text(
                stringResource(R.string.demo_badge),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Preview(name = "Narrow phone", widthDp = 320, heightDp = 640, showBackground = true)
@Preview(name = "Normal phone", widthDp = 412, heightDp = 892, showBackground = true)
@Preview(name = "Wide", widthDp = 840, heightDp = 900, showBackground = true)
@Preview(
    name = "Dark",
    widthDp = 412,
    heightDp = 892,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Increased font",
    widthDp = 360,
    heightDp = 800,
    showBackground = true,
    fontScale = 1.5f,
)
@Composable
private fun DesignFoundationPreview() {
    SpendTrackerTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ContentPane {
                Column(
                    modifier = Modifier.padding(SpendTrackerSpacing.PageMargin),
                    verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.SectionGap),
                ) {
                    ScreenHeader("Transactions")
                    StatusBanner(
                        title = "Needs your input",
                        body = "Details missing",
                        tone = StatusTone.WARNING,
                    )
                    Surface(shape = MaterialTheme.shapes.medium) {
                        Column {
                            SectionHeading("Recent activity", metadata = "2 records")
                            GroupedRow(
                                title = "Example Grocer",
                                metadata = "Food & dining · 8 Sep",
                                trailing = "₹850.00",
                            )
                            GroupedRow(
                                title = "Local transfer",
                                metadata = "Excluded · 7 Sep",
                                trailing = "₹240.00",
                                showDivider = false,
                            )
                        }
                    }
                    StatusBanner(
                        title = "Delete data",
                        body = "This action permanently clears local SpendTracker data.",
                        tone = StatusTone.DESTRUCTIVE,
                    )
                }
            }
        }
    }
}
