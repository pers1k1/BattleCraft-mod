package com.persiki84.capturepoints.capture;

import com.persiki84.shared.zone.ZoneArea;
import com.persiki84.shared.zone.ZoneShape;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

public class CapturePoint {
    private String name;
    private ResourceKey<Level> dimension = Level.OVERWORLD;
    private ZoneArea area;
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

    private CaptureMode mode;
    private RewardSplit rewardSplit;
    private int captureSpeed;
    private int rollbackSpeed;
    private int pressuredRollbackSpeed;
    private int ownedRollbackSpeed;
    private int teamCooldown;
    private CooldownScope teamCooldownScope;

    public static final int DEFAULT_CAPTURE_SPEED = 100;
    public static final int DEFAULT_ROLLBACK_SPEED = 200;
    public static final int DEFAULT_PRESSURED_ROLLBACK_SPEED = 400;
    public static final int DEFAULT_OWNED_ROLLBACK_SPEED = 800;
    public static final int DEFAULT_TEAM_COOLDOWN = 30;

    public CapturePoint(String name, ZoneArea area, int captureTime, int cooldown) {
        this.name = name;
        this.area = area;
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

        this.mode = CaptureMode.DEFAULT;
        this.rewardSplit = RewardSplit.DEFAULT;
        this.captureSpeed = DEFAULT_CAPTURE_SPEED;
        this.rollbackSpeed = DEFAULT_ROLLBACK_SPEED;
        this.pressuredRollbackSpeed = DEFAULT_PRESSURED_ROLLBACK_SPEED;
        this.ownedRollbackSpeed = DEFAULT_OWNED_ROLLBACK_SPEED;
        this.teamCooldown = DEFAULT_TEAM_COOLDOWN;
        this.teamCooldownScope = CooldownScope.DEFAULT;
    }

    protected static ResourceKey<Level> readDimension(CompoundTag tag) {
        if (!tag.contains("dimension")) return Level.OVERWORLD;

        ResourceLocation id = ResourceLocation.tryParse(tag.getString("dimension"));
        return id == null ? Level.OVERWORLD : ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id);
    }

    public String getName() { return name; }
    public ResourceKey<Level> getDimension() { return dimension; }
    public void setDimension(ResourceKey<Level> value) { this.dimension = value; }
    public ZoneArea getArea() { return area; }
    public BlockPos getPosition() { return area.center(); }
    public double getSize() { return area.size(); }
    public ZoneShape getShape() { return area.shape(); }
    public int getCaptureTime() { return captureTime; }
    public int getCooldown() { return cooldown; }
    public String getOwnerTeam() { return ownerTeam; }
    public ItemStack getReward() { return reward; }
    public int getRewardAmount() { return rewardAmount; }
    public void setSize(double size) { this.area = area.withSize(size); }
    public void setShape(ZoneShape shape) { this.area = area.withShape(shape); }
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

    public CaptureMode getMode() { return mode; }
    public void setMode(CaptureMode mode) { this.mode = mode == null ? CaptureMode.DEFAULT : mode; }

    public RewardSplit getRewardSplit() { return rewardSplit; }
    public void setRewardSplit(RewardSplit split) { this.rewardSplit = split == null ? RewardSplit.DEFAULT : split; }

    public int getCaptureSpeed() { return captureSpeed; }
    public void setCaptureSpeed(int percent) { this.captureSpeed = Math.max(1, percent); }

    public int getRollbackSpeed() { return rollbackSpeed; }
    public void setRollbackSpeed(int percent) { this.rollbackSpeed = Math.max(1, percent); }

    public int getPressuredRollbackSpeed() { return pressuredRollbackSpeed; }
    public void setPressuredRollbackSpeed(int percent) { this.pressuredRollbackSpeed = Math.max(1, percent); }

    public int getOwnedRollbackSpeed() { return ownedRollbackSpeed; }
    public void setOwnedRollbackSpeed(int percent) { this.ownedRollbackSpeed = Math.max(1, percent); }

    public int getTeamCooldown() { return teamCooldown; }
    public void setTeamCooldown(int seconds) { this.teamCooldown = Math.max(0, seconds); }

    public CooldownScope getTeamCooldownScope() { return teamCooldownScope; }
    public void setTeamCooldownScope(CooldownScope scope) {
        this.teamCooldownScope = scope == null ? CooldownScope.DEFAULT : scope;
    }

    public double getHeightUp() { return area.heightUp(); }
    public void setHeightUp(double heightUp) { this.area = area.withHeight(heightUp, area.heightDown()); }

    public double getHeightDown() { return area.heightDown(); }
    public void setHeightDown(double heightDown) { this.area = area.withHeight(area.heightUp(), heightDown); }

    public void resetHeight() {
        this.area = area.withHeight(ZoneArea.DEFAULT_HEIGHT, ZoneArea.DEFAULT_HEIGHT);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.putString("dimension", dimension.location().toString());
        area.saveInto(tag);
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
        writeTuning(tag);

        return tag;
    }

    private void writeTuning(CompoundTag tag) {
        tag.putString("captureMode", mode.id());
        tag.putString("rewardSplit", rewardSplit.id());
        tag.putInt("captureSpeed", captureSpeed);
        tag.putInt("rollbackSpeed", rollbackSpeed);
        tag.putInt("pressuredRollbackSpeed", pressuredRollbackSpeed);
        tag.putInt("ownedRollbackSpeed", ownedRollbackSpeed);
        tag.putInt("teamCooldown", teamCooldown);
        tag.putString("teamCooldownScope", teamCooldownScope.id());
    }

    protected static void readTuning(CompoundTag tag, CapturePoint point) {
        point.mode = CaptureMode.byId(tag.getString("captureMode"));
        point.rewardSplit = RewardSplit.byId(tag.getString("rewardSplit"));
        point.teamCooldownScope = CooldownScope.byId(tag.getString("teamCooldownScope"));
        point.captureSpeed = percentOr(tag, "captureSpeed", DEFAULT_CAPTURE_SPEED);
        point.rollbackSpeed = percentOr(tag, "rollbackSpeed", DEFAULT_ROLLBACK_SPEED);
        point.pressuredRollbackSpeed = percentOr(tag, "pressuredRollbackSpeed", DEFAULT_PRESSURED_ROLLBACK_SPEED);
        point.ownedRollbackSpeed = percentOr(tag, "ownedRollbackSpeed", DEFAULT_OWNED_ROLLBACK_SPEED);
        point.teamCooldown = tag.contains("teamCooldown")
                ? Math.max(0, tag.getInt("teamCooldown"))
                : DEFAULT_TEAM_COOLDOWN;
    }

    private static int percentOr(CompoundTag tag, String key, int fallback) {
        return tag.contains(key) ? Math.max(1, tag.getInt(key)) : fallback;
    }

    public static CapturePoint load(CompoundTag tag) {
        String name = tag.getString("name");
        ZoneArea area = ZoneArea.loadFrom(tag);
        int captureTime = tag.getInt("captureTime");
        int cooldown = tag.getInt("cooldown");

        CapturePoint point = new CapturePoint(name, area, captureTime, cooldown);
        point.dimension = readDimension(tag);

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
        readTuning(tag, point);

        return point;
    }
}
