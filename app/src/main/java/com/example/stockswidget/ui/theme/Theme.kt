package com.example.stockswidget.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color // Added import
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color.Black,   // Set background to Black
    surface = Color.Black,      // Set surface to Black
    onPrimary = Color.White,    // Assuming white text on primary
    onSecondary = Color.White,  // Assuming white text on secondary
    onTertiary = Color.White,   // Assuming white text on tertiary
    onBackground = Color.White, // White text on black background
    onSurface = Color.White     // White text on black surface
)

private val LightColorScheme = lightColorScheme(
    primary = Purple80, // You might want to adjust primary/secondary for better visibility on black
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color.Black,   // Set background to Black
    surface = Color.Black,      // Set surface to Black
    onPrimary = Color.White,    // Or Color.White depending on Purple40's brightness
    onSecondary = Color.White,  // Or Color.White
    onTertiary = Color.White,   // Or Color.White
    onBackground = Color.White, // White text on black background
    onSurface = Color.White     // White text on black surface

    /* Other default colors to override
    background = Color(0xFFFFFBFE), // Original Light
    surface = Color(0xFFFFFBFE),   // Original Light
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F), // Original Light
    onSurface = Color(0xFF1C1B1F),    // Original Light
    */
)

@Composable
fun StocksWidgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true, // Changed to false to force black background
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            // Even with dynamic color, we might want to enforce black background
            // For now, let's prioritize the explicit black by turning dynamicColor off in the function signature
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}