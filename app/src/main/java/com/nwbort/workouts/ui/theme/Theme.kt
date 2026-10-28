package com.nwbort.workouts.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// A trail green, used where Material You's wallpaper colours aren't available.
private val Light = lightColorScheme(
    primary = Color(0xFF1F6F4A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA8F2C5),
    onPrimaryContainer = Color(0xFF002112),
    secondary = Color(0xFF4E6355),
    secondaryContainer = Color(0xFFD0E8D6),
    tertiary = Color(0xFF3B6470),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8CD5AA),
    onPrimary = Color(0xFF003822),
    primaryContainer = Color(0xFF005234),
    onPrimaryContainer = Color(0xFFA8F2C5),
    secondary = Color(0xFFB4CCBA),
    secondaryContainer = Color(0xFF364B3E),
    tertiary = Color(0xFFA3CDDB),
)

@Composable
fun WorkoutsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
