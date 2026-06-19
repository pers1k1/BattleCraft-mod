package com.persiki84.knockdown.cap;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;
import java.util.UUID;

public class KnockdownCapability implements INBTSerializable<CompoundTag> {
    private boolean isKnocked = false;
    private float reviveProgress = 0.0f;
    private int deathTimer = 900;
    private int injectorCooldown = 0;
    private int nextKnockdownTimer = 0;
    private boolean isSelfReviving = false;
    private float surrenderProgress = 0.0f;
    private boolean isSurrendering = false;

    private UUID lastAttackerUUID;

    public boolean isKnocked() { return isKnocked; }
    public void setKnocked(boolean knocked) { this.isKnocked = knocked; }

    public float getReviveProgress() { return reviveProgress; }
    public void setReviveProgress(float progress) { this.reviveProgress = Math.min(progress, 100.0f); }
    public void addReviveProgress(float amount) { this.reviveProgress = Math.min(this.reviveProgress + amount, 100.0f); }

    public int getDeathTimer() { return deathTimer; }
    public void setDeathTimer(int time) { this.deathTimer = time; }

    public int getInjectorCooldown() { return injectorCooldown; }
    public void setInjectorCooldown(int cooldown) { this.injectorCooldown = cooldown; }

    public int getNextKnockdownTimer() { return nextKnockdownTimer; }
    public void setNextKnockdownTimer(int time) { this.nextKnockdownTimer = time; }

    public boolean isSelfReviving() { return isSelfReviving; }
    public void setSelfReviving(boolean selfReviving) { this.isSelfReviving = selfReviving; }

    public float getSurrenderProgress() { return surrenderProgress; }
    public void setSurrenderProgress(float progress) { this.surrenderProgress = Math.min(Math.max(progress, 0.0f), 100.0f); }
    public void addSurrenderProgress(float amount) { this.surrenderProgress = Math.min(Math.max(this.surrenderProgress + amount, 0.0f), 100.0f); }

    public boolean isSurrendering() { return isSurrendering; }
    public void setSurrendering(boolean surrendering) { this.isSurrendering = surrendering; }

    public UUID getLastAttackerUUID() { return lastAttackerUUID; }
    public void setLastAttackerUUID(UUID uuid) { this.lastAttackerUUID = uuid; }

    public void copyFrom(KnockdownCapability other) {
        this.isKnocked = other.isKnocked;
        this.reviveProgress = other.reviveProgress;
        this.deathTimer = other.deathTimer;
        this.injectorCooldown = other.injectorCooldown;
        this.nextKnockdownTimer = other.nextKnockdownTimer;
        this.isSelfReviving = other.isSelfReviving;
        this.surrenderProgress = other.surrenderProgress;
        this.isSurrendering = other.isSurrendering;
        this.lastAttackerUUID = other.lastAttackerUUID;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("isKnocked", isKnocked);
        tag.putFloat("reviveProgress", reviveProgress);
        tag.putInt("deathTimer", deathTimer);
        tag.putInt("injectorCooldown", injectorCooldown);
        tag.putInt("nextKnockdownTimer", nextKnockdownTimer);
        tag.putBoolean("isSelfReviving", isSelfReviving);
        tag.putFloat("surrenderProgress", surrenderProgress);
        tag.putBoolean("isSurrendering", isSurrendering);
        if (lastAttackerUUID != null) {
            tag.putUUID("lastAttacker", lastAttackerUUID);
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        isKnocked = nbt.getBoolean("isKnocked");
        reviveProgress = nbt.getFloat("reviveProgress");
        deathTimer = nbt.getInt("deathTimer");
        injectorCooldown = nbt.getInt("injectorCooldown");
        nextKnockdownTimer = nbt.getInt("nextKnockdownTimer");
        isSelfReviving = nbt.getBoolean("isSelfReviving");
        surrenderProgress = nbt.getFloat("surrenderProgress");
        isSurrendering = nbt.getBoolean("isSurrendering");
        if (nbt.hasUUID("lastAttacker")) {
            lastAttackerUUID = nbt.getUUID("lastAttacker");
        }
    }
}
