package com.voicerpg.engine.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/**
 * Idiomatic Compose localization helper.
 * Automatically triggers recomposition when on-device translations are loaded or updated.
 */
@Composable
fun tr(text: String): String {
    val version by TranslationManager.translationVersion.collectAsState()
    val lang by TranslationManager.currentLanguage.collectAsState()
    return remember(text, version, lang) {
        TranslationManager.translate(text, lang)
    }
}
