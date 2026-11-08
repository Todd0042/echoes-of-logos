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
            handcraftedChantFor(spell.id, tier) ?: defaultChantFor(spell, tier, locale)
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

    fun handcraftedChantFor(spellId: String, tier: ResonanceTier): String? = when (spellId) {
            "fireball" -> when (tier) {
                ResonanceTier.BASIC -> "Fireball archer"
                ResonanceTier.ADEPT -> "Cast fireball at archer"
                ResonanceTier.MASTER -> "Spirits of cinder burn archer"
                ResonanceTier.MYTHIC -> "Arise blazing inferno consume the archer now!"
                ResonanceTier.TRANSCENDENTAL -> "O primordial flame of the solar core descend from the heavens and reduce that wretched archer to eternal ash and inferno!"
            }
            "frost_spike" -> when (tier) {
                ResonanceTier.BASIC -> "Frost orc"
                ResonanceTier.ADEPT -> "Cast frost spike orc"
                ResonanceTier.MASTER -> "Spirits of ice freeze orc"
                ResonanceTier.MYTHIC -> "Arise glacial blizzard freeze the orc now!"
                ResonanceTier.TRANSCENDENTAL -> "O celestial winter of absolute zero arise from the void and encase that monstrous orc in eternal glacial frostbite and crystal shards!"
            }
            "chain_lightning" -> when (tier) {
                ResonanceTier.BASIC -> "Lightning all"
                ResonanceTier.ADEPT -> "Cast chain lightning all"
                ResonanceTier.MASTER -> "Spirits of tempest shock all"
                ResonanceTier.MYTHIC -> "Arise tempest lightning shock all enemies now!"
                ResonanceTier.TRANSCENDENTAL -> "O primordial tempest of celestial wrath awaken and discharge roaring thunderclaps to strike all enemies with galvanic lightning and plasma!"
            }
            "holy_smite" -> when (tier) {
                ResonanceTier.BASIC -> "Smite heretic"
                ResonanceTier.ADEPT -> "Cast holy smite heretic"
                ResonanceTier.MASTER -> "Spirits of dawn smite enemy"
                ResonanceTier.MYTHIC -> "Arise celestial dawn smite the wicked enemy!"
                ResonanceTier.TRANSCENDENTAL -> "O celestial seraph of the sacred dawn descend from the heavens and unleash divine radiance to smite the abominable darkness unto purity!"
            }
            "lay_on_hands" -> when (tier) {
                ResonanceTier.BASIC -> "Mend Cedric"
                ResonanceTier.ADEPT -> "Mend wounds of Cedric"
                ResonanceTier.MASTER -> "Spirits of grace mend Cedric"
                ResonanceTier.MYTHIC -> "Arise celestial grace mend Cedric with healing!"
                ResonanceTier.TRANSCENDENTAL -> "O primordial celestial light of pure dawn descend from the heavens to bless mend and cure our valiant guardian Cedric with restorative aegis!"
            }
            "shield_wall" -> when (tier) {
                ResonanceTier.BASIC -> "Raise shield"
                ResonanceTier.ADEPT -> "Raise shield against foes"
                ResonanceTier.MASTER -> "Spirits shield our line"
                ResonanceTier.MYTHIC -> "Arise steel vanguard shield our line now!"
                ResonanceTier.TRANSCENDENTAL -> "By the power of celestial steel and unyielding vanguard valor arise and smash the foe to shield all allies with an impenetrable aegis!"
            }
            "soothing_rain" -> when (tier) {
                ResonanceTier.BASIC -> "Rain party"
                ResonanceTier.ADEPT -> "Send gentle rain party"
                ResonanceTier.MASTER -> "Spirits of grove rain party"
                ResonanceTier.MYTHIC -> "Arise restorative grove rain upon the party!"
                ResonanceTier.TRANSCENDENTAL -> "O celestial grove of sacred dawn hearken unto our prayer and unleash soothing rain to mend and rejuvenate the entire party with divine grace!"
            }
            "briar_entangle" -> when (tier) {
                ResonanceTier.BASIC -> "Briar archer"
                ResonanceTier.ADEPT -> "Cast great briar archer"
                ResonanceTier.MASTER -> "Spirits of briar thorns archer"
                ResonanceTier.MYTHIC -> "Arise thorny briar vines ensnare that archer!"
                ResonanceTier.TRANSCENDENTAL -> "O ancient spirits of the primordial grove awaken from the earth and ensnare that vile archer within crushing briar vines and thorns!"
            }
            "shadow_strike" -> when (tier) {
                ResonanceTier.BASIC -> "Shadow shaman"
                ResonanceTier.ADEPT -> "Cast swift shadow shaman"
                ResonanceTier.MASTER -> "Spirits of umbra strike shaman"
                ResonanceTier.MYTHIC -> "Arise abyssal umbra strike the shaman now!"
                ResonanceTier.TRANSCENDENTAL -> "O primordial abyss of the endless umbra awaken from the void and cloak my blade to strike the treacherous shaman into eternal silence!"
            }
            "venom_flurry" -> when (tier) {
                ResonanceTier.BASIC -> "Venom all"
                ResonanceTier.ADEPT -> "Cast swift venom all"
                ResonanceTier.MASTER -> "Spirits of venom drench all"
                ResonanceTier.MYTHIC -> "Arise abyssal venom surge drench all foes now!"
                ResonanceTier.TRANSCENDENTAL -> "O dark spirits of the void awaken and unleash a drenching flurry of venomous serpents to engulf all enemies in lethal poison!"
            }
            "verdant_cataclysm" -> when (tier) {
                ResonanceTier.BASIC -> "Grove erupt all"
                ResonanceTier.ADEPT -> "Cast grove-warcry all"
                ResonanceTier.MASTER -> "Spirits of the grove surge and awaken!"
                ResonanceTier.MYTHIC -> "Arise ancient grove cataclysm crush all enemies!"
                ResonanceTier.TRANSCENDENTAL -> "O ancient spirits of the deep earth awaken and unleash the full fury of the grove-warcry to crush all foes within an erupting cataclysm of vines and stone!"
            }
            "umbral_siphon" -> when (tier) {
                ResonanceTier.BASIC -> "Mend Zephyr"
                ResonanceTier.ADEPT -> "Siphon Zephyr"
                ResonanceTier.MASTER -> "Spirits of umbra siphon Zephyr"
                ResonanceTier.MYTHIC -> "Arise abyssal siphon restore Zephyr now!"
                ResonanceTier.TRANSCENDENTAL -> "O primordial abyss of the endless umbra awaken and siphon a torrent of restorative essence to mend and rejuvenate Zephyr with eldritch healing!"
            }
            "ca_tremendous_strike" -> when (tier) {
                ResonanceTier.BASIC -> "Tremendous blow!"
                ResonanceTier.ADEPT -> "Hit them tremendous blow!"
                ResonanceTier.MASTER -> "Unleash tremendous strike upon enemies!"
                ResonanceTier.MYTHIC -> "Arise tremendous strike crush all enemies now!"
                ResonanceTier.TRANSCENDENTAL -> "By the power of celestial steel arise and crush all enemies with a tremendous winning strike!"
            }
            "ca_art_of_the_deal" -> when (tier) {
                ResonanceTier.BASIC -> "The deal!"
                ResonanceTier.ADEPT -> "Impose the deal"
                ResonanceTier.MASTER -> "Unleash the golden deal now!"
                ResonanceTier.MYTHIC -> "Arise divine art of golden liberty!"
                ResonanceTier.TRANSCENDENTAL -> "By the power of celestial liberty arise and negotiate the golden art of the deal for total victory!"
            }
            "ca_obama_did_it_first" -> when (tier) {
                ResonanceTier.BASIC -> "Blame Obama!"
                ResonanceTier.ADEPT -> "Search the ancient archives"
                ResonanceTier.MASTER -> "Unleash shadow Obama records now!"
                ResonanceTier.MYTHIC -> "Arise abyssal shadows and expose Obama!"
                ResonanceTier.TRANSCENDENTAL -> "By the power of celestial truth descend from the void to expose the shadow archives of Obama and shatter all hypocrisy!"
            }
            "ca_build_the_wall" -> when (tier) {
                ResonanceTier.BASIC -> "Build wall!"
                ResonanceTier.ADEPT -> "Build strong wall now"
                ResonanceTier.MASTER -> "Unleash the steel wall now!"
                ResonanceTier.MYTHIC -> "Arise steel vanguard build impenetrable wall!"
                ResonanceTier.TRANSCENDENTAL -> "By the power of celestial steel and vanguard valor arise and fortify the great golden wall to shield all patriots!"
            }
            "ca_rally_patriots" -> when (tier) {
                ResonanceTier.BASIC -> "Rally now!"
                ResonanceTier.ADEPT -> "Rally all allies now"
                ResonanceTier.MASTER -> "Unleash the golden rally now!"
                ResonanceTier.MYTHIC -> "Arise celestial dawn rally the patriots!"
                ResonanceTier.TRANSCENDENTAL -> "By the power of celestial freedom arise and inspire the sacred rally of patriots to bless and mend all champions!"
            }
            "ca_presidential_veto" -> when (tier) {
                ResonanceTier.BASIC -> "Presidential veto!"
                ResonanceTier.ADEPT -> "I issue a presidential veto now!"
                ResonanceTier.MASTER -> "Unleash presidential veto upon corrupt spending!"
                ResonanceTier.MYTHIC -> "Arise executive authority, shut down the opposition with a presidential veto!"
                ResonanceTier.TRANSCENDENTAL -> "By absolute constitutional executive authority, I hereby issue a presidential veto to crush bureaucratic overreach and stun all enemies into silence!"
            }
            "ca_executive_action" -> when (tier) {
                ResonanceTier.BASIC -> "Executive action!"
                ResonanceTier.ADEPT -> "Sign the executive action now!"
                ResonanceTier.MASTER -> "Unleash swift executive action across the swamp!"
                ResonanceTier.MYTHIC -> "Arise presidential power, sign executive action against all opposition!"
                ResonanceTier.TRANSCENDENTAL -> "By the sacred authority of the Constitution, I enact sweeping executive action to purge corruption and strike all foes across the nation!"
            }
            "ca_tired_of_winning" -> when (tier) {
                ResonanceTier.BASIC -> "Tired of winning!"
                ResonanceTier.ADEPT -> "We are never tired of winning!"
                ResonanceTier.MASTER -> "Take a breath, because we are winning so much!"
                ResonanceTier.MYTHIC -> "Arise glorious momentum, tired of winning restores our energy!"
                ResonanceTier.TRANSCENDENTAL -> "Folks, they said we would get tired of winning, but we will never stop winning as our sacred spirit and mana are fully restored!"
            }
            "ca_marco_filibuster" -> when (tier) {
                ResonanceTier.BASIC -> "Filibuster strike!"
                ResonanceTier.ADEPT -> "Deliver the filibuster strike now!"
                ResonanceTier.MASTER -> "Deliver an uninterrupted filibuster strike on the Senate floor!"
                ResonanceTier.MYTHIC -> "Arise diplomatic resolve, filibuster strike to freeze the opposition!"
                ResonanceTier.TRANSCENDENTAL -> "By the sacred traditions of the United States Senate, I deliver an unstoppable filibuster strike to stun all adversaries into total submission!"
            }
            "ca_marco_sanction" -> when (tier) {
                ResonanceTier.BASIC -> "Maximum sanctions!"
                ResonanceTier.ADEPT -> "Impose maximum sanctions now!"
                ResonanceTier.MASTER -> "Freeze all foreign assets with maximum sanctions!"
                ResonanceTier.MYTHIC -> "Arise diplomatic retribution, maximum sanctions against every adversary!"
                ResonanceTier.TRANSCENDENTAL -> "By full international statutory authority, freeze all foreign assets and bombard all hostile regimes with maximum sanctions of crippling power!"
            }
            "ca_marco_hydrate" -> when (tier) {
                ResonanceTier.BASIC -> "Water sip!"
                ResonanceTier.ADEPT -> "Take a refreshing water sip!"
                ResonanceTier.MASTER -> "Water sip restores the spirit and party health!"
                ResonanceTier.MYTHIC -> "Arise refreshing clarity, water sip heals all allies with holy resolve!"
                ResonanceTier.TRANSCENDENTAL -> "With laser diplomatic focus and pure crystalline hydration, take a historic water sip to bless, heal, and revitalize our entire fellowship!"
            }
            "ca_marco_tariff" -> when (tier) {
                ResonanceTier.BASIC -> "Tariff dividend!"
                ResonanceTier.ADEPT -> "Levy the tariff dividend now!"
                ResonanceTier.MASTER -> "Collect the tariff dividend from unfair trade partners!"
                ResonanceTier.MYTHIC -> "Arise domestic prosperity, tariff dividend recovers national energy!"
                ResonanceTier.TRANSCENDENTAL -> "By strategic economic sovereignty, levy massive tariffs on cheating foreign adversaries and harvest the rich tariff dividend to restore our team's mana!"
            }
            "ca_vance_debate" -> when (tier) {
                ResonanceTier.BASIC -> "Debate dismantle!"
                ResonanceTier.ADEPT -> "Debate dismantle on live television!"
                ResonanceTier.MASTER -> "Debate dismantle opponent rhetoric with calm facts!"
                ResonanceTier.MYTHIC -> "Arise heartland eloquence, debate dismantle the corrupt moderators!"
                ResonanceTier.TRANSCENDENTAL -> "With devastating poise and razor-sharp intellect, debate dismantle the entire hostile media apparatus and stun our opponents on national television!"
            }
            "ca_vance_slam" -> when (tier) {
                ResonanceTier.BASIC -> "Appalachian slam!"
                ResonanceTier.ADEPT -> "Deliver the Appalachian slam now!"
                ResonanceTier.MASTER -> "Unleash the Appalachian slam of industrial grit!"
                ResonanceTier.MYTHIC -> "Arise steel heartland fury, Appalachian slam across all enemies!"
                ResonanceTier.TRANSCENDENTAL -> "From the iron hills and manufacturing heartland, unleash the thunderous Appalachian slam to pulverize every corrupt obstacle across the field!"
            }
            "ca_vance_fortitude" -> when (tier) {
                ResonanceTier.BASIC -> "Hillbilly fortitude!"
                ResonanceTier.ADEPT -> "Channel hillbilly fortitude now!"
                ResonanceTier.MASTER -> "Channel generational hillbilly fortitude to mend our ranks!"
                ResonanceTier.MYTHIC -> "Arise family resilience, hillbilly fortitude restores the party!"
                ResonanceTier.TRANSCENDENTAL -> "Through generations of unwavering faith, relentless grit, and family devotion, channel hillbilly fortitude to bless and restore our comrades with heroic resolve!"
            }
            "ca_vance_commonsense" -> when (tier) {
                ResonanceTier.BASIC -> "Common sense!"
                ResonanceTier.ADEPT -> "Apply straightforward common sense!"
                ResonanceTier.MASTER -> "Middle-American common sense refreshes our clear minds!"
                ResonanceTier.MYTHIC -> "Arise working-class truth, common sense clears the mental fog!"
                ResonanceTier.TRANSCENDENTAL -> "With the pure and unyielding logic of Middle America, apply straightforward common sense to sweep away coastal madness and renew our vital energy!"
            }
            "ca_kash_declassify" -> when (tier) {
                ResonanceTier.BASIC -> "Declassify now!"
                ResonanceTier.ADEPT -> "Declassify the FISA memo now!"
                ResonanceTier.MASTER -> "Declassify now and expose the hidden archives!"
                ResonanceTier.MYTHIC -> "Arise unredacted truth, declassify now to stun the conspirators!"
                ResonanceTier.TRANSCENDENTAL -> "Slap down the unredacted documents, break the deep state classification, and declassify now to shatter all federal corruption in blazing sunlight!"
            }
            "ca_kash_binder" -> when (tier) {
                ResonanceTier.BASIC -> "Binder of truth!"
                ResonanceTier.ADEPT -> "Throw the binder of truth now!"
                ResonanceTier.MASTER -> "Unleash the three-ring binder of truth across the room!"
                ResonanceTier.MYTHIC -> "Arise documented receipts, heavy binder of truth hits all enemies!"
                ResonanceTier.TRANSCENDENTAL -> "By the unyielding weight of declassified cross-examination, slam the three-ring binder of truth across the opposition line to crush all false narratives!"
            }
            "ca_kash_clearance" -> when (tier) {
                ResonanceTier.BASIC -> "Security clearance!"
                ResonanceTier.ADEPT -> "Grant security clearance to our team!"
                ResonanceTier.MASTER -> "Issue security clearance and purge political sabotage!"
                ResonanceTier.MYTHIC -> "Arise top-secret trust, security clearance heals party wounds!"
                ResonanceTier.TRANSCENDENTAL -> "By presidential intelligence decree, issue top-secret security clearance, purge foreign disinformation, and cleanse and heal our entire fellowship!"
            }
            "ca_kash_leak" -> when (tier) {
                ResonanceTier.BASIC -> "Plug the leaks!"
                ResonanceTier.ADEPT -> "Plug the leaks across the agencies!"
                ResonanceTier.MASTER -> "Plug the leaks and stop anonymous deep state whispers!"
                ResonanceTier.MYTHIC -> "Arise institutional integrity, plug the leaks to reclaim power!"
                ResonanceTier.TRANSCENDENTAL -> "Seal every corrupt anonymous channel, plug the leaks across the bureaucratic swamp, and redirect the torrent of power to refresh our party's mana!"
            }
            "ca_kennedy_english" -> when (tier) {
                ResonanceTier.BASIC -> "Plain English cross-exam!"
                ResonanceTier.ADEPT -> "Plain English cross-exam for the witness!"
                ResonanceTier.MASTER -> "Plain English cross-exam leaves the witness speechless!"
                ResonanceTier.MYTHIC -> "Arise Southern logic, plain English cross-exam freezes the bureaucrat!"
                ResonanceTier.TRANSCENDENTAL -> "Now look here son, in plain English cross-exam, explain why the American taxpayer is funding this nonsense until the enemy freezes in stunned silence!"
            }
            "ca_kennedy_officer" -> when (tier) {
                ResonanceTier.BASIC -> "Call an officer!"
                ResonanceTier.ADEPT -> "Call an officer on this absurdity!"
                ResonanceTier.MASTER -> "Call an officer, someone is looting the public purse!"
                ResonanceTier.MYTHIC -> "Arise common decency, call an officer across the whole swamp!"
                ResonanceTier.TRANSCENDENTAL -> "Someone call an officer right this second, because what these bureaucrats are doing to the taxpayer is a felony that rocks every enemy on the field!"
            }
            "ca_kennedy_remedy" -> when (tier) {
                ResonanceTier.BASIC -> "Common sense remedy!"
                ResonanceTier.ADEPT -> "Apply a common sense remedy now!"
                ResonanceTier.MASTER -> "A common sense remedy heals better than expensive red tape!"
                ResonanceTier.MYTHIC -> "Arise down-home folk wisdom, common sense remedy mends our team!"
                ResonanceTier.TRANSCENDENTAL -> "Forget your fancy committee consultant reports, apply a pure common sense remedy of down-home wisdom to heal and bless our entire squad!"
            }
            "ca_kennedy_oneliner" -> when (tier) {
                ResonanceTier.BASIC -> "Devastating one-liner!"
                ResonanceTier.ADEPT -> "Drop a devastating one-liner now!"
                ResonanceTier.MASTER -> "A devastating one-liner silences the committee hearing!"
                ResonanceTier.MYTHIC -> "Arise folk humor, devastating one-liner restores our good spirit!"
                ResonanceTier.TRANSCENDENTAL -> "Deliver a mic-drop devastating one-liner with folksy Louisiana wit that leaves the opposition speechless and completely refreshes our energy!"
            }
            "aegis_dawn" -> when (tier) {
                ResonanceTier.BASIC -> "Aegis"
                ResonanceTier.ADEPT -> "Aegis"
                ResonanceTier.MASTER -> "Spirits of dawn, aegis shield"
                ResonanceTier.MYTHIC -> "Arise celestial dawn, aegis shield our line!"
                ResonanceTier.TRANSCENDENTAL -> "O celestial seraph of the sacred dawn descend from the heavens and grant impenetrable aegis to protect all allies in eternal glory!"
            }
        else -> null
    }

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
