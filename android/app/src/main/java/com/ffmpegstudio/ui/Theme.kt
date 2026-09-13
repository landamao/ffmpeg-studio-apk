package com.ffmpegstudio.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// 色板严格对应 Web 版 CSS 变量
data class StudioColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val accentDim: Color,
    val info: Color,
    val warn: Color,
    val danger: Color,
    val dangerDim: Color,
    val monoBg: Color,
    val monoInk: Color,
    val monoMuted: Color,
    val logOk: Color = Color(0xFF2ED88A),
    val logErr: Color = Color(0xFFFF5C6C),
    val logInfo: Color = Color(0xFF5B9DFF),
    val toastBg: Color,
    val toastInk: Color,
)

val LightColors = StudioColors(
    bg = Color(0xFFF4F6F8), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEEF2F6),
    line = Color(0xFFD8E0E8), ink = Color(0xFF15202B), muted = Color(0xFF6B7C8D),
    accent = Color(0xFF0D9F6E), accentDim = Color(0xFFE3F7EF),
    info = Color(0xFF2B6DE8), warn = Color(0xFFC97A00),
    danger = Color(0xFFD93A4A), dangerDim = Color(0xFFFDECEE),
    monoBg = Color(0xFF1A222C), monoInk = Color(0xFFD7E0EA), monoMuted = Color(0xFF8B9AAB),
    toastBg = Color(0xFF15202B), toastInk = Color(0xFFF4F6F8),
)

val DarkColors = StudioColors(
    bg = Color(0xFF0A0E12), surface = Color(0xFF121820), surface2 = Color(0xFF1A222C),
    line = Color(0xFF2A3542), ink = Color(0xFFE8EEF5), muted = Color(0xFF8B9AAB),
    accent = Color(0xFF2ED88A), accentDim = Color(0xFF1A3D30),
    info = Color(0xFF5B9DFF), warn = Color(0xFFFFB020),
    danger = Color(0xFFFF5C6C), dangerDim = Color(0xFF3A1C22),
    monoBg = Color(0xFF0D1218), monoInk = Color(0xFFC5D0DC), monoMuted = Color(0xFF7A8A9A),
    toastBg = Color(0xFFE8EEF5), toastInk = Color(0xFF0A0E12),
)

val LocalStudioColors = staticCompositionLocalOf { LightColors }

@Composable
fun studioThemeColors(themeMode: String): StudioColors {
    val dark = if (themeMode == "system") isSystemInDarkTheme() else themeMode == "dark"
    return if (dark) DarkColors else LightColors
}
