package com.persiki84.quarrymod.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public class QuarryBlockRule {
    public static final int GLOBAL_COOLDOWN = -1;
    public static final int MIN_MULTIPLIER = 1;
    public static final int MAX_MULTIPLIER = 64;
    public static final int MAX_COOLDOWN_SECONDS = 3600;

    private final ResourceLocation block;
    private int cooldownSeconds = GLOBAL_COOLDOWN;
    private int multiplier = MIN_MULTIPLIER;

    public QuarryBlockRule(ResourceLocation block) {
        this.block = block;
    }

    public ResourceLocation block() {
        return block;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    public int multiplier() {
        return multiplier;
    }

    public void cooldown(int seconds) {
        cooldownSeconds = seconds < 0 ? GLOBAL_COOLDOWN : Math.min(MAX_COOLDOWN_SECONDS, seconds);
    }

    public void multiplier(int value) {
        multiplier = Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, value));
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("block", block.toString());
        tag.putInt("cooldown", cooldownSeconds);
        tag.putInt("multiplier", multiplier);
        return tag;
    }

    public static QuarryBlockRule fromNBT(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("block"));
        if (id == null) return null;

        QuarryBlockRule rule = new QuarryBlockRule(id);
        rule.cooldown(tag.getInt("cooldown"));
        rule.multiplier(tag.getInt("multiplier"));
        return rule;
    }
}
