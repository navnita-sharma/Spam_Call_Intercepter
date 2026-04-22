package com.example.myapplication

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE) }
    val fontScale = sharedPref.getFloat("fontScale", 1.0f)
    
    val currentDensity = LocalDensity.current
    val customDensity = Density(
        density = currentDensity.density,
        fontScale = fontScale
    )

    CompositionLocalProvider(LocalDensity provides customDensity) {
        MaterialTheme(colorScheme = lightColorScheme()) {
            content()
        }
    }
}
