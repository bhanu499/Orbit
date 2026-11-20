package com.orbit.assistant.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class OrbitColorPalette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceSubtle: Color,
    val border: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val accentContainer: Color,
    val userBubble: Color,
    val userBubbleText: Color,
    val statusSuccess: Color,
    val statusSuccessContainer: Color,
    val statusWarning: Color,
    val statusWarningContainer: Color,
    val statusError: Color,
    val statusErrorContainer: Color,
    val statusInfo: Color,
    val statusInfoContainer: Color
)

val DarkOrbitColors = OrbitColorPalette(
    background = Color(0xFF0E0F13),
    surface = Color(0xFF15171E),
    surfaceElevated = Color(0xFF1D202A),
    surfaceSubtle = Color(0xFF191B23),
    border = Color(0xFF2A2E3B),
    borderSubtle = Color(0xFF20232E),
    textPrimary = Color(0xFFF2F4F8),
    textSecondary = Color(0xFFA2A9B8),
    textTertiary = Color(0xFF6E7585),
    accentPrimary = Color(0xFF7C6BFF),
    accentSecondary = Color(0xFF5B8DEF),
    accentContainer = Color(0xFF232042),
    userBubble = Color(0xFF252444),
    userBubbleText = Color(0xFFF3F4FA),
    statusSuccess = Color(0xFF34D399),
    statusSuccessContainer = Color(0xFF122C24),
    statusWarning = Color(0xFFFBBF24),
    statusWarningContainer = Color(0xFF2D2412),
    statusError = Color(0xFFF87171),
    statusErrorContainer = Color(0xFF31171A),
    statusInfo = Color(0xFF60A5FA),
    statusInfoContainer = Color(0xFF15243A)
)

val LightOrbitColors = OrbitColorPalette(
    background = Color(0xFFF7F8FB),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF0F2F7),
    surfaceSubtle = Color(0xFFECEEF4),
    border = Color(0xFFDCDFE9),
    borderSubtle = Color(0xFFE9ECF3),
    textPrimary = Color(0xFF111318),
    textSecondary = Color(0xFF565D6D),
    textTertiary = Color(0xFF838A9B),
    accentPrimary = Color(0xFF6352EC),
    accentSecondary = Color(0xFF3B73E6),
    accentContainer = Color(0xFFECE9FE),
    userBubble = Color(0xFF6352EC),
    userBubbleText = Color(0xFFFFFFFF),
    statusSuccess = Color(0xFF059669),
    statusSuccessContainer = Color(0xFFD1FAE5),
    statusWarning = Color(0xFFD97706),
    statusWarningContainer = Color(0xFFFEF3C7),
    statusError = Color(0xFFDC2626),
    statusErrorContainer = Color(0xFFFEE2E2),
    statusInfo = Color(0xFF2563EB),
    statusInfoContainer = Color(0xFFDBEAFE)
)
