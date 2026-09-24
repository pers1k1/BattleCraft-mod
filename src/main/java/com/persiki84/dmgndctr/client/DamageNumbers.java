package com.persiki84.dmgndctr.client;

import com.persiki84.battlecraft.client.hud.HudConfig;

import java.util.concurrent.ThreadLocalRandom;

public final class DamageNumbers {
    private static final int CAPACITY = 48;
    private static final float BASE_LIFE = 1.0f;
    private static final float CRIT_LIFE = 0.3f;
    private static final float STACK_WINDOW = 0.6f;
    private static final float DRIFT = 7.0f;

    private static final DamageNumber[] numbers = new DamageNumber[CAPACITY];

    static {
        for (int index = 0; index < CAPACITY; index++) {
            numbers[index] = new DamageNumber();
        }
    }

    private DamageNumbers() {}

    public static DamageNumber[] all() {
        return numbers;
    }

    public static void add(int target, double x, double y, double z, float amount, boolean crit) {
        if (!(amount > 0.0f) || Float.isInfinite(amount)) return;

        DamageNumber stacked = HudConfig.damageStack() ? stackable(target) : null;
        if (stacked != null) {
            stacked.merge(x, y, z, amount, crit);
            return;
        }
        float life = (BASE_LIFE + (crit ? CRIT_LIFE : 0.0f)) * HudConfig.damageTime();
        float drift = (float) (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * DRIFT;
        freeOrOldest().start(target, x, y, z, amount, crit, life, drift);
    }

    public static void advance(float delta) {
        for (DamageNumber number : numbers) {
            if (number.alive) number.advance(delta);
        }
    }

    public static void clear() {
        for (DamageNumber number : numbers) {
            number.alive = false;
        }
    }

    private static DamageNumber stackable(int target) {
        for (DamageNumber number : numbers) {
            if (number.alive && number.target == target && number.sinceHit < STACK_WINDOW) return number;
        }
        return null;
    }

    private static DamageNumber freeOrOldest() {
        DamageNumber oldest = numbers[0];
        for (DamageNumber number : numbers) {
            if (!number.alive) return number;
            if (number.progress() > oldest.progress()) oldest = number;
        }
        return oldest;
    }
}
