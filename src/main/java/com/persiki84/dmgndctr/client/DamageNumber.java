package com.persiki84.dmgndctr.client;

import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class DamageNumber {
    private static final float WHOLE_EPSILON = 0.05f;

    int target;
    double x;
    double y;
    double z;
    float amount;
    boolean crit;
    float age;
    float life;
    float sinceHit;
    float drift;
    Component text = Component.empty();
    boolean alive;
    boolean onScreen;
    float screenX;
    float screenY;
    float distance;

    void start(int id, double hitX, double hitY, double hitZ, float damage, boolean critical,
               float lifetime, float sideways) {
        target = id;
        amount = 0.0f;
        crit = false;
        age = 0.0f;
        life = lifetime;
        drift = sideways;
        alive = true;
        onScreen = false;
        merge(hitX, hitY, hitZ, damage, critical);
    }

    // WHY: добавленный удар перезапускает и жизнь, и толчок: цифра, которая растёт, обязана
    // WHY: снова читаться как свежая, а не догорать со старым числом внутри
    void merge(double hitX, double hitY, double hitZ, float damage, boolean critical) {
        x = hitX;
        y = hitY;
        z = hitZ;
        amount += damage;
        crit |= critical;
        age = 0.0f;
        sinceHit = 0.0f;
        text = Component.literal(format(amount));
    }

    void advance(float delta) {
        age += delta;
        sinceHit += delta;
        if (age >= life) alive = false;
    }

    public float progress() {
        return life <= 0.0f ? 1.0f : Math.min(1.0f, age / life);
    }

    public float sinceHit() { return sinceHit; }
    public float drift() { return drift; }
    public boolean crit() { return crit; }
    public Component text() { return text; }
    public boolean shown() { return alive && onScreen; }
    public float screenX() { return screenX; }
    public float screenY() { return screenY; }
    public float distance() { return distance; }

    private static String format(float damage) {
        int rounded = Math.round(damage);
        if (Math.abs(damage - rounded) < WHOLE_EPSILON) return String.valueOf(rounded);
        return String.format(Locale.ROOT, "%.1f", damage);
    }
}
