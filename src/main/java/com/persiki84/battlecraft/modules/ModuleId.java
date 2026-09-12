package com.persiki84.battlecraft.modules;

public enum ModuleId {
    AIRDROP("airdrop"),
    CAPTURE_POINTS("capturepoints"),
    COMBAT_TIMER("combattimer"),
    DAMAGE_INDICATOR("dmgndctr"),
    IMMORTALITY("immortality"),
    ITEM_MODIFIERS("itemmodifiers"),
    KILL_REWARD("killreward"),
    KNOCKDOWN("knockdown"),
    MINIMAP("minimap"),
    QUARRY("quarry"),
    SELL("sell"),
    ZONES("zones");

    private final String id;

    ModuleId(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String label() {
        return "battlecraft.module." + id;
    }

}
