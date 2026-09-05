package com.spendtracker.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spendtracker.app.R
import com.spendtracker.app.ui.DashboardUiState
import com.spendtracker.app.ui.components.DemoBanner
import com.spendtracker.app.ui.components.InfoCard
import com.spendtracker.app.ui.components.ScreenHeader
import com.spendtracker.app.ui.format.formatMoney
import com.spendtracker.app.ui.theme.SpendTrackerTheme
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.Money

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
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

        if (state.includedTransactions == 0) {
            InfoCard(
                title = stringResource(R.string.dashboard_empty_title),
                body = stringResource(R.string.dashboard_empty_body),
            )
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.detected_spend),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        formatMoney(
                            Money(
                                amountMinor = state.inrSpendMinor,
                                currency = CurrencyCode.INR,
                            ),
                        ),
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            pluralStringResource(
                                R.plurals.included_transaction_count,
                                state.includedTransactions,
                                state.includedTransactions,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (state.foreignTransactions > 0) {
                        Text(
                            pluralStringResource(
                                R.plurals.foreign_transaction_count,
                                state.foreignTransactions,
                                state.foreignTransactions,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        InfoCard(
            title = stringResource(R.string.dashboard_future_title),
            body = stringResource(R.string.dashboard_future_body),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardContentPreview() {
    SpendTrackerTheme {
        DashboardScreen(
            DashboardUiState(
                inrSpendMinor = 352_550,
                includedTransactions = 3,
                foreignTransactions = 1,
                isDemo = true,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardEmptyPreview() {
    SpendTrackerTheme {
        DashboardScreen(DashboardUiState())
    }
}
