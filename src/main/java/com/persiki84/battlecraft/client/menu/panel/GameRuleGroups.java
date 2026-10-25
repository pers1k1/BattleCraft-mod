package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.rules.GameRule;

import java.util.List;

// WHY: правила делятся на группы одинаково у оператора и у игрока: экран правил матча показывает те же
// WHY: вкладки, что студия игровых правил, а не свой второй список
public final class GameRuleGroups {
    public static final List<GameRule> CLIENT_RULES = List.of(
            GameRule.FIRST_PERSON_ONLY,
            GameRule.BLOCK_DEBUG_KEYS,
            GameRule.BLOCK_ADVANCEMENTS,
            GameRule.BLOCK_MOD_LIST,
            GameRule.BLOCK_RESOURCE_PACKS,
            GameRule.BLOCK_VOICE_GROUPS,
            GameRule.SPECTATOR_LIGHT,
            GameRule.BLOCK_SUBTITLES,
            GameRule.NO_ENCHANT_GLINT,
            GameRule.CHECK_CONFIGS,
            GameRule.SERVER_PULSE
    );

    public static final List<GameRule> COMBAT_RULES = List.of(
            GameRule.AIM_WALKS,
            GameRule.FIRE_WALKS,
            GameRule.RELOAD_WALKS,
            GameRule.ZOOM_LOCKED_WHILE_AIMING,
            GameRule.NO_JUMP_WHILE_AIMING,
            GameRule.NO_DODGE_WHILE_AIMING,
            GameRule.ESCORT_PROJECTILES,
            GameRule.NO_REFIT_WHEN_HURT,
            GameRule.NO_REFIT_IN_COMBAT,
            GameRule.CAPTURE_GLOW,
            GameRule.IFF_DEVICE
    );

    public static final List<GameRule> PARKOUR_RULES = List.of(
            GameRule.PARKOUR_HIDES_WEAPON,
            GameRule.DODGE_BLOCKS_FIRE,
            GameRule.NO_SLIDE_WITH_GUN,
            GameRule.NO_CLIMB_WITH_GUN,
            GameRule.CRAWL_SPAM_PENALTY,
            GameRule.SPRINT_COSTS_STAMINA,
            GameRule.STAMINA_LIMITS_MOVES,
            GameRule.NO_SPRINT_BOOST_WITH_GUN
    );

    private GameRuleGroups() {}
}
