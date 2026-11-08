package com.voicerpg.engine.model

import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

/**
 * Region/accent option for voice synthesis and speech recognition.
 */
data class RegionOption(
    val code: String,       // ISO 2-letter uppercase country code (e.g. "US", "GB", "MX", "ES"), or "" for Any/Default
    val displayName: String // Localized display name
)

/**
 * Supported language declaration with available regional variants.
 */
data class LanguageOption(
    val code: String,        // ISO 2-letter lowercase language code (e.g. "en", "es", "ja", "de", "fr")
    val displayName: String,  // Name in English
    val nativeName: String,   // Name in native language
    val regions: List<RegionOption> = emptyList(),
    val isFeatured: Boolean = false // Featured/popular languages shown at top of selection list
)

/**
 * Universal Catalog of all 59 on-device languages supported by Google ML Kit
 * and Android Text-to-Speech / Speech Recognition engines.
 * Content-neutral: provides generic localization metadata for voice synthesis,
 * speech recognition, and command recognition across all supported languages.
 */
object LanguageCatalog {

    /**
     * Hand-curated rich regional accents for major world languages.
     */
    private val REGIONAL_ACCENTS_MAP: Map<String, List<RegionOption>> = mapOf(
        "en" to listOf(
            RegionOption("", "Any / Default"),
            RegionOption("US", "United States"),
            RegionOption("GB", "United Kingdom"),
            RegionOption("CA", "Canada"),
            RegionOption("AU", "Australia"),
            RegionOption("IE", "Ireland"),
            RegionOption("IN", "India"),
            RegionOption("ZA", "South Africa"),
            RegionOption("NG", "Nigeria"),
            RegionOption("NZ", "New Zealand")
        ),
        "es" to listOf(
            RegionOption("", "Cualquiera / Auto"),
            RegionOption("ES", "España"),
            RegionOption("MX", "México"),
            RegionOption("US", "Estados Unidos"),
            RegionOption("AR", "Argentina"),
            RegionOption("CO", "Colombia"),
            RegionOption("CL", "Chile"),
            RegionOption("PE", "Perú")
        ),
        "de" to listOf(
            RegionOption("", "Alle / Standard"),
            RegionOption("DE", "Deutschland"),
            RegionOption("AT", "Österreich"),
            RegionOption("CH", "Schweiz")
        ),
        "fr" to listOf(
            RegionOption("", "Tous / Défaut"),
            RegionOption("FR", "France"),
            RegionOption("CA", "Canada"),
            RegionOption("BE", "Belgique"),
            RegionOption("CH", "Suisse")
        ),
        "pt" to listOf(
            RegionOption("", "Qualquer / Padrão"),
            RegionOption("BR", "Brasil"),
            RegionOption("PT", "Portugal")
        ),
        "it" to listOf(
            RegionOption("", "Tutti / Predefinito"),
            RegionOption("IT", "Italia"),
            RegionOption("CH", "Svizzera")
        ),
        "zh" to listOf(
            RegionOption("", "默认 / Default"),
            RegionOption("CN", "中国大陆 (Simplified)"),
            RegionOption("TW", "台灣 (Traditional)"),
            RegionOption("HK", "香港 (Hong Kong)")
        ),
        "ar" to listOf(
            RegionOption("", "افتراضي / Default"),
            RegionOption("EG", "مصر (Egypt)"),
            RegionOption("SA", "السعودية (Saudi Arabia)"),
            RegionOption("AE", "الإمارات (UAE)")
        )
    )

    private val FEATURED_CODES = setOf("en", "es", "de", "fr", "pt", "it", "ja", "ko", "zh")

    /**
     * Complete list of all 59 languages supported by on-device ML Kit,
     * sorted with Featured languages at the top, then alphabetically by English displayName.
     */
    val SUPPORTED_LANGUAGES: List<LanguageOption> by lazy {
        val allMlKitCodes = TranslateLanguage.getAllLanguages()
        val list = mutableListOf<LanguageOption>()

        for (code in allMlKitCodes) {
            val locale = Locale(code)
            val englishName = locale.getDisplayLanguage(Locale.ENGLISH).replaceFirstChar { it.uppercase() }
            val nativeName = locale.getDisplayLanguage(locale).replaceFirstChar { it.uppercase() }
            val cleanEnglish = if (englishName.isNotBlank()) englishName else code.uppercase()
            val cleanNative = if (nativeName.isNotBlank()) nativeName else cleanEnglish

            val regions = REGIONAL_ACCENTS_MAP[code] ?: listOf(
                RegionOption("", "Standard / Default")
            )

            list.add(
                LanguageOption(
                    code = code,
                    displayName = cleanEnglish,
                    nativeName = cleanNative,
                    regions = regions,
                    isFeatured = code in FEATURED_CODES
                )
            )
        }

        // Sort: featured first (in specific order), then alphabetically
        val featuredOrder = listOf("en", "es", "de", "fr", "pt", "it", "ja", "ko", "zh")
        list.sortedWith(
            compareBy<LanguageOption> { opt ->
                val fIdx = featuredOrder.indexOf(opt.code)
                if (fIdx >= 0) fIdx else 1000
            }.thenBy { it.displayName }
        )
    }

    fun getLanguage(code: String): LanguageOption {
        val clean = code.lowercase().trim().take(2)
        return SUPPORTED_LANGUAGES.firstOrNull { it.code.equals(clean, ignoreCase = true) }
            ?: SUPPORTED_LANGUAGES.firstOrNull { it.code == "en" }
            ?: SUPPORTED_LANGUAGES.first()
    }

    fun getRegionsForLanguage(langCode: String): List<RegionOption> =
        getLanguage(langCode).regions

    fun isValidLanguage(code: String): Boolean {
        val clean = code.lowercase().trim().take(2)
        return SUPPORTED_LANGUAGES.any { it.code.equals(clean, ignoreCase = true) }
    }
}
