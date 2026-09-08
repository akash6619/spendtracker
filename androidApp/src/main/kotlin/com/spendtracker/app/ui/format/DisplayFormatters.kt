package com.spendtracker.app.ui.format

import androidx.annotation.StringRes
import com.spendtracker.app.R
import com.spendtracker.core.model.CurrencyCode
import com.spendtracker.core.model.Money
import com.spendtracker.core.model.SpendCategory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun formatMoney(money: Money): String {
    val digits = money.currency.fractionDigits
    val scale = powerOfTen(digits)
    val whole = money.amountMinor / scale
    val fraction = money.amountMinor % scale
    val number = if (digits == 0) {
        whole.toString()
    } else {
        "$whole.${fraction.toString().padStart(digits, '0')}"
    }
    return "${money.currency.displayPrefix}$number"
}

fun formatDate(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("d MMM yyyy")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))

/**
 * Half-open period shown using the device time zone. A single-day period uses
 * one date; longer periods use "start – last day".
 */
fun formatRange(startInclusiveEpochMillis: Long, endExclusiveEpochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy")
    val startDay = Instant.ofEpochMilli(startInclusiveEpochMillis).atZone(zone).toLocalDate()
    val lastDay = Instant.ofEpochMilli(endExclusiveEpochMillis).atZone(zone).toLocalDate().minusDays(1)
    return if (startDay == lastDay) {
        formatter.format(startDay)
    } else {
        "${formatter.format(startDay)} – ${formatter.format(lastDay)}"
    }
}

/** Day label for one daily-series bar, e.g. "3 Sep 2026". */
fun dayLabel(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("d MMM yyyy")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))

/** Short weekday label for the week chart axis, e.g. "Mon". */
fun weekdayShortLabel(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        .dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())

@StringRes
fun SpendCategory.labelResource(): Int = when (this) {
    SpendCategory.FOOD_AND_DINING -> R.string.category_food_and_dining
    SpendCategory.GROCERIES -> R.string.category_groceries
    SpendCategory.TRANSPORT -> R.string.category_transport
    SpendCategory.SHOPPING -> R.string.category_shopping
    SpendCategory.BILLS_AND_UTILITIES -> R.string.category_bills_and_utilities
    SpendCategory.HOUSING -> R.string.category_housing
    SpendCategory.HEALTH -> R.string.category_health
    SpendCategory.ENTERTAINMENT -> R.string.category_entertainment
    SpendCategory.TRAVEL -> R.string.category_travel
    SpendCategory.EDUCATION -> R.string.category_education
    SpendCategory.SUBSCRIPTIONS -> R.string.category_subscriptions
    SpendCategory.FEES_AND_CHARGES -> R.string.category_fees_and_charges
    SpendCategory.OTHER -> R.string.category_other
}

private val CurrencyCode.displayPrefix: String
    get() = when (this) {
        CurrencyCode.INR -> "₹"
        CurrencyCode.USD -> "US$"
        CurrencyCode.EUR -> "€"
        CurrencyCode.GBP -> "£"
        CurrencyCode.JPY -> "JP¥"
    }

private fun powerOfTen(exponent: Int): Long {
    var result = 1L
    repeat(exponent) { result *= 10L }
    return result
}
