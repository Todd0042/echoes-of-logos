package com.voicerpg.engine.engine

import com.voicerpg.engine.model.Enemy
import com.voicerpg.engine.model.PartyMember
import com.voicerpg.engine.model.ParsedIntent
import com.voicerpg.engine.model.Spell
import com.voicerpg.engine.model.SpellSchool
import com.voicerpg.engine.model.TargetSelection

import com.voicerpg.engine.model.MetaCommand

object IntentParser {

    @Volatile
    var currentLocale: String = "en"

    /**
     * Dynamic party-member matching: a member is invoked when the utterance contains their
     * display name, their lore class, or any voiceAlias declared in their character data file.
     */
    private fun memberMatchesUtterance(member: PartyMember, lower: String): Boolean {
        if (member.name.isNotBlank() && lower.contains(member.name.lowercase())) return true
        if (member.loreClass.isNotBlank() && lower.contains(member.loreClass.lowercase())) return true
        return com.voicerpg.engine.content.GameContent.voiceAliasesFor(member.id)
            .any { it.isNotBlank() && lower.contains(it.lowercase()) }
    }

    fun parse(
        utterance: String,
        availableSpells: List<Spell> = emptyList(),
        activeEnemies: List<Enemy> = emptyList(),
        party: List<PartyMember> = emptyList(),
        locale: String = currentLocale
    ): ParsedIntent {
        val lower = utterance.lowercase().trim()
        val wordsInUtterance = lower.split(Regex("[^\\p{L}0-9]+")).filter { it.isNotBlank() }.toSet()

        // 0. Detect Meta Voice Commands via localized command catalog with English fallback
        var metaCommand = com.voicerpg.engine.content.GameContent.resolveMetaCommand(utterance, locale)
        if (metaCommand == MetaCommand.NONE) {
            metaCommand = when {
                lower.contains("unlock screen") || lower.contains("show screen") || lower.contains("turn on screen") ||
                        lower.contains("open screen") || lower.contains("wake up") || lower.contains("wake screen") ||
                        lower.contains("dismiss lock") || lower.contains("resume screen") || lower == "unlock" -> MetaCommand.UNLOCK_SCREEN

                lower.contains("lock screen") || lower.contains("lock display") || lower.contains("pocket lock") ||
                        lower.contains("blank screen") || lower.contains("hide screen") || lower.contains("dim screen") ||
                        lower == "lock" -> MetaCommand.LOCK_SCREEN

                lower.contains("exit pocket mode") || lower.contains("disable pocket mode") || lower.contains("turn off pocket mode") ||
                        lower.contains("stop pocket mode") || lower.contains("leave pocket mode") || lower.contains("exit eyes free") ||
                        lower.contains("disable eyes free") || lower.contains("turn off eyes free") -> MetaCommand.DISABLE_EYES_FREE

                lower.contains("enable pocket mode") || lower.contains("turn on pocket mode") || lower.contains("start pocket mode") ||
                        lower.contains("enable eyes free") || lower.contains("turn on eyes free") -> MetaCommand.ENABLE_EYES_FREE

                lower.contains("eyes free") || lower.contains("pocket mode") || lower.contains("blind mode") ||
                        lower.contains("screenless") || lower.contains("audio mode") || lower.contains("toggle narrator") ||
                        lower.contains("toggle audio") || lower == "narrator" -> MetaCommand.TOGGLE_EYES_FREE

                lower.contains("auto listen") || lower.contains("hands free") || lower.contains("auto mic") ||
                        lower.contains("automatic listening") -> MetaCommand.TOGGLE_AUTO_LISTEN

                lower.contains("toggle narration") || lower == "narration" || lower.contains("narration on") ||
                        lower.contains("narration off") || lower.contains("read dialogue") || lower.contains("toggle speech") -> MetaCommand.TOGGLE_NARRATION

                lower.contains("read choices") || lower.contains("toggle choices") || lower.contains("read options") ||
                        lower.contains("toggle options reading") || lower.contains("stop reading choices") -> MetaCommand.TOGGLE_READ_CHOICES

                lower.contains("speaker name") || lower.contains("toggle speaker") || lower.contains("who is speaking") ||
                        lower.contains("toggle attribution") || lower == "attribution" || lower.contains("announcer says") -> MetaCommand.TOGGLE_SPEAKER_ATTRIBUTION

                lower.contains("toggle music") || lower == "music" || lower.contains("music on") ||
                        lower.contains("music off") || lower.contains("mute music") || lower.contains("unmute music") ||
                        lower.contains("background music") || lower.contains("bgm") -> MetaCommand.TOGGLE_MUSIC

                lower == "options" || lower == "settings" || lower == "menu" || lower.contains("open options") ||
                        lower.contains("open settings") || lower.contains("show options") -> MetaCommand.OPEN_OPTIONS

                lower.contains("close options") || lower.contains("close settings") || lower == "resume" ||
                        lower == "back" || lower == "close menu" -> MetaCommand.CLOSE_OPTIONS

                lower == "log" || lower == "history" || lower == "backlog" || lower.contains("open log") ||
                        lower.contains("show log") || lower.contains("open history") || lower.contains("dialogue history") ||
                        lower.contains("review dialogue") -> MetaCommand.OPEN_BACKLOG

                lower.contains("close log") || lower.contains("close history") || lower.contains("close backlog") ||
                        lower.contains("dismiss log") -> MetaCommand.CLOSE_BACKLOG

                lower == "skip" || lower.contains("fast forward") || lower.contains("skip dialogue") || lower == "rush" -> MetaCommand.FAST_FORWARD

                lower == "stop" || lower == "halt" || lower == "pause" || lower.contains("stop skip") -> MetaCommand.STOP_FAST_FORWARD

                lower == "recap" || lower == "story so far" || lower.contains("quest recap") ||
                        lower.contains("story recap") || lower == "where was i" || lower.contains("catch me up") ||
                        lower == "quest log" || lower.contains("what happened") -> MetaCommand.STORY_RECAP

                lower == "help" || lower.contains("what can i say") || lower == "commands" ||
                        lower == "voice commands" || lower == "help commands" -> MetaCommand.HELP

                lower.contains("faster narration") || lower.contains("speed up narration") ||
                        lower.contains("faster speech") || lower.contains("faster voice") ||
                        lower == "faster" || lower.contains("speak faster") -> MetaCommand.SPEED_UP_NARRATION

                lower.contains("slower narration") || lower.contains("slow down narration") ||
                        lower.contains("slower speech") || lower.contains("slower voice") ||
                        lower == "slower" || lower.contains("speak slower") -> MetaCommand.SLOW_DOWN_NARRATION

                lower.contains("normal narration") || lower.contains("reset speech speed") ||
                        lower.contains("normal speech speed") || lower.contains("reset narration speed") ||
                        lower.contains("normal speed") || lower.contains("normal voice") -> MetaCommand.RESET_NARRATION_SPEED

                else -> MetaCommand.NONE
            }
        }

        if (metaCommand != MetaCommand.NONE) {
            return ParsedIntent(
                spell = defaultFallbackSpell(),
                target = TargetSelection.FIRST_ALIVE_ENEMY,
                rawUtterance = utterance,
                metaCommand = metaCommand
            )
        }

        val hasHealKeyword = com.voicerpg.engine.content.GameContent.hasAction(utterance, "heal", locale) ||
                lower.contains("heal") || lower.contains("mend") || lower.contains("restore") ||
                lower.contains("cure") || lower.contains("cleanse") || lower.contains("dispel") || lower.contains("purify")

        var targetEnemyId: String? = null
        var targetHeroId: String? = null

        // 1. Detect target — dynamic with legacy fallback
        var target: TargetSelection = when {
            party.any { memberMatchesUtterance(it, lower) } -> {
                val matchedHero = party.first { memberMatchesUtterance(it, lower) }
                targetHeroId = matchedHero.id
                TargetSelection.SPECIFIC_HERO
            }
            lower.contains("cedric") || lower.contains("paladin") || lower.contains("templar") -> {
                targetHeroId = party.firstOrNull { it.id == "cedric" }?.id ?: "cedric"
                TargetSelection.CEDRIC
            }
            lower.contains("lyra") || lower.contains("warden") || lower.contains("druid") -> {
                targetHeroId = party.firstOrNull { it.id == "lyra" }?.id ?: "lyra"
                TargetSelection.LYRA
            }
            lower.contains("zephyr") || lower.contains("assassin") -> {
                targetHeroId = party.firstOrNull { it.id == "zephyr" }?.id ?: "zephyr"
                TargetSelection.ZEPHYR
            }
            lower.contains("aethel") || ((lower.contains("mage") || lower.contains("elementalist")) && hasHealKeyword) -> {
                targetHeroId = party.firstOrNull { it.id == "hero" || it.id == "aethel" }?.id ?: "hero"
                TargetSelection.HERO
            }
            com.voicerpg.engine.content.GameContent.hasGroup(utterance, "self", locale) || "self" in wordsInUtterance || "me" in wordsInUtterance -> TargetSelection.SELF
            com.voicerpg.engine.content.GameContent.hasGroup(utterance, "party", locale) || lower.contains("party") || lower.contains("allies") || lower.contains("team") -> TargetSelection.PARTY_LOWEST
            com.voicerpg.engine.content.GameContent.hasGroup(utterance, "all", locale) || "all" in wordsInUtterance || "everyone" in wordsInUtterance || "horde" in wordsInUtterance ||
                    lower.contains("all enemies") || lower.contains("all foes") -> TargetSelection.ALL_ENEMIES
            else -> TargetSelection.FIRST_ALIVE_ENEMY
        }

        // If target is not a party-specific target, check enemy targets dynamically
        if (target == TargetSelection.FIRST_ALIVE_ENEMY) {
            val aliveEnemies = activeEnemies.filter { it.isAlive }

            // A. Ordinal position matching via localized command catalog with English fallback
            var ordinalIndex = com.voicerpg.engine.content.GameContent.resolveOrdinal(utterance, locale)
            if (ordinalIndex == -1) {
                ordinalIndex = when {
                    lower.contains("first") || lower.contains("1st") || lower.contains("enemy 1") || lower.contains("monster 1") -> 0
                    lower.contains("second") || lower.contains("2nd") || lower.contains("enemy 2") || lower.contains("monster 2") -> 1
                    lower.contains("third") || lower.contains("3rd") || lower.contains("enemy 3") || lower.contains("monster 3") -> 2
                    lower.contains("fourth") || lower.contains("4th") || lower.contains("enemy 4") || lower.contains("monster 4") -> 3
                    lower.contains("fifth") || lower.contains("5th") || lower.contains("enemy 5") || lower.contains("monster 5") -> 4
                    lower.contains("sixth") || lower.contains("6th") || lower.contains("enemy 6") || lower.contains("monster 6") -> 5
                    else -> -1
                }
            }

            if (ordinalIndex in aliveEnemies.indices) {
                val matched = aliveEnemies[ordinalIndex]
                target = TargetSelection.SPECIFIC_ENEMY
                targetEnemyId = matched.id
            } else {
                // B. Dynamic name and subtitle matching from active enemies with relevance scoring
                val scoredEnemies = aliveEnemies.map { enemy ->
                    val nameLower = enemy.name.lowercase()
                    val subtitleLower = enemy.subtitle.lowercase()
                    val idLower = enemy.id.lowercase()
                    var score = 0

                    if (lower.contains(nameLower)) score += 100
                    if (lower.contains(subtitleLower)) score += 30
                    if (lower.contains(idLower)) score += 40

                    val words = (nameLower.split(" ") + subtitleLower.split(" "))
                        .map { it.trim().trimEnd('!', '.', ',', '?') }
                        .filter { it.length >= 3 && it != "the" && it != "and" }

                    for (word in words) {
                        if (lower.contains(word)) {
                            score += if (word.length >= 4) 15 else 8
                        }
                    }
                    enemy to score
                }.filter { it.second > 0 }

                val matchedByName = scoredEnemies.maxByOrNull { it.second }?.first

                if (matchedByName != null) {
                    target = TargetSelection.SPECIFIC_ENEMY
                    targetEnemyId = matchedByName.id
                } else {
                    // C. Fallback to classic keyword matching (useful when activeEnemies list is empty or for presets)
                    when {
                        lower.contains("orc") || lower.contains("warrior") -> {
                            target = TargetSelection.ORC
                            targetEnemyId = "orc"
                        }
                        lower.contains("archer") || lower.contains("skeleton") -> {
                            target = TargetSelection.ARCHER
                            targetEnemyId = "archer"
                        }
                        lower.contains("shaman") || lower.contains("caster") || (lower.contains("mage") && !hasHealKeyword) -> {
                            target = TargetSelection.SHAMAN
                            targetEnemyId = "shaman"
                        }
                        else -> {
                            val currentTargeted = activeEnemies.firstOrNull { it.isTargeted && it.isAlive }
                            if (currentTargeted != null) {
                                target = TargetSelection.SPECIFIC_ENEMY
                                targetEnemyId = currentTargeted.id
                            } else {
                                target = TargetSelection.FIRST_ALIVE_ENEMY
                                targetEnemyId = aliveEnemies.firstOrNull()?.id
                            }
                        }
                    }
                }
            }
        }

        // 2. Detect spell
        val cleanLower = lower.replace(Regex("[^a-z0-9 ]"), " ")

        fun findExactSpellMatch(spells: List<Spell>): Spell? {
            // Level 1: Exact / substring spell name match
            val exactName = spells.firstOrNull { spell ->
                val cleanName = spell.name.lowercase().replace(Regex("[^a-z0-9 ]"), " ")
                cleanLower.contains(cleanName) || lower.contains(spell.name.lowercase())
            }
            if (exactName != null) return exactName

            // Level 2: Multi-word alias match (e.g. "tremendous blow", "stop leaks", "fisa memo")
            return spells.firstOrNull { spell ->
                spell.aliases.any { alias ->
                    alias.contains(" ") && (
                        cleanLower.contains(alias.lowercase().replace(Regex("[^a-z0-9 ]"), " ")) ||
                        lower.contains(alias.lowercase())
                    )
                }
            }
        }

        fun findLooseSpellMatch(spells: List<Spell>): Spell? {
            // Level 3: Single-word alias or key name words (>= 4 characters)
            return spells.firstOrNull { spell ->
                val cleanName = spell.name.lowercase().replace(Regex("[^a-z0-9 ]"), " ")
                val nameWords = cleanName.split(Regex("\\s+")).filter { it.length >= 4 }
                if (nameWords.isNotEmpty() && nameWords.any { cleanLower.contains(it) }) return@firstOrNull true
                val aliasWords = spell.aliases.flatMap { it.lowercase().split(Regex("[^a-z0-9]+")) }
                    .filter { it.length >= 4 }
                aliasWords.any { cleanLower.contains(it) }
            }
        }

        // Step 1: Check active character's exact/multi-word matches
        val activeExactMatch = findExactSpellMatch(availableSpells)

        // Step 2: Check other party members' exact/multi-word matches (prevent loose word match from stealing exact spell names)
        var offTurnFound: Pair<Spell, PartyMember>? = null
        if (activeExactMatch == null) {
            for (member in party) {
                val otherSpells = member.spells.filter { s -> availableSpells.none { it.id == s.id } }
                if (otherSpells.isNotEmpty()) {
                    val directMatch = findExactSpellMatch(otherSpells)
                    if (directMatch != null) {
                        offTurnFound = directMatch to member
                        break
                    }
                }
            }
        }

        // Step 3: Check active character's loose single-word match
        val activeLooseMatch = if (activeExactMatch == null && offTurnFound == null) {
            findLooseSpellMatch(availableSpells)
        } else null

        // Step 4: Check other party members' loose match if still not found
        if (activeExactMatch == null && offTurnFound == null && activeLooseMatch == null) {
            for (member in party) {
                val otherSpells = member.spells.filter { s -> availableSpells.none { it.id == s.id } }
                if (otherSpells.isNotEmpty()) {
                    val looseMatch = findLooseSpellMatch(otherSpells)
                    if (looseMatch != null) {
                        offTurnFound = looseMatch to member
                        break
                    }
                }
            }
        }

        val activeDirectMatch = activeExactMatch ?: activeLooseMatch

        var isWrongCharacter = false
        var wrongCharacterOwnerId: String? = null
        var wrongCharacterOwnerName: String? = null

        val matchedSpell = if (activeDirectMatch != null) {
            activeDirectMatch
        } else if (offTurnFound != null) {
            isWrongCharacter = true
            wrongCharacterOwnerId = offTurnFound.second.id
            wrongCharacterOwnerName = offTurnFound.second.name
            offTurnFound.first
        } else {
            // Breath/recovery high-priority: catch ASR-mangled breath commands by checking
            // whether ANY breath alias keyword appears in the utterance (fuzzy match).
            availableSpells.firstOrNull { spell ->
                spell.manaRestorePct > 0f && spell.aliases.any { alias ->
                    alias.split(" ").filter { it.length >= 4 }.any { word -> lower.contains(word) }
                }
            } ?: availableSpells.firstOrNull { spell ->
                val schoolKeywords = SpellThesaurus.getKeywordsForSchool(spell.school)
                schoolKeywords.any { kw -> kw.length >= 4 && lower.contains(kw) } &&
                    (if (hasHealKeyword) spell.isHeal else !spell.isHeal)
            } ?: availableSpells.firstOrNull { it.manaRestorePct == 0f && (if (hasHealKeyword) it.isHeal else !it.isHeal) }
              ?: availableSpells.firstOrNull { it.manaRestorePct == 0f }
              ?: availableSpells.firstOrNull()
              ?: defaultFallbackSpell()
        }

        // If it's a heal or friendly cleanse spell and targeting was unset/first alive enemy/all enemies, default to party
        val isAllySpell = matchedSpell.isHeal || matchedSpell.cleansesDebuffs || matchedSpell.isCleanse
        if (isAllySpell && (target == TargetSelection.FIRST_ALIVE_ENEMY || target == TargetSelection.ALL_ENEMIES || target == TargetSelection.SPECIFIC_ENEMY)) {
            target = TargetSelection.PARTY_LOWEST
            targetEnemyId = null
        }

        return ParsedIntent(
            spell = matchedSpell,
            target = target,
            rawUtterance = utterance,
            targetEnemyId = targetEnemyId,
            targetHeroId = targetHeroId,
            metaCommand = metaCommand,
            isWrongCharacterSpell = isWrongCharacter,
            wrongCharacterOwnerId = wrongCharacterOwnerId,
            wrongCharacterOwnerName = wrongCharacterOwnerName
        )
    }

    private fun defaultFallbackSpell(): Spell {
        return Spell(
            id = "fallback_strike",
            name = "Strike",
            school = SpellSchool.PHYSICAL,
            basePower = 45,
            mpCost = 0,
            description = "A basic template attack used when no spell matches.",
            exampleChant = "Strike the enemy",
            aliases = listOf("strike", "attack")
        )
    }
}
