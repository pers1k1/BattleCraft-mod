package com.persiki84.capturepoints.capture;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

public class CapturePoint {
    private String name;
    private BlockPos position;
    private int radius;
    private int captureTime;
    private int cooldown;
    private String ownerTeam;
    private long lastCaptureTime;
    private ItemStack reward;
    private int rewardAmount;

    private String buffEffect;
    private int buffAmplifier;

    private ItemStack incomeItem;
    private int passiveIncomeAmount;
    private int incomeTimer;
    private int incomeIntervalSeconds;

    private int heightUp = 5;
    private int heightDown = 5;

    public CapturePoint(String name, BlockPos position, int radius, int captureTime, int cooldown) {
        this.name = name;
        this.position = position;
        this.radius = radius;
        this.captureTime = captureTime;
        this.cooldown = cooldown;
        this.ownerTeam = null;
        this.lastCaptureTime = 0;

        this.reward = new ItemStack(Items.DIAMOND);
        this.rewardAmount = 1;

        this.buffEffect = null;
        this.buffAmplifier = 0;

        this.incomeItem = new ItemStack(Items.AIR);
        this.passiveIncomeAmount = 0;
        this.incomeTimer = 0;
        this.incomeIntervalSeconds = 300;
    }

    public String getName() { return name; }
    public BlockPos getPosition() { return position; }
    public int getRadius() { return radius; }
    public int getCaptureTime() { return captureTime; }
    public int getCooldown() { return cooldown; }
    public String getOwnerTeam() { return ownerTeam; }
    public ItemStack getReward() { return reward; }
    public int getRewardAmount() { return rewardAmount; }
    public long getLastCaptureTime() { return lastCaptureTime; }

    public void setName(String name) { this.name = name; }
    public void setPosition(BlockPos position) { this.position = position; }
    public void setRadius(int radius) { this.radius = radius; }
    public void setCaptureTime(int captureTime) { this.captureTime = captureTime; }
    public void setCooldown(int cooldown) { this.cooldown = cooldown; }
    public void setOwnerTeam(String ownerTeam) {
        this.ownerTeam = ownerTeam;
        this.lastCaptureTime = System.currentTimeMillis();
    }
    public void setReward(ItemStack reward) { this.reward = reward; }
    public void setRewardAmount(int amount) { this.rewardAmount = amount; }
    public void resetCooldown() { this.lastCaptureTime = 0; }

    public boolean isOnCooldown() {
        long currentTime = System.currentTimeMillis();
        long cooldownMs = cooldown * 50L;
        return (currentTime - lastCaptureTime) < cooldownMs;
    }

    public long getRemainingCooldown() {
        long currentTime = System.currentTimeMillis();
        long cooldownMs = cooldown * 50L;
        long elapsed = currentTime - lastCaptureTime;
        return Math.max(0, cooldownMs - elapsed) / 1000;
    }

    protected void setLastCaptureTime(long time) { this.lastCaptureTime = time; }

    public String getBuffEffect() { return buffEffect; }
    public void setBuffEffect(String effect) { this.buffEffect = effect; }
    public int getBuffAmplifier() { return buffAmplifier; }
    public void setBuffAmplifier(int amplifier) { this.buffAmplifier = amplifier; }

    public ItemStack getIncomeItem() { return incomeItem; }
    public void setIncomeItem(ItemStack item) { this.incomeItem = item; }

    public int getPassiveIncomeAmount() { return passiveIncomeAmount; }
    public void setPassiveIncomeAmount(int amount) { this.passiveIncomeAmount = amount; }

    public int getIncomeIntervalSeconds() { return incomeIntervalSeconds; }
    public void setIncomeIntervalSeconds(int seconds) { this.incomeIntervalSeconds = seconds; }

    public int getIncomeTimer() { return incomeTimer; }
    public void incrementIncomeTimer() { this.incomeTimer++; }
    public void resetIncomeTimer() { this.incomeTimer = 0; }

    public int getHeightUp() { return heightUp; }
    public void setHeightUp(int heightUp) { this.heightUp = Math.max(0, heightUp); }

    public int getHeightDown() { return heightDown; }
    public void setHeightDown(int heightDown) { this.heightDown = Math.max(0, heightDown); }

    public void resetHeight() {
        this.heightUp = 5;
        this.heightDown = 5;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putInt("x", position.getX());
        tag.putInt("y", position.getY());
        tag.putInt("z", position.getZ());
        tag.putInt("radius", radius);
        tag.putInt("captureTime", captureTime);
        tag.putInt("cooldown", cooldown);

        if (ownerTeam != null) tag.putString("ownerTeam", ownerTeam);
        tag.putLong("lastCaptureTime", lastCaptureTime);
        tag.putString("rewardItem", ForgeRegistries.ITEMS.getKey(reward.getItem()).toString());
        tag.putInt("rewardAmount", rewardAmount);

        if (buffEffect != null) tag.putString("buffEffect", buffEffect);
        tag.putInt("buffAmplifier", buffAmplifier);

        tag.putString("incomeItem", ForgeRegistries.ITEMS.getKey(incomeItem.getItem()).toString());
        tag.putInt("passiveIncomeAmount", passiveIncomeAmount);
        tag.putInt("incomeInterval", incomeIntervalSeconds);

        tag.putInt("heightUp", heightUp);
        tag.putInt("heightDown", heightDown);

        return tag;
    }

    public static CapturePoint load(CompoundTag tag) {
        String name = tag.getString("name");
        BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
        int radius = tag.getInt("radius");
        int captureTime = tag.getInt("captureTime");
        int cooldown = tag.getInt("cooldown");

        CapturePoint point = new CapturePoint(name, pos, radius, captureTime, cooldown);

        if (tag.contains("ownerTeam")) point.ownerTeam = tag.getString("ownerTeam");
        point.lastCaptureTime = tag.getLong("lastCaptureTime");

        if (tag.contains("rewardItem")) {
            Item rewardItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(tag.getString("rewardItem")));
            if (rewardItem != null) point.reward = new ItemStack(rewardItem);
        }
        point.rewardAmount = tag.getInt("rewardAmount");

        if (tag.contains("buffEffect")) point.buffEffect = tag.getString("buffEffect");
        if (tag.contains("buffAmplifier")) point.buffAmplifier = tag.getInt("buffAmplifier");

        if (tag.contains("incomeItem")) {
            Item incItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(tag.getString("incomeItem")));
            if (incItem != null) point.incomeItem = new ItemStack(incItem);
        }
        if (tag.contains("passiveIncomeAmount")) point.passiveIncomeAmount = tag.getInt("passiveIncomeAmount");
        if (tag.contains("incomeInterval")) point.incomeIntervalSeconds = tag.getInt("incomeInterval");

        if (tag.contains("heightUp")) point.heightUp = tag.getInt("heightUp");
        if (tag.contains("heightDown")) point.heightDown = tag.getInt("heightDown");

        return point;
    }
}
