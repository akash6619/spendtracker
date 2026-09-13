package com.spendtracker.app.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spendtracker.app.R
import com.spendtracker.app.ui.DashboardUiState
import com.spendtracker.app.ui.PreviewData
import com.spendtracker.app.ui.components.ContentPane
import com.spendtracker.app.ui.components.InfoCard
import com.spendtracker.app.ui.format.*
import com.spendtracker.app.ui.theme.SpendTrackerSpacing
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.app.ui.theme.SpendCategoryPalette
import com.spendtracker.core.aggregation.*
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.Money
import com.spendtracker.core.model.SpendCategory
import kotlin.math.roundToInt

/** User intents emitted by dashboard controls and reproducible ledger deep links. */
data class DashboardActions(
    val onPeriodSelected: (DashboardPeriod) -> Unit = {},
    val onPreviousPeriod: () -> Unit = {},
    val onNextPeriod: () -> Unit = {},
    val onCategorySelected: (SpendCategory) -> Unit = {},
    val onExcludedSelected: () -> Unit = {},
    val onForeignSelected: () -> Unit = {},
)

/** Compact financial overview for the selected calendar period. */
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    actions: DashboardActions = DashboardActions(),
) {
    ContentPane(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(SpendTrackerSpacing.PageMargin),
            verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.SectionGap),
        ) {
            DashboardHeader(state, actions)
            val report = state.report
            if (report == null) {
                PeriodNavigation(state, actions)
                InfoCard(stringResource(R.string.dashboard_empty_title), stringResource(R.string.dashboard_empty_body))
            } else {
                SummarySurface(state, actions)
                if (report.includedInrTotalMinor == 0L) {
                    InfoCard(stringResource(R.string.dashboard_no_inr_title), stringResource(R.string.dashboard_no_inr_body))
                }
                DashboardPanels(report, actions)
            }
        }
    }
}

@Composable
private fun DashboardHeader(state: DashboardUiState, actions: DashboardActions) {
    var periodMenuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = SpendTrackerSpacing.MinimumTouchTarget),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.dashboard_title), style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.isDemo) Text(
                stringResource(R.string.demo_badge),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                TextButton(
                    onClick = { periodMenuOpen = true },
                    modifier = Modifier.testTag("period_menu"),
                ) {
                    Text(stringResource(state.period.labelResource()))
                }
                DropdownMenu(expanded = periodMenuOpen, onDismissRequest = { periodMenuOpen = false }) {
                    DashboardPeriod.entries.forEach { period ->
                        DropdownMenuItem(
                            text = { Text(stringResource(period.labelResource())) },
                            onClick = {
                                periodMenuOpen = false
                                actions.onPeriodSelected(period)
                            },
                            modifier = Modifier.testTag("period_${period.name.lowercase()}"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummarySurface(state: DashboardUiState, actions: DashboardActions) {
    val report = state.report ?: return
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(SpendTrackerSpacing.GroupPadding), verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.TightGap)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(state.titleResource()), style = MaterialTheme.typography.titleSmall)
                    Text(
                        formatRange(report.range.startInclusiveEpochMillis, report.range.endExclusiveEpochMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PeriodNavigation(state, actions)
            }
            Text(formatMoney(Money(report.includedInrTotalMinor, CurrencyCode.INR)), style = MaterialTheme.typography.headlineMedium)
            Text(
                pluralStringResource(R.plurals.included_transaction_count, report.includedInrCount, report.includedInrCount),
                style = MaterialTheme.typography.bodySmall,
            )
            ComparisonLine(state)
            if (report.excludedCount > 0 || report.foreignCount > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.RelatedGap)) {
                    if (report.excludedCount > 0) FactLink(
                        stringResource(R.string.dashboard_excluded_fact, report.excludedCount),
                        stringResource(R.string.link_excluded_cd), "link_excluded", actions.onExcludedSelected, Modifier.weight(1f),
                    )
                    if (report.foreignCount > 0) FactLink(
                        stringResource(R.string.dashboard_foreign_fact, report.foreignCount),
                        stringResource(R.string.link_foreign_cd), "link_foreign", actions.onForeignSelected, Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodNavigation(state: DashboardUiState, actions: DashboardActions) {
    val periodWord = stringResource(state.period.wordResource())
    val previousDescription = stringResource(R.string.period_previous_cd, periodWord)
    val nextDescription = stringResource(R.string.period_next_cd, periodWord)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = actions.onPreviousPeriod,
            modifier = Modifier.testTag("period_previous"),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = previousDescription,
            )
        }
        IconButton(
            onClick = actions.onNextPeriod,
            enabled = state.periodOffset < 0,
            modifier = Modifier.testTag("period_next"),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = nextDescription,
            )
        }
    }
}

@Composable
private fun ComparisonLine(state: DashboardUiState) {
    val comparison = state.comparison ?: return
    val periodWord = stringResource(state.period.wordResource())
    val text = comparison.deltaPercent?.let {
        stringResource(if (state.periodOffset == 0) R.string.comparison_format else R.string.comparison_full_format, it, periodWord)
    } ?: stringResource(R.string.comparison_none, periodWord)
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FactLink(label: String, description: String, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier.testTag(tag).clickable(onClick = onClick).semantics { contentDescription = description },
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DashboardPanels(report: PeriodReport, actions: DashboardActions) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val categories = report.categoryBreakdown.isNotEmpty()
        val daily = report.period != DashboardPeriod.DAY
        if (maxWidth >= 720.dp && categories && daily) {
            Row(horizontalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.SectionGap)) {
                CategoryBreakdownPanel(report, actions, Modifier.weight(1f))
                DailySpendPanel(report, Modifier.weight(1f))
            }
        } else Column(verticalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.SectionGap)) {
            if (categories) CategoryBreakdownPanel(report, actions)
            if (daily) DailySpendPanel(report)
        }
    }
}

@Composable
private fun CategoryBreakdownPanel(report: PeriodReport, actions: DashboardActions, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(stringResource(R.string.category_breakdown_title), Modifier.padding(bottom = SpendTrackerSpacing.RelatedGap), style = MaterialTheme.typography.titleMedium)
        CategoryShareBar(report)
        Spacer(Modifier.height(SpendTrackerSpacing.RelatedGap))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column {
                report.categoryBreakdown.forEachIndexed { index, item ->
                    val share = if (report.includedInrTotalMinor == 0L) 0 else ((item.totalMinor * 100.0) / report.includedInrTotalMinor).roundToInt()
                    val label = stringResource(item.category.labelResource())
                    val description = stringResource(R.string.link_category_cd, label)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = SpendTrackerSpacing.MinimumTouchTarget)
                            .clickable { actions.onCategorySelected(item.category) }
                            .testTag("category_row_${item.category.name}").semantics { contentDescription = description }
                            .padding(horizontal = SpendTrackerSpacing.CompactGroupPadding, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(SpendTrackerSpacing.RelatedGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(SpendCategoryPalette.color(item.category)),
                        )
                        Text(
                            "$label  ${stringResource(R.string.category_share, share)} · ${item.transactionCount}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(formatMoney(Money(item.totalMinor, CurrencyCode.INR)), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                    if (index != report.categoryBreakdown.lastIndex) HorizontalDivider(Modifier.padding(start = SpendTrackerSpacing.CompactGroupPadding))
                }
            }
        }
    }
}

@Composable
private fun CategoryShareBar(report: PeriodReport) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        report.categoryBreakdown.forEach { item ->
            if (item.totalMinor > 0L) {
                Box(
                    Modifier
                        .weight(item.totalMinor.toFloat())
                        .fillMaxHeight()
                        .background(SpendCategoryPalette.color(item.category)),
                )
            }
        }
    }
}

@Composable
private fun DailySpendPanel(report: PeriodReport, modifier: Modifier = Modifier) {
    val total = report.dailySeries.sumOf { it.totalMinor }
    val chartDescription = stringResource(R.string.daily_chart_cd, report.dailySeries.size, formatMoney(Money(total, CurrencyCode.INR)))
    var selectedBar by remember(report.dailySeries) { mutableStateOf<Int?>(null) }
    Column(modifier) {
        Text(stringResource(R.string.daily_spend_title), Modifier.padding(bottom = SpendTrackerSpacing.RelatedGap), style = MaterialTheme.typography.titleMedium)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(SpendTrackerSpacing.CompactGroupPadding)) {
                if (report.dailySeries.isEmpty() || total == 0L) {
                    Text(
                        stringResource(R.string.daily_spend_empty),
                        Modifier.fillMaxWidth().height(72.dp).semantics { contentDescription = chartDescription },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val max = report.dailySeries.maxOf { it.totalMinor }
                    Row(
                        Modifier.fillMaxWidth().height(88.dp).testTag("daily_chart").semantics { contentDescription = chartDescription },
                        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom,
                    ) {
                        report.dailySeries.forEachIndexed { index, day ->
                            Bar(
                                day = day,
                                max = max,
                                index = index,
                                selected = selectedBar == index,
                                onSelected = { selectedBar = if (selectedBar == index) null else index },
                                onDismiss = { selectedBar = null },
                            )
                        }
                    }
                    if (report.period == DashboardPeriod.WEEK) Row(Modifier.fillMaxWidth()) {
                        report.dailySeries.forEach { day -> Text(weekdayShortLabel(day.dayStartEpochMillis), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Bar(
    day: DailySpend,
    max: Long,
    index: Int,
    selected: Boolean,
    onSelected: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fraction = maxOf(0.03f, day.totalMinor.toFloat() / max)
    val description = stringResource(R.string.daily_bar_cd, dayLabel(day.dayStartEpochMillis), formatMoney(Money(day.totalMinor, CurrencyCode.INR)))
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .testTag("daily_bar_$index")
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onSelected)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            Modifier.fillMaxWidth(0.68f).fillMaxHeight(fraction)
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        DropdownMenu(
            expanded = selected || hovered,
            onDismissRequest = onDismiss,
        ) {
            Text(
                description,
                modifier = Modifier
                    .testTag("daily_bar_popup")
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 892)
@Composable private fun DashboardContentPreview() { SpendTrackerTheme { DashboardScreen(dashboardUiState(DashboardPeriod.WEEK)) } }
@Preview(showBackground = true, widthDp = 840, heightDp = 900)
@Composable private fun DashboardWidePreview() { SpendTrackerTheme { DashboardScreen(dashboardUiState(DashboardPeriod.MONTH)) } }
@Preview(showBackground = true)
@Composable private fun DashboardEmptyPreview() { SpendTrackerTheme { DashboardScreen(DashboardUiState()) } }

private fun dashboardUiState(period: DashboardPeriod): DashboardUiState {
    val zone = kotlinx.datetime.TimeZone.UTC
    val now = java.time.Instant.parse("2026-09-09T12:00:00Z").toEpochMilli()
    val range = PeriodCalculator.currentRange(now, zone, period)
    val report = ReportAggregator.compute(PreviewData.dashboardLedger, range, period, zone)
    val previous = ReportAggregator.compute(PreviewData.dashboardLedger, PeriodCalculator.sameElapsedPreviousRange(now, zone, period), period, zone)
    return DashboardUiState(
        period = period,
        report = report,
        comparison = ReportAggregator.compare(report.includedInrTotalMinor, previous.includedInrTotalMinor),
        hasTransactions = true,
        isDemo = true,
    )
}

@StringRes private fun DashboardPeriod.labelResource(): Int = when (this) {
    DashboardPeriod.DAY -> R.string.period_day
    DashboardPeriod.WEEK -> R.string.period_week
    DashboardPeriod.MONTH -> R.string.period_month
}
@StringRes private fun DashboardPeriod.wordResource(): Int = when (this) {
    DashboardPeriod.DAY -> R.string.period_day_word
    DashboardPeriod.WEEK -> R.string.period_week_word
    DashboardPeriod.MONTH -> R.string.period_month_word
}
@StringRes private fun DashboardUiState.titleResource(): Int = if (periodOffset == 0) when (period) {
    DashboardPeriod.DAY -> R.string.dashboard_today
    DashboardPeriod.WEEK -> R.string.dashboard_this_week
    DashboardPeriod.MONTH -> R.string.dashboard_this_month
} else when (period) {
    DashboardPeriod.DAY -> R.string.dashboard_selected_day
    DashboardPeriod.WEEK -> R.string.dashboard_selected_week
    DashboardPeriod.MONTH -> R.string.dashboard_selected_month
}
