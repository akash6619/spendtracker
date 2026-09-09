package com.spendtracker.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.spendtracker.core.model.SpendCategory

/**
 * Single UI color mapping for spend categories across dashboard and ledger screens.
 * Colors provide continuity only; labels and accessibility descriptions continue
 * to carry category meaning without relying on color.
 */
object SpendCategoryPalette {
    fun color(category: SpendCategory): Color = when (category) {
        SpendCategory.FOOD_AND_DINING -> Color(0xFFE7792B)
        SpendCategory.GROCERIES -> Color(0xFF747B73)
        SpendCategory.TRANSPORT, SpendCategory.TRAVEL -> Color(0xFF3278C7)
        SpendCategory.SHOPPING -> Color(0xFF8059BD)
        SpendCategory.BILLS_AND_UTILITIES, SpendCategory.SUBSCRIPTIONS -> Color(0xFF5264AE)
        SpendCategory.HEALTH -> Color(0xFF21867E)
        SpendCategory.HOUSING -> Color(0xFF8B6F47)
        SpendCategory.ENTERTAINMENT -> Color(0xFF7A6FC2)
        SpendCategory.EDUCATION -> Color(0xFF5E7D42)
        SpendCategory.FEES_AND_CHARGES -> Color(0xFFB56A3B)
        SpendCategory.OTHER -> Color(0xFF747B73)
    }
}
