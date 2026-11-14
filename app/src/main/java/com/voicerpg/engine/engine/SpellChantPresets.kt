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

    private val HANDCRAFTED_CHANTS: Map<Pair<String, ResonanceTier>, String> = mapOf(
        ("fireball" to ResonanceTier.BASIC) to "Fireball archer",
        ("fireball" to ResonanceTier.ADEPT) to "Cast fireball at archer",
        ("fireball" to ResonanceTier.MASTER) to "Spirits of cinder burn archer",
        ("fireball" to ResonanceTier.MYTHIC) to "Arise blazing inferno consume the archer now!",
        ("fireball" to ResonanceTier.TRANSCENDENTAL) to "O primordial flame of the solar core descend from the heavens and reduce that wretched archer to eternal ash and inferno!",

        ("frost_spike" to ResonanceTier.BASIC) to "Frost orc",
        ("frost_spike" to ResonanceTier.ADEPT) to "Cast frost spike orc",
        ("frost_spike" to ResonanceTier.MASTER) to "Spirits of ice freeze orc",
        ("frost_spike" to ResonanceTier.MYTHIC) to "Arise glacial blizzard freeze the orc now!",
        ("frost_spike" to ResonanceTier.TRANSCENDENTAL) to "O celestial winter of absolute zero arise from the void and encase that monstrous orc in eternal glacial frostbite and crystal shards!",

        ("chain_lightning" to ResonanceTier.BASIC) to "Lightning all",
        ("chain_lightning" to ResonanceTier.ADEPT) to "Cast chain lightning all",
        ("chain_lightning" to ResonanceTier.MASTER) to "Spirits of tempest shock all",
        ("chain_lightning" to ResonanceTier.MYTHIC) to "Arise tempest lightning shock all enemies now!",
        ("chain_lightning" to ResonanceTier.TRANSCENDENTAL) to "O primordial tempest of celestial wrath awaken and discharge roaring thunderclaps to strike all enemies with galvanic lightning and plasma!",

        ("holy_smite" to ResonanceTier.BASIC) to "Smite heretic",
        ("holy_smite" to ResonanceTier.ADEPT) to "Cast holy smite heretic",
        ("holy_smite" to ResonanceTier.MASTER) to "Spirits of dawn smite enemy",
        ("holy_smite" to ResonanceTier.MYTHIC) to "Arise celestial dawn smite the wicked enemy!",
        ("holy_smite" to ResonanceTier.TRANSCENDENTAL) to "O celestial seraph of the sacred dawn descend from the heavens and unleash divine radiance to smite the abominable darkness unto purity!",

        ("lay_on_hands" to ResonanceTier.BASIC) to "Mend Cedric",
        ("lay_on_hands" to ResonanceTier.ADEPT) to "Mend wounds of Cedric",
        ("lay_on_hands" to ResonanceTier.MASTER) to "Spirits of grace mend Cedric",
        ("lay_on_hands" to ResonanceTier.MYTHIC) to "Arise celestial grace mend Cedric with healing!",
        ("lay_on_hands" to ResonanceTier.TRANSCENDENTAL) to "O primordial celestial light of pure dawn descend from the heavens to bless mend and cure our valiant guardian Cedric with restorative aegis!",

        ("shield_wall" to ResonanceTier.BASIC) to "Raise shield",
        ("shield_wall" to ResonanceTier.ADEPT) to "Raise shield against foes",
        ("shield_wall" to ResonanceTier.MASTER) to "Spirits shield our line",
        ("shield_wall" to ResonanceTier.MYTHIC) to "Arise steel vanguard shield our line now!",
        ("shield_wall" to ResonanceTier.TRANSCENDENTAL) to "By the power of celestial steel and unyielding vanguard valor arise and smash the foe to shield all allies with an impenetrable aegis!",

        ("soothing_rain" to ResonanceTier.BASIC) to "Rain party",
        ("soothing_rain" to ResonanceTier.ADEPT) to "Send gentle rain party",
        ("soothing_rain" to ResonanceTier.MASTER) to "Spirits of grove rain party",
        ("soothing_rain" to ResonanceTier.MYTHIC) to "Arise restorative grove rain upon the party!",
        ("soothing_rain" to ResonanceTier.TRANSCENDENTAL) to "O celestial grove of sacred dawn hearken unto our prayer and unleash soothing rain to mend and rejuvenate the entire party with divine grace!",

        ("briar_entangle" to ResonanceTier.BASIC) to "Briar archer",
        ("briar_entangle" to ResonanceTier.ADEPT) to "Cast great briar archer",
        ("briar_entangle" to ResonanceTier.MASTER) to "Spirits of briar thorns archer",
        ("briar_entangle" to ResonanceTier.MYTHIC) to "Arise thorny briar vines ensnare that archer!",
        ("briar_entangle" to ResonanceTier.TRANSCENDENTAL) to "O ancient spirits of the primordial grove awaken from the earth and ensnare that vile archer within crushing briar vines and thorns!",

        ("shadow_strike" to ResonanceTier.BASIC) to "Shadow shaman",
        ("shadow_strike" to ResonanceTier.ADEPT) to "Cast swift shadow shaman",
        ("shadow_strike" to ResonanceTier.MASTER) to "Spirits of umbra strike shaman",
        ("shadow_strike" to ResonanceTier.MYTHIC) to "Arise abyssal umbra strike the shaman now!",
        ("shadow_strike" to ResonanceTier.TRANSCENDENTAL) to "O primordial abyss of the endless umbra awaken from the void and cloak my blade to strike the treacherous shaman into eternal silence!",

        ("venom_flurry" to ResonanceTier.BASIC) to "Venom all",
        ("venom_flurry" to ResonanceTier.ADEPT) to "Cast swift venom all",
        ("venom_flurry" to ResonanceTier.MASTER) to "Spirits of venom drench all",
        ("venom_flurry" to ResonanceTier.MYTHIC) to "Arise abyssal venom surge drench all foes now!",
        ("venom_flurry" to ResonanceTier.TRANSCENDENTAL) to "O dark spirits of the void awaken and unleash a drenching flurry of venomous serpents to engulf all enemies in lethal poison!",

        ("verdant_cataclysm" to ResonanceTier.BASIC) to "Grove erupt all",
        ("verdant_cataclysm" to ResonanceTier.ADEPT) to "Cast grove-warcry all",
        ("verdant_cataclysm" to ResonanceTier.MASTER) to "Spirits of the grove surge and awaken!",
        ("verdant_cataclysm" to ResonanceTier.MYTHIC) to "Arise ancient grove cataclysm crush all enemies!",
        ("verdant_cataclysm" to ResonanceTier.TRANSCENDENTAL) to "O ancient spirits of the deep earth awaken and unleash the full fury of the grove-warcry to crush all foes within an erupting cataclysm of vines and stone!",

        ("umbral_siphon" to ResonanceTier.BASIC) to "Mend Zephyr",
        ("umbral_siphon" to ResonanceTier.ADEPT) to "Siphon Zephyr",
        ("umbral_siphon" to ResonanceTier.MASTER) to "Spirits of umbra siphon Zephyr",
        ("umbral_siphon" to ResonanceTier.MYTHIC) to "Arise abyssal siphon restore Zephyr now!",
        ("umbral_siphon" to ResonanceTier.TRANSCENDENTAL) to "O primordial abyss of the endless umbra awaken and siphon a torrent of restorative essence to mend and rejuvenate Zephyr with eldritch healing!",

        ("ca_tremendous_strike" to ResonanceTier.BASIC) to "Tremendous blow!",
        ("ca_tremendous_strike" to ResonanceTier.ADEPT) to "Hit them tremendous blow!",
        ("ca_tremendous_strike" to ResonanceTier.MASTER) to "Unleash tremendous strike upon enemies!",
        ("ca_tremendous_strike" to ResonanceTier.MYTHIC) to "Arise tremendous strike crush all enemies now!",
        ("ca_tremendous_strike" to ResonanceTier.TRANSCENDENTAL) to "By the power of celestial steel arise and crush all enemies with a tremendous winning strike!",

        ("ca_art_of_the_deal" to ResonanceTier.BASIC) to "The deal!",
        ("ca_art_of_the_deal" to ResonanceTier.ADEPT) to "Impose the deal",
        ("ca_art_of_the_deal" to ResonanceTier.MASTER) to "Unleash the golden deal now!",
        ("ca_art_of_the_deal" to ResonanceTier.MYTHIC) to "Arise divine art of golden liberty!",
        ("ca_art_of_the_deal" to ResonanceTier.TRANSCENDENTAL) to "By the power of celestial liberty arise and negotiate the golden art of the deal for total victory!",

        ("ca_obama_did_it_first" to ResonanceTier.BASIC) to "Blame Obama!",
        ("ca_obama_did_it_first" to ResonanceTier.ADEPT) to "Search the ancient archives",
        ("ca_obama_did_it_first" to ResonanceTier.MASTER) to "Unleash shadow Obama records now!",
        ("ca_obama_did_it_first" to ResonanceTier.MYTHIC) to "Arise abyssal shadows and expose Obama!",
        ("ca_obama_did_it_first" to ResonanceTier.TRANSCENDENTAL) to "By the power of celestial truth descend from the void to expose the shadow archives of Obama and shatter all hypocrisy!",

        ("ca_build_the_wall" to ResonanceTier.BASIC) to "Build wall!",
        ("ca_build_the_wall" to ResonanceTier.ADEPT) to "Build strong wall now",
        ("ca_build_the_wall" to ResonanceTier.MASTER) to "Unleash the steel wall now!",
        ("ca_build_the_wall" to ResonanceTier.MYTHIC) to "Arise steel vanguard build impenetrable wall!",
        ("ca_build_the_wall" to ResonanceTier.TRANSCENDENTAL) to "By the power of celestial steel and vanguard valor arise and fortify the great golden wall to shield all patriots!",

        ("ca_rally_patriots" to ResonanceTier.BASIC) to "Rally now!",
        ("ca_rally_patriots" to ResonanceTier.ADEPT) to "Rally all allies now",
        ("ca_rally_patriots" to ResonanceTier.MASTER) to "Unleash the golden rally now!",
        ("ca_rally_patriots" to ResonanceTier.MYTHIC) to "Arise celestial dawn rally the patriots!",
        ("ca_rally_patriots" to ResonanceTier.TRANSCENDENTAL) to "By the power of celestial freedom arise and inspire the sacred rally of patriots to bless and mend all champions!",

        ("ca_presidential_veto" to ResonanceTier.BASIC) to "Presidential veto!",
        ("ca_presidential_veto" to ResonanceTier.ADEPT) to "I issue a presidential veto now!",
        ("ca_presidential_veto" to ResonanceTier.MASTER) to "Unleash presidential veto upon corrupt spending!",
        ("ca_presidential_veto" to ResonanceTier.MYTHIC) to "Arise executive authority, shut down the opposition with a presidential veto!",
        ("ca_presidential_veto" to ResonanceTier.TRANSCENDENTAL) to "By absolute constitutional executive authority, I hereby issue a presidential veto to crush bureaucratic overreach and stun all enemies into silence!",

        ("ca_executive_action" to ResonanceTier.BASIC) to "Executive action!",
        ("ca_executive_action" to ResonanceTier.ADEPT) to "Sign the executive action now!",
        ("ca_executive_action" to ResonanceTier.MASTER) to "Unleash swift executive action across the swamp!",
        ("ca_executive_action" to ResonanceTier.MYTHIC) to "Arise presidential power, sign executive action against all opposition!",
        ("ca_executive_action" to ResonanceTier.TRANSCENDENTAL) to "By the sacred authority of the Constitution, I enact sweeping executive action to purge corruption and strike all foes across the nation!",

        ("ca_tired_of_winning" to ResonanceTier.BASIC) to "Tired of winning!",
        ("ca_tired_of_winning" to ResonanceTier.ADEPT) to "We are never tired of winning!",
        ("ca_tired_of_winning" to ResonanceTier.MASTER) to "Take a breath, because we are winning so much!",
        ("ca_tired_of_winning" to ResonanceTier.MYTHIC) to "Arise glorious momentum, tired of winning restores our energy!",
        ("ca_tired_of_winning" to ResonanceTier.TRANSCENDENTAL) to "Folks, they said we would get tired of winning, but we will never stop winning as our sacred spirit and mana are fully restored!",

        ("ca_marco_filibuster" to ResonanceTier.BASIC) to "Filibuster strike!",
        ("ca_marco_filibuster" to ResonanceTier.ADEPT) to "Deliver the filibuster strike now!",
        ("ca_marco_filibuster" to ResonanceTier.MASTER) to "Deliver an uninterrupted filibuster strike on the Senate floor!",
        ("ca_marco_filibuster" to ResonanceTier.MYTHIC) to "Arise diplomatic resolve, filibuster strike to freeze the opposition!",
        ("ca_marco_filibuster" to ResonanceTier.TRANSCENDENTAL) to "By the sacred traditions of the United States Senate, I deliver an unstoppable filibuster strike to stun all adversaries into total submission!",

        ("ca_marco_sanction" to ResonanceTier.BASIC) to "Maximum sanctions!",
        ("ca_marco_sanction" to ResonanceTier.ADEPT) to "Impose maximum sanctions now!",
        ("ca_marco_sanction" to ResonanceTier.MASTER) to "Freeze all foreign assets with maximum sanctions!",
        ("ca_marco_sanction" to ResonanceTier.MYTHIC) to "Arise diplomatic retribution, maximum sanctions against every adversary!",
        ("ca_marco_sanction" to ResonanceTier.TRANSCENDENTAL) to "By full international statutory authority, freeze all foreign assets and bombard all hostile regimes with maximum sanctions of crippling power!",

        ("ca_marco_hydrate" to ResonanceTier.BASIC) to "Water sip!",
        ("ca_marco_hydrate" to ResonanceTier.ADEPT) to "Take a refreshing water sip!",
        ("ca_marco_hydrate" to ResonanceTier.MASTER) to "Water sip restores the spirit and party health!",
        ("ca_marco_hydrate" to ResonanceTier.MYTHIC) to "Arise refreshing clarity, water sip heals all allies with holy resolve!",
        ("ca_marco_hydrate" to ResonanceTier.TRANSCENDENTAL) to "With laser diplomatic focus and pure crystalline hydration, take a historic water sip to bless, heal, and revitalize our entire fellowship!",

        ("ca_marco_tariff" to ResonanceTier.BASIC) to "Tariff dividend!",
        ("ca_marco_tariff" to ResonanceTier.ADEPT) to "Levy the tariff dividend now!",
        ("ca_marco_tariff" to ResonanceTier.MASTER) to "Collect the tariff dividend from unfair trade partners!",
        ("ca_marco_tariff" to ResonanceTier.MYTHIC) to "Arise domestic prosperity, tariff dividend recovers national energy!",
        ("ca_marco_tariff" to ResonanceTier.TRANSCENDENTAL) to "By strategic economic sovereignty, levy massive tariffs on cheating foreign adversaries and harvest the rich tariff dividend to restore our team's mana!",

        ("ca_vance_debate" to ResonanceTier.BASIC) to "Debate dismantle!",
        ("ca_vance_debate" to ResonanceTier.ADEPT) to "Debate dismantle on live television!",
        ("ca_vance_debate" to ResonanceTier.MASTER) to "Debate dismantle opponent rhetoric with calm facts!",
        ("ca_vance_debate" to ResonanceTier.MYTHIC) to "Arise heartland eloquence, debate dismantle the corrupt moderators!",
        ("ca_vance_debate" to ResonanceTier.TRANSCENDENTAL) to "With devastating poise and razor-sharp intellect, debate dismantle the entire hostile media apparatus and stun our opponents on national television!",

        ("ca_vance_slam" to ResonanceTier.BASIC) to "Appalachian slam!",
        ("ca_vance_slam" to ResonanceTier.ADEPT) to "Deliver the Appalachian slam now!",
        ("ca_vance_slam" to ResonanceTier.MASTER) to "Unleash the Appalachian slam of industrial grit!",
        ("ca_vance_slam" to ResonanceTier.MYTHIC) to "Arise steel heartland fury, Appalachian slam across all enemies!",
        ("ca_vance_slam" to ResonanceTier.TRANSCENDENTAL) to "From the iron hills and manufacturing heartland, unleash the thunderous Appalachian slam to pulverize every corrupt obstacle across the field!",

        ("ca_vance_fortitude" to ResonanceTier.BASIC) to "Hillbilly fortitude!",
        ("ca_vance_fortitude" to ResonanceTier.ADEPT) to "Channel hillbilly fortitude now!",
        ("ca_vance_fortitude" to ResonanceTier.MASTER) to "Channel generational hillbilly fortitude to mend our ranks!",
        ("ca_vance_fortitude" to ResonanceTier.MYTHIC) to "Arise family resilience, hillbilly fortitude restores the party!",
        ("ca_vance_fortitude" to ResonanceTier.TRANSCENDENTAL) to "Through generations of unwavering faith, relentless grit, and family devotion, channel hillbilly fortitude to bless and restore our comrades with heroic resolve!",

        ("ca_vance_commonsense" to ResonanceTier.BASIC) to "Common sense!",
        ("ca_vance_commonsense" to ResonanceTier.ADEPT) to "Apply straightforward common sense!",
        ("ca_vance_commonsense" to ResonanceTier.MASTER) to "Middle-American common sense refreshes our clear minds!",
        ("ca_vance_commonsense" to ResonanceTier.MYTHIC) to "Arise working-class truth, common sense clears the mental fog!",
        ("ca_vance_commonsense" to ResonanceTier.TRANSCENDENTAL) to "With the pure and unyielding logic of Middle America, apply straightforward common sense to sweep away coastal madness and renew our vital energy!",

        ("ca_kash_declassify" to ResonanceTier.BASIC) to "Declassify now!",
        ("ca_kash_declassify" to ResonanceTier.ADEPT) to "Declassify the FISA memo now!",
        ("ca_kash_declassify" to ResonanceTier.MASTER) to "Declassify now and expose the hidden archives!",
        ("ca_kash_declassify" to ResonanceTier.MYTHIC) to "Arise unredacted truth, declassify now to stun the conspirators!",
        ("ca_kash_declassify" to ResonanceTier.TRANSCENDENTAL) to "Slap down the unredacted documents, break the deep state classification, and declassify now to shatter all federal corruption in blazing sunlight!",

        ("ca_kash_binder" to ResonanceTier.BASIC) to "Binder of truth!",
        ("ca_kash_binder" to ResonanceTier.ADEPT) to "Throw the binder of truth now!",
        ("ca_kash_binder" to ResonanceTier.MASTER) to "Unleash the three-ring binder of truth across the room!",
        ("ca_kash_binder" to ResonanceTier.MYTHIC) to "Arise documented receipts, heavy binder of truth hits all enemies!",
        ("ca_kash_binder" to ResonanceTier.TRANSCENDENTAL) to "By the unyielding weight of declassified cross-examination, slam the three-ring binder of truth across the opposition line to crush all false narratives!",

        ("ca_kash_clearance" to ResonanceTier.BASIC) to "Security clearance!",
        ("ca_kash_clearance" to ResonanceTier.ADEPT) to "Grant security clearance to our team!",
        ("ca_kash_clearance" to ResonanceTier.MASTER) to "Issue security clearance and purge political sabotage!",
        ("ca_kash_clearance" to ResonanceTier.MYTHIC) to "Arise top-secret trust, security clearance heals party wounds!",
        ("ca_kash_clearance" to ResonanceTier.TRANSCENDENTAL) to "By presidential intelligence decree, issue top-secret security clearance, purge foreign disinformation, and cleanse and heal our entire fellowship!",

        ("ca_kash_leak" to ResonanceTier.BASIC) to "Plug the leaks!",
        ("ca_kash_leak" to ResonanceTier.ADEPT) to "Plug the leaks across the agencies!",
        ("ca_kash_leak" to ResonanceTier.MASTER) to "Plug the leaks and stop anonymous deep state whispers!",
        ("ca_kash_leak" to ResonanceTier.MYTHIC) to "Arise institutional integrity, plug the leaks to reclaim power!",
        ("ca_kash_leak" to ResonanceTier.TRANSCENDENTAL) to "Seal every corrupt anonymous channel, plug the leaks across the bureaucratic swamp, and redirect the torrent of power to refresh our party's mana!",

        ("ca_kennedy_english" to ResonanceTier.BASIC) to "Plain English cross-exam!",
        ("ca_kennedy_english" to ResonanceTier.ADEPT) to "Plain English cross-exam for the witness!",
        ("ca_kennedy_english" to ResonanceTier.MASTER) to "Plain English cross-exam leaves the witness speechless!",
        ("ca_kennedy_english" to ResonanceTier.MYTHIC) to "Arise Southern logic, plain English cross-exam freezes the bureaucrat!",
        ("ca_kennedy_english" to ResonanceTier.TRANSCENDENTAL) to "Now look here son, in plain English cross-exam, explain why the American taxpayer is funding this nonsense until the enemy freezes in stunned silence!",

        ("ca_kennedy_officer" to ResonanceTier.BASIC) to "Call an officer!",
        ("ca_kennedy_officer" to ResonanceTier.ADEPT) to "Call an officer on this absurdity!",
        ("ca_kennedy_officer" to ResonanceTier.MASTER) to "Call an officer, someone is looting the public purse!",
        ("ca_kennedy_officer" to ResonanceTier.MYTHIC) to "Arise common decency, call an officer across the whole swamp!",
        ("ca_kennedy_officer" to ResonanceTier.TRANSCENDENTAL) to "Someone call an officer right this second, because what these bureaucrats are doing to the taxpayer is a felony that rocks every enemy on the field!",

        ("ca_kennedy_remedy" to ResonanceTier.BASIC) to "Common sense remedy!",
        ("ca_kennedy_remedy" to ResonanceTier.ADEPT) to "Apply a common sense remedy now!",
        ("ca_kennedy_remedy" to ResonanceTier.MASTER) to "A common sense remedy heals better than expensive red tape!",
        ("ca_kennedy_remedy" to ResonanceTier.MYTHIC) to "Arise down-home folk wisdom, common sense remedy mends our team!",
        ("ca_kennedy_remedy" to ResonanceTier.TRANSCENDENTAL) to "Forget your fancy committee consultant reports, apply a pure common sense remedy of down-home wisdom to heal and bless our entire squad!",

        ("ca_kennedy_oneliner" to ResonanceTier.BASIC) to "Devastating one-liner!",
        ("ca_kennedy_oneliner" to ResonanceTier.ADEPT) to "Drop a devastating one-liner now!",
        ("ca_kennedy_oneliner" to ResonanceTier.MASTER) to "A devastating one-liner silences the committee hearing!",
        ("ca_kennedy_oneliner" to ResonanceTier.MYTHIC) to "Arise folk humor, devastating one-liner restores our good spirit!",
        ("ca_kennedy_oneliner" to ResonanceTier.TRANSCENDENTAL) to "Deliver a mic-drop devastating one-liner with folksy Louisiana wit that leaves the opposition speechless and completely refreshes our energy!",

        ("aegis_dawn" to ResonanceTier.BASIC) to "Aegis",
        ("aegis_dawn" to ResonanceTier.ADEPT) to "Aegis",
        ("aegis_dawn" to ResonanceTier.MASTER) to "Spirits of dawn, aegis shield",
        ("aegis_dawn" to ResonanceTier.MYTHIC) to "Arise celestial dawn, aegis shield our line!",
        ("aegis_dawn" to ResonanceTier.TRANSCENDENTAL) to "O celestial seraph of the sacred dawn descend from the heavens and grant impenetrable aegis to protect all allies in eternal glory!"
    )

    fun handcraftedChantFor(spellId: String, tier: ResonanceTier): String? =
        HANDCRAFTED_CHANTS[spellId to tier]

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
