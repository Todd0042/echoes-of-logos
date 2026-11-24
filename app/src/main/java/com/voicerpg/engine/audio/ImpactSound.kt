package com.voicerpg.engine.audio

import com.voicerpg.engine.model.SpellSchool

/** The synthesized impact set for each school (M8); [SfxManager.playImpact] renders them. */
enum class ImpactSound {
    FIRE_CRACKLE, ICE_SHATTER, THUNDER, HOLY_CHIME, SHADOW_THRUM, WIND_WHOOSH, PHYSICAL_THUD;

    companion object {
        fun forSchool(school: SpellSchool): ImpactSound = when (school) {
            SpellSchool.PYROMANCY -> FIRE_CRACKLE
            SpellSchool.CRYOMANCY -> ICE_SHATTER
            SpellSchool.ELECTROMANCY -> THUNDER
            SpellSchool.HOLY -> HOLY_CHIME
            SpellSchool.SHADOW -> SHADOW_THRUM
            SpellSchool.NATURE -> WIND_WHOOSH
            SpellSchool.PHYSICAL -> PHYSICAL_THUD
        }
    }
}
