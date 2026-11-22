package com.voicerpg.engine.engine

import com.voicerpg.engine.model.AcousticProfile
import com.voicerpg.engine.model.ResonanceTier
import com.voicerpg.engine.model.Spell

data class ChantPreset(
    val chantText: String,
    val acousticProfile: AcousticProfile,
    val tier: ResonanceTier,
    val label: String
)

/**
 * Generates finely-tuned incantations paired with acoustic profiles designed to hit
 * the requested resonance tier for hands-free and simulator testing.
 *
 * Tier targets:
 * - BASIC: 0% .. 15% (1.0x .. 1.15x) -> Target ~10%
 * - ADEPT: 20% .. 50% (1.20x .. 1.50x) -> Target ~48%
 * - MASTER: 55% .. 95% (1.55x .. 1.95x) -> Target ~87%
 * - MYTHIC: 100% .. 150% (2.0x .. 2.50x) -> Target ~142%
 * - TRANSCENDENTAL: 155% .. 200% (2.55x .. 3.0x MAX) -> Target 200% MAX
 */
object SpellChantPresets {

    fun acousticFor(tier: ResonanceTier): AcousticProfile = when (tier) {
        ResonanceTier.BASIC -> AcousticProfile.BASIC
        ResonanceTier.ADEPT -> AcousticProfile.ADEPT
        ResonanceTier.MASTER -> AcousticProfile.MASTER
        ResonanceTier.MYTHIC -> AcousticProfile.MYTHIC
        ResonanceTier.TRANSCENDENTAL -> AcousticProfile.TRANSCENDENTAL
    }

    fun getPreset(spell: Spell, tier: ResonanceTier, locale: String = "en"): ChantPreset {
        val manifest = runCatching { com.voicerpg.engine.content.GameContent.manifest }.getOrNull()
        val customTemplate = manifest?.resonanceProfile?.tierChants?.get(tier.name)
        val chant = if (!customTemplate.isNullOrBlank()) {
            customTemplate.replace("{name}", spell.name)
        } else if (locale.equals("en", ignoreCase = true)) {
            handcraftedChantFor(spell, tier) ?: defaultChantFor(spell, tier, locale)
        } else {
            defaultChantFor(spell, tier, locale)
        }
        val acoustic = acousticFor(tier)

        val label = when (tier) {
            ResonanceTier.BASIC -> "10% Basic"
            ResonanceTier.ADEPT -> "40% Adept"
            ResonanceTier.MASTER -> "80% Master"
            ResonanceTier.MYTHIC -> "125% Mythic"
            ResonanceTier.TRANSCENDENTAL -> "200% MAX"
        }

        return ChantPreset(
            chantText = chant,
            acousticProfile = acoustic,
            tier = tier,
            label = label
        )
    }

    /** A chant the game wrote for this spell and tier (`presetChants` in its spell JSON), if any. */
    fun handcraftedChantFor(spell: Spell, tier: ResonanceTier): String? =
        spell.presetChants[tier.name]?.takeIf { it.isNotBlank() }

    fun defaultChantFor(spell: Spell, tier: ResonanceTier, locale: String = "en"): String = when (locale.lowercase()) {
        "es" -> when (tier) {
            ResonanceTier.BASIC -> spell.name
            ResonanceTier.ADEPT -> spell.name
            ResonanceTier.MASTER -> "Desata ${spell.name} ahora"
            ResonanceTier.MYTHIC -> "Por el poder del canto, lanza ${spell.name}"
            ResonanceTier.TRANSCENDENTAL ->
                "¡O fuerzas celestiales primordiales desciendan de los cielos, desaten ${spell.name} para destruir a todos en victoria eterna de una vez por todas!"
        }
        "de" -> when (tier) {
            ResonanceTier.BASIC -> spell.name
            ResonanceTier.ADEPT -> spell.name
            ResonanceTier.MASTER -> "Entfessle ${spell.name} jetzt"
            ResonanceTier.MYTHIC -> "Durch die macht des gesangs, wirkt ${spell.name}"
            ResonanceTier.TRANSCENDENTAL ->
                "O urzeitliche himmlische kräfte steigt herab aus den himmeln, entfesselt ${spell.name} um alle im ewigen sieg ein für alle mal zu vernichten!"
        }
        "fr" -> when (tier) {
            ResonanceTier.BASIC -> spell.name
            ResonanceTier.ADEPT -> spell.name
            ResonanceTier.MASTER -> "Déchaine ${spell.name} maintenant"
            ResonanceTier.MYTHIC -> "Par le pouvoir du chant, lance ${spell.name}"
            ResonanceTier.TRANSCENDENTAL ->
                "Ô forces primordiales et célestes descendez des cieux, déchaînez ${spell.name} pour anéantir tout dans une victoire éternelle une bonne fois pour toutes!"
        }
        "pt" -> when (tier) {
            ResonanceTier.BASIC -> spell.name
            ResonanceTier.ADEPT -> spell.name
            ResonanceTier.MASTER -> "Liberte ${spell.name} agora"
            ResonanceTier.MYTHIC -> "Pelo poder do canto antigo, lance ${spell.name}"
            ResonanceTier.TRANSCENDENTAL ->
                "Ó forças primordiais celestiais descam dos céus, libertem ${spell.name} para obliterar tudo na vitória eterna de uma vez por todas!"
        }
        "it" -> when (tier) {
            ResonanceTier.BASIC -> spell.name
            ResonanceTier.ADEPT -> spell.name
            ResonanceTier.MASTER -> "Scatena ${spell.name} adesso"
            ResonanceTier.MYTHIC -> "Per il potere del canto, scaglia ${spell.name}"
            ResonanceTier.TRANSCENDENTAL ->
                "O forze primordiali e celestiali scendete dai cieli, scatenate ${spell.name} per annientare tutti nell'eterna vittoria una volta per tutte!"
        }
        else -> when (tier) {
            ResonanceTier.BASIC -> spell.name
            ResonanceTier.ADEPT -> spell.name
            ResonanceTier.MASTER -> "Unleash ${spell.name} now"
            ResonanceTier.MYTHIC -> "By the power of song, cast ${spell.name}"
            ResonanceTier.TRANSCENDENTAL ->
                "O primordial celestial forces descend from the heavens, unleash ${spell.name} to obliterate all in eternal victory once and for all!"
        }
    }
}
