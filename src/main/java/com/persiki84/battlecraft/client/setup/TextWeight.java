package com.persiki84.battlecraft.client.setup;

public enum TextWeight {
    THIN("thin", -0.45f),
    PLAIN("plain", 0.0f),
    DENSE("dense", 0.45f);

    private final String id;
    private final float amount;

    TextWeight(String id, float amount) {
        this.id = id;
        this.amount = amount;
    }

    public float amount() {
        return amount;
    }

    public String translationKey() {
        return "battlecraft.setup.text." + id;
    }

    public String noteKey() {
        return translationKey() + ".note";
    }
}
