package com.spendtracker.app.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spendtracker.app.R
import com.spendtracker.app.ui.DashboardUiState
import com.spendtracker.app.ui.components.DemoBanner
import com.spendtracker.app.ui.components.InfoCard
import com.spendtracker.app.ui.components.ScreenHeader
import com.spendtracker.app.ui.format.dayLabel
import com.spendtracker.app.ui.format.formatMoney
import com.spendtracker.app.ui.format.formatRange
import com.spendtracker.app.ui.format.labelResource
import com.spendtracker.app.ui.format.weekdayShortLabel
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.core.aggregation.DashboardPeriod
import com.spendtracker.core.aggregation.DailySpend
import com.spendtracker.core.aggregation.PeriodReport
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.Money
import com.spendtracker.core.model.SpendCategory
import kotlin.math.roundToInt

/**
 * Intents the dashboard emits for period selection and explainable deep links.
 * Default callbacks keep previews independent of the ViewModel; every callback
 * opens a transaction view whose filter reproduces the tapped dashboard fact.
 */
data class DashboardActions(
    val onPeriodSelected: (DashboardPeriod) -> Unit = {},
    val onPreviousPeriod: () -> Unit = {},
    val onNextPeriod: () -> Unit = {},
    val onCategorySelected: (SpendCategory) -> Unit = {},
    val onExcludedSelected: () -> Unit = {},
    val onForeignSelected: () -> Unit = {},
)

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    actions: DashboardActions = DashboardActions(),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScreenHeader(
            title = stringResource(R.string.dashboard_title),
            subtitle = stringResource(R.string.dashboard_subtitle),
        )
        if (state.isDemo) DemoBanner()

        PeriodSelector(state.period, actions.onPeriodSelected)
        PeriodNavigation(state, actions)

        val report = state.report
        if (report == null) {
            InfoCard(
                title = stringResource(R.string.dashboard_empty_title),
                body = stringResource(R.string.dashboard_empty_body),
            )
        } else {
            HeadlineCard(state, actions)
            if (report.includedInrTotalMinor == 0L) {
                InfoCard(
                    title = stringResource(R.string.dashboard_no_inr_title),
                    body = stringResource(R.string.dashboard_no_inr_body),
                )
            }
            if (report.categoryBreakdown.isNotEmpty()) {
                CategoryBreakdownCard(report, actions)
            }
            if (report.period != DashboardPeriod.DAY) DailySpendCard(report)
        }
    }
}

@Composable
private fun PeriodSelector(selected: DashboardPeriod, onSelected: (DashboardPeriod) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        DashboardPeriod.entries.forEachIndexed { index, period ->
            SegmentedButton(
                selected = period == selected,
                onClick = { onSelected(period) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = DashboardPeriod.entries.size),
                modifier = Modifier.testTag(
                    "period_${period.name.lowercase()}",
                ),
            ) {
                Text(stringResource(period.labelResource()))
            }
        }
    }
}

@Composable
private fun PeriodNavigation(state: DashboardUiState, actions: DashboardActions) {
    val periodWord = stringResource(state.period.wordResource())
    val previousDescription = stringResource(R.string.period_previous_cd, periodWord)
    val nextDescription = stringResource(R.string.period_next_cd, periodWord)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = actions.onPreviousPeriod,
            modifier = Modifier
                .testTag("period_previous")
                .semantics { contentDescription = previousDescription },
        ) {
            Text(stringResource(R.string.period_previous))
        }
        TextButton(
            onClick = actions.onNextPeriod,
            enabled = state.periodOffset < 0,
            modifier = Modifier
                .testTag("period_next")
                .semantics { contentDescription = nextDescription },
        ) {
            Text(stringResource(R.string.period_next))
        }
    }
}

@Composable
private fun HeadlineCard(
    state: DashboardUiState,
    actions: DashboardActions,
) {
    val report = state.report ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(state.titleResource()),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                formatRange(report.range.startInclusiveEpochMillis, report.range.endExclusiveEpochMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatMoney(Money(report.includedInrTotalMinor, CurrencyCode.INR)),
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                pluralStringResource(
                    R.plurals.included_transaction_count,
                    report.includedInrCount,
                    report.includedInrCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            ComparisonLine(state)
            if (report.excludedCount > 0) {
                LinkRow(
                    label = pluralStringResource(
                        R.plurals.excluded_transaction_count,
                        report.excludedCount,
                        report.excludedCount,
                    ),
                    description = stringResource(R.string.link_excluded_cd),
                    tag = "link_excluded",
                    onClick = actions.onExcludedSelected,
                )
            }
            if (report.foreignCount > 0) {
                LinkRow(
                    label = pluralStringResource(
                        R.plurals.foreign_transaction_count,
                        report.foreignCount,
                        report.foreignCount,
                    ),
                    description = stringResource(R.string.link_foreign_cd),
                    tag = "link_foreign",
                    onClick = actions.onForeignSelected,
                )
            }
        }
    }
}

@Composable
private fun ComparisonLine(state: DashboardUiState) {
    val comparison = state.comparison ?: return
    val periodWord = stringResource(state.period.wordResource())
    val text = comparison.deltaPercent?.let { percent ->
        stringResource(
            if (state.periodOffset == 0) R.string.comparison_format else R.string.comparison_full_format,
            percent,
            periodWord,
        )
    } ?: stringResource(R.string.comparison_none, periodWord)
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LinkRow(label: String, description: String, tag: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .semantics { contentDescription = description },
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CategoryBreakdownCard(report: PeriodReport, actions: DashboardActions) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.category_breakdown_title),
                style = MaterialTheme.typography.titleMedium,
            )
            report.categoryBreakdown.forEach { categorySpend ->
                val share = ((categorySpend.totalMinor * 100.0) / report.includedInrTotalMinor).roundToInt()
                val categoryLabel = stringResource(categorySpend.category.labelResource())
                val description = stringResource(R.string.link_category_cd, categoryLabel)
                TextButton(
                    onClick = { actions.onCategorySelected(categorySpend.category) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("category_row_${categorySpend.category.name}")
                        .semantics {
                            contentDescription = description
                        },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "$categoryLabel · ${stringResource(R.string.category_share, share)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${formatMoney(Money(categorySpend.totalMinor, CurrencyCode.INR))} · " +
                                pluralStringResource(
                                    R.plurals.category_transaction_count,
                                    categorySpend.transactionCount,
                                    categorySpend.transactionCount,
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailySpendCard(report: PeriodReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.daily_spend_title),
                style = MaterialTheme.typography.titleMedium,
            )
            val max = report.dailySeries.maxOfOrNull { it.totalMinor } ?: 0L
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                report.dailySeries.forEachIndexed { index, day ->
                    Bar(day, max, index)
                }
            }
            if (report.period == DashboardPeriod.WEEK) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    report.dailySeries.forEachIndexed { index, day ->
                        Text(
                            weekdayShortLabel(day.dayStartEpochMillis),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.Bar(day: DailySpend, max: Long, index: Int) {
    val fraction = if (max == 0L) {
        0.02f
    } else {
        maxOf(0.02f, day.totalMinor.toFloat() / max)
    }
    val description = stringResource(
        R.string.daily_bar_cd,
        dayLabel(day.dayStartEpochMillis),
        formatMoney(Money(day.totalMinor, CurrencyCode.INR)),
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .testTag("daily_bar_$index")
            .semantics {
                contentDescription = description
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .fillMaxHeight(fraction)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardDayPreview() {
    SpendTrackerTheme {
        DashboardScreen(dashboardUiState(DashboardPeriod.DAY))
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardContentPreview() {
    SpendTrackerTheme {
        DashboardScreen(dashboardUiState(DashboardPeriod.WEEK))
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardMonthPreview() {
    SpendTrackerTheme {
        DashboardScreen(dashboardUiState(DashboardPeriod.MONTH))
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardEmptyPreview() {
    SpendTrackerTheme {
        DashboardScreen(DashboardUiState())
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardNoInrPreview() {
    SpendTrackerTheme {
        DashboardScreen(
            DashboardUiState(
                period = DashboardPeriod.WEEK,
                report = com.spendtracker.core.aggregation.PeriodReport(
                    period = DashboardPeriod.WEEK,
                    range = com.spendtracker.core.aggregation.DateRange(0, 0),
                    includedInrTotalMinor = 0,
                    includedInrCount = 0,
                    categoryBreakdown = emptyList(),
                    foreignCount = 2,
                    excludedCount = 1,
                    reviewCount = 1,
                    dailySeries = emptyList(),
                ),
                comparison = com.spendtracker.core.aggregation.PeriodComparison(0, 0, 0, null),
                hasTransactions = true,
            ),
        )
    }
}

private fun dashboardUiState(period: DashboardPeriod): DashboardUiState {
    val zone = kotlinx.datetime.TimeZone.UTC
    val now = java.time.Instant.parse("2026-09-09T12:00:00Z").toEpochMilli()
    val range = com.spendtracker.core.aggregation.PeriodCalculator.currentRange(now, zone, period)
    val report = com.spendtracker.core.aggregation.ReportAggregator.compute(
        com.spendtracker.app.ui.PreviewData.dashboardLedger,
        range,
        period,
        zone,
    )
    val previous = com.spendtracker.core.aggregation.ReportAggregator.compute(
        com.spendtracker.app.ui.PreviewData.dashboardLedger,
        com.spendtracker.core.aggregation.PeriodCalculator.sameElapsedPreviousRange(now, zone, period),
        period,
        zone,
    )
    return DashboardUiState(
        period = period,
        report = report,
        comparison = com.spendtracker.core.aggregation.ReportAggregator.compare(
            report.includedInrTotalMinor,
            previous.includedInrTotalMinor,
        ),
        hasTransactions = true,
        isDemo = true,
    )
}

@StringRes
private fun DashboardPeriod.labelResource(): Int = when (this) {
    DashboardPeriod.DAY -> R.string.period_day
    DashboardPeriod.WEEK -> R.string.period_week
    DashboardPeriod.MONTH -> R.string.period_month
}

@StringRes
private fun DashboardPeriod.wordResource(): Int = when (this) {
    DashboardPeriod.DAY -> R.string.period_day_word
    DashboardPeriod.WEEK -> R.string.period_week_word
    DashboardPeriod.MONTH -> R.string.period_month_word
}

@StringRes
private fun DashboardUiState.titleResource(): Int = if (periodOffset == 0) {
    when (period) {
        DashboardPeriod.DAY -> R.string.dashboard_today
        DashboardPeriod.WEEK -> R.string.dashboard_this_week
        DashboardPeriod.MONTH -> R.string.dashboard_this_month
    }
} else {
    when (period) {
        DashboardPeriod.DAY -> R.string.dashboard_selected_day
        DashboardPeriod.WEEK -> R.string.dashboard_selected_week
        DashboardPeriod.MONTH -> R.string.dashboard_selected_month
    }
}
