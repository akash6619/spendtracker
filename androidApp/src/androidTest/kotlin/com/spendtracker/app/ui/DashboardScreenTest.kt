package com.spendtracker.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendtracker.app.ui.dashboard.DashboardActions
import com.spendtracker.app.ui.dashboard.DashboardScreen
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.core.aggregation.DateRange
import com.spendtracker.core.aggregation.DashboardPeriod
import com.spendtracker.core.aggregation.PeriodCalculator
import com.spendtracker.core.aggregation.ReportAggregator
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val fixedNow: Long = Instant.parse("2026-09-09T12:00:00Z").toEpochMilli()
    private val zone: TimeZone = TimeZone.UTC

    @Test
    fun populatedDashboardShowsHeadlineCountsCategoriesAndSevenDailyBars() {
        compose.setContent {
            SpendTrackerTheme {
                DashboardScreen(weekState())
            }
        }

        compose.onNodeWithText("This week").assertIsDisplayed()
        // Six included INR rows; p8 review row is an included purchase.
        compose.onNodeWithText("₹3127.00").assertIsDisplayed()
        compose.onNodeWithText("6 included transactions").assertIsDisplayed()
        compose.onNodeWithText("1 foreign").assertIsDisplayed()
        compose.onNodeWithText("1 excluded").assertIsDisplayed()
        compose.onNodeWithTag("link_foreign").assertIsDisplayed()
        compose.onNodeWithTag("link_excluded").assertIsDisplayed()
        compose.onNodeWithTag("category_row_FOOD_AND_DINING").assertIsDisplayed()
        compose.onNodeWithTag("daily_chart").assertIsDisplayed()
        compose.onAllNodes(hasTagPrefix("daily_bar_")).assertCountEquals(7)
        compose.onNodeWithTag("daily_bar_0").performClick()
        compose.onNodeWithTag("daily_bar_popup").assertIsDisplayed()
    }

    @Test
    fun monthSelectorShowsMonthRangeAndRecomputesBars() {
        compose.setContent {
            SpendTrackerTheme {
                DashboardScreen(monthState())
            }
        }

        compose.onNodeWithText("This month").assertIsDisplayed()
        compose.onAllNodes(hasTagPrefix("daily_bar_")).assertCountEquals(30)
    }

    @Test
    fun periodSelectorReportsWeekAndMonthIntents() {
        val requested = mutableListOf<DashboardPeriod>()
        compose.setContent {
            var selected by remember { mutableStateOf(DashboardPeriod.WEEK) }
            SpendTrackerTheme {
                DashboardScreen(
                    weekState().copy(period = selected),
                    actions = DashboardActions(onPeriodSelected = {
                        selected = it
                        requested += it
                    }),
                )
            }
        }

        compose.onNodeWithTag("period_menu").performClick()
        compose.onNodeWithTag("period_month").performClick()
        compose.onNodeWithText("This month").assertIsDisplayed()
        assertEquals(listOf(DashboardPeriod.MONTH), requested)
    }

    @Test
    fun daySelectorAndHistoricalNavigationExposeExpectedControls() {
        val requested = mutableListOf<DashboardPeriod>()
        var previous = 0
        var next = 0
        compose.setContent {
            var state by remember { mutableStateOf(weekState()) }
            SpendTrackerTheme {
                DashboardScreen(
                    state,
                    actions = DashboardActions(
                        onPeriodSelected = {
                            requested += it
                            state = state.copy(period = it, periodOffset = 0)
                        },
                        onPreviousPeriod = {
                            previous += 1
                            state = state.copy(periodOffset = state.periodOffset - 1)
                        },
                        onNextPeriod = {
                            next += 1
                            state = state.copy(periodOffset = minOf(0, state.periodOffset + 1))
                        },
                    ),
                )
            }
        }

        compose.onNodeWithTag("period_menu").performClick()
        compose.onNodeWithTag("period_day").performClick()
        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNodeWithTag("period_next").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Show previous day").assertIsDisplayed()
        compose.onNodeWithTag("period_previous").performClick()
        compose.onNodeWithText("Selected day").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show next day").assertIsDisplayed()
        compose.onNodeWithTag("period_next").assertIsEnabled().performClick()
        compose.onNodeWithTag("period_next").assertIsNotEnabled()

        assertEquals(listOf(DashboardPeriod.DAY), requested)
        assertEquals(1, previous)
        assertEquals(1, next)
    }

    @Test
    fun categoryExcludedForeignAndReviewLinksEmitIntents() {
        val categories = mutableListOf<String>()
        var excluded = 0
        var foreign = 0
        compose.setContent {
            SpendTrackerTheme {
                DashboardScreen(
                    weekState(),
                    actions = DashboardActions(
                        onCategorySelected = { categories += it.name },
                        onExcludedSelected = { excluded += 1 },
                        onForeignSelected = { foreign += 1 },
                    ),
                )
            }
        }

        compose.onNodeWithTag("category_row_FOOD_AND_DINING").performClick()
        compose.onNodeWithTag("link_excluded").performClick()
        compose.onNodeWithTag("link_foreign").performClick()

        assertEquals(listOf("FOOD_AND_DINING"), categories)
        assertEquals(1, excluded)
        assertEquals(1, foreign)
    }

    @Test
    fun emptyDashboardShowsOnboardingStyleEmptyState() {
        compose.setContent {
            SpendTrackerTheme {
                DashboardScreen(DashboardUiState())
            }
        }

        compose.onNodeWithText("No recognized spend yet").assertIsDisplayed()
        compose.onNodeWithTag("period_menu").assertIsDisplayed()
    }

    @Test
    fun noInrSpendShowsExplanationInsteadOfFakeZero() {
        compose.setContent {
            SpendTrackerTheme {
                DashboardScreen(
                    DashboardUiState(
                        report = com.spendtracker.core.aggregation.PeriodReport(
                            period = DashboardPeriod.WEEK,
                            range = DateRange(fixedNow, fixedNow + 7 * 86_400_000L),
                            includedInrTotalMinor = 0,
                            includedInrCount = 0,
                            categoryBreakdown = emptyList(),
                            foreignCount = 2,
                            excludedCount = 1,
                            reviewCount = 0,
                            dailySeries = emptyList(),
                        ),
                        comparison = null,
                        hasTransactions = true,
                    ),
                )
            }
        }

        compose.onNodeWithText("No included INR spend in this period").assertIsDisplayed()
        compose.onNodeWithText("No included INR spend to chart.").assertIsDisplayed()
    }

    private fun weekState(): DashboardUiState {
        val range = PeriodCalculator.currentRange(fixedNow, zone, DashboardPeriod.WEEK)
        val report = ReportAggregator.compute(PreviewData.dashboardLedger, range, DashboardPeriod.WEEK, zone)
        val previous = ReportAggregator.compute(
            PreviewData.dashboardLedger,
            PeriodCalculator.sameElapsedPreviousRange(fixedNow, zone, DashboardPeriod.WEEK),
            DashboardPeriod.WEEK,
            zone,
        )
        return DashboardUiState(
            period = DashboardPeriod.WEEK,
            report = report,
            comparison = ReportAggregator.compare(report.includedInrTotalMinor, previous.includedInrTotalMinor),
            hasTransactions = true,
        )
    }

    private fun monthState(): DashboardUiState {
        val range = PeriodCalculator.currentRange(fixedNow, zone, DashboardPeriod.MONTH)
        val report = ReportAggregator.compute(PreviewData.dashboardLedger, range, DashboardPeriod.MONTH, zone)
        return DashboardUiState(
            period = DashboardPeriod.MONTH,
            report = report,
            comparison = null,
            hasTransactions = true,
        )
    }

    private fun hasTagPrefix(prefix: String) = SemanticsMatcher("has tag prefix $prefix") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }
}
