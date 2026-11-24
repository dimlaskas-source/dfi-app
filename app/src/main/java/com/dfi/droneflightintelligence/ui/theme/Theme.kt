package com.dfi.droneflightintelligence.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DfiBg = Color(0xFF0B1117)
val DfiSurface = Color(0xFF131B24)
val DfiSurfaceAlt = Color(0xFF1A2532)
val DfiCyan = Color(0xFF00E5FF)
val DfiGreen = Color(0xFF00E676)
val DfiAmber = Color(0xFFFFB300)
val DfiRed = Color(0xFFFF3D57)
val DfiTextPrimary = Color(0xFFEAF2F8)
val DfiTextMuted = Color(0xFF8FA4B8)

private val DfiColorScheme = darkColorScheme(
    primary = DfiCyan,
    onPrimary = Color(0xFF001F26),
    secondary = DfiGreen,
    background = DfiBg,
    surface = DfiSurface,
    surfaceVariant = DfiSurfaceAlt,
    onBackground = DfiTextPrimary,
    onSurface = DfiTextPrimary,
    error = DfiRed
)

@Composable
fun DfiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DfiColorScheme,
        content = content
    )
}
