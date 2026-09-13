package com.spendtracker.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared spacing scale for the compact balanced-ledger UI.
 * Screen and reusable-component layouts use these values so density changes
 * remain consistent without reducing Material's minimum interaction sizes.
 */
object SpendTrackerSpacing {
    val NarrowPageMargin = 12.dp
    val PageMargin = 16.dp
    val SectionGap = 12.dp
    val RelatedGap = 8.dp
    val TightGap = 4.dp
    val GroupPadding = 16.dp
    val CompactGroupPadding = 12.dp
    val MinimumTouchTarget = 48.dp
}

/** Centered content-width limits for the app's single-column layouts. */
object SpendTrackerWidths {
    val FocusedContent = 600.dp
    val LedgerContent = 720.dp
}

private val SpendTrackerShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

private val SpendTrackerTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B52),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA8F2D3),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF4D635A),
    background = Color(0xFFF7FBF8),
    surface = Color(0xFFF7FBF8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CD6B8),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF00513D),
    onPrimaryContainer = Color(0xFFA8F2D3),
    secondary = Color(0xFFB4CCC0),
)

@Composable
fun SpendTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SpendTrackerTypography,
        shapes = SpendTrackerShapes,
        content = content,
    )
}
