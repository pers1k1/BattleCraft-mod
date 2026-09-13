package com.persiki84.battlecraft.rules;

public enum GameRule {
    FIRST_PERSON_ONLY("first_person_only", true),
    BLOCK_DEBUG_KEYS("block_debug_keys", true),
    BLOCK_ADVANCEMENTS("block_advancements", true),
    BLOCK_MOD_LIST("block_mod_list", true),
    BLOCK_VOICE_GROUPS("block_voice_groups", true),
    BLOCK_RESOURCE_PACKS("block_resource_packs", true),
    AIM_WALKS("aim_walks", true),
    FIRE_WALKS("fire_walks", true),
    RELOAD_WALKS("reload_walks", true),
    ZOOM_LOCKED_WHILE_AIMING("zoom_locked_while_aiming", true),
    NO_JUMP_WHILE_AIMING("no_jump_while_aiming", true),
    NO_DODGE_WHILE_AIMING("no_dodge_while_aiming", true),
    PARKOUR_HIDES_WEAPON("parkour_hides_weapon", true),
    DODGE_BLOCKS_FIRE("dodge_blocks_fire", true),
    NO_SLIDE_WITH_GUN("no_slide_with_gun", true),
    NO_CLIMB_WITH_GUN("no_climb_with_gun", true),
    CRAWL_SPAM_PENALTY("crawl_spam_penalty", true),
    ESCORT_PROJECTILES("escort_projectiles", true),
    SPECTATOR_LIGHT("spectator_light", true),
    SPRINT_COSTS_STAMINA("sprint_costs_stamina", true),
    STAMINA_LIMITS_MOVES("stamina_limits_moves", true),
    NO_SPRINT_BOOST_WITH_GUN("no_sprint_boost_with_gun", true),
    BLOCK_SUBTITLES("block_subtitles", true),
    NO_REFIT_WHEN_HURT("no_refit_when_hurt", true),
    NO_REFIT_IN_COMBAT("no_refit_in_combat", true),
    NO_ENCHANT_GLINT("no_enchant_glint", true),
    CHECK_CONFIGS("check_configs", true),
    CAPTURE_GLOW("capture_glow", true),
    IFF_DEVICE("iff_device", true);

    private final String id;
    private final boolean enabledByDefault;

    GameRule(String id, boolean enabledByDefault) {
        this.id = id;
        this.enabledByDefault = enabledByDefault;
    }

    public String id() {
        return id;
    }

    public boolean enabledByDefault() {
        return enabledByDefault;
    }

    public String label() {
        return "battlecraft.rule." + id;
    }

    public String hint() {
        return "battlecraft.rule." + id + ".hint";
    }

    public static GameRule byId(String id) {
        for (GameRule rule : values()) {
            if (rule.id.equals(id)) return rule;
        }
        return null;
    }
}
