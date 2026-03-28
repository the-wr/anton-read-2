package com.antonread.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val BackgroundColor = Color(0xFFFAF8F5)
val SurfaceColor    = Color(0xFFF0EDE8)
val PrimaryColor    = Color(0xFF2E4057)
val CorrectColor    = Color(0xFF3A7D44)
val WrongColor      = Color(0xFFC0392B)
val DimColor        = Color(0xFFBBBBBB)
val KnownColor      = Color(0xFF2E4057)

private val ColorScheme = lightColorScheme(
    primary = PrimaryColor,
    background = BackgroundColor,
    surface = SurfaceColor,
    onPrimary = Color.White,
    onBackground = PrimaryColor,
    onSurface = PrimaryColor,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content
    )
}
