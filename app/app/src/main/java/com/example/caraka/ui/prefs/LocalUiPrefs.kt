package com.example.caraka.ui.prefs

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Snapshot of user-controlled UI preferences exposed to the whole Compose tree.
 * Lets any composable read language / bigText / highContrast / haptics without
 * threading these values manually through every function.
 */
data class UiPrefsState(
    val language: String = "id",
    val bigText: Boolean = false,
    val highContrast: Boolean = false,
    val haptics: Boolean = true,
    val toggleLanguage: () -> Unit = {},
    val toggleBigText: () -> Unit = {},
    val toggleHighContrast: () -> Unit = {},
    val toggleHaptics: () -> Unit = {}
)

val LocalUiPrefs = compositionLocalOf { UiPrefsState() }

/**
 * Re-wrap the system Context with the chosen language locale so `stringResource`
 * picks the right `values-xx` bucket. Use as a wrapper around the screen tree.
 */
@Composable
fun ProvideLocalizedContext(language: String, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val localizedCtx = remember(ctx, language) { ctx.withLocale(language) }
    val config = remember(language) {
        Configuration(ctx.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
    }
    CompositionLocalProvider(
        LocalContext provides localizedCtx,
        LocalConfiguration provides config,
        content = content
    )
}

private fun Context.withLocale(language: String): Context {
    val locale = Locale.forLanguageTag(language)
    Locale.setDefault(locale)
    val config = Configuration(resources.configuration).apply { setLocale(locale) }
    return createConfigurationContext(config)
}
