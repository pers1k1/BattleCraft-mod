package com.persiki84.capturepoints.menu;

import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.capturepoints.capture.CapturePointManager;
import com.persiki84.capturepoints.event.BlockProtectionHandler;
import com.persiki84.shared.menu.MenuStates;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;

public final class CapturePointMenuState {
    public static final String MENU_ID = "capturepoints";
    public static final String POINTS = "points";
    public static final String PROTECTION = "protection";
    public static final String BREAK_PLACED = "breakPlaced";
    public static final String DENY_PLACE = "denyPlace";
    public static final String CAPTURE_MARKERS = "captureMarkers";
    public static final String FINAL_MARKERS = "finalMarkers";
    public static final String FINAL_OPENER_ONLY = "finalOpenerOnly";
    public static final String FINAL_FLAG = "final";

    private static final int TICKS_PER_SECOND = 20;

    private CapturePointMenuState() {}

    public static void register() {
        MenuStates.register(MENU_ID, 2, CapturePointMenuState::snapshot);
    }

    private static CompoundTag snapshot(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        ListTag points = new ListTag();

        for (CapturePoint point : CapturePointManager.getAllPoints()) {
            points.add(describe(point, false));
        }
        for (CapturePoint point : CapturePointManager.getAllFinalPoints()) {
            points.add(describe(point, true));
        }

        tag.put(POINTS, points);
        tag.putBoolean(PROTECTION, BlockProtectionHandler.isProtectionEnabled());
        tag.putBoolean(BREAK_PLACED, BlockProtectionHandler.isPlayerPlacedBreakable());
        tag.putBoolean(DENY_PLACE, BlockProtectionHandler.isPlacementDenied());
        tag.putBoolean(CAPTURE_MARKERS, CapturePointManager.isGlobalCaptureMarkers());
        tag.putBoolean(FINAL_MARKERS, CapturePointManager.isGlobalFinalMarkers());
        tag.putBoolean(FINAL_OPENER_ONLY, CapturePointManager.isFinalForOpenerOnly());
        return tag;
    }

    private static CompoundTag describe(CapturePoint point, boolean last) {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", point.getName());
        tag.putBoolean(FINAL_FLAG, last);
        tag.putString("shape", point.getShape().id());
        tag.putInt("size", (int) Math.round(point.getSize()));
        tag.putInt("heightUp", (int) Math.round(point.getHeightUp()));
        tag.putInt("heightDown", (int) Math.round(point.getHeightDown()));
        tag.putInt("captureTime", point.getCaptureTime() / TICKS_PER_SECOND);
        tag.putInt("cooldown", point.getCooldown() / TICKS_PER_SECOND);
        tag.putString("owner", point.getOwnerTeam() == null ? "" : point.getOwnerTeam());
        tag.putBoolean("required", point.isRequired());
        tag.putBoolean("shownInHud", point.isShownInHud());
        putRewards(tag, point);
        putTuning(tag, point);
        return tag;
    }

    private static void putTuning(CompoundTag tag, CapturePoint point) {
        tag.putString("mode", point.getMode().id());
        tag.putString("split", point.getRewardSplit().id());
        tag.putInt("captureSpeed", point.getCaptureSpeed());
        tag.putInt("rollbackSpeed", point.getRollbackSpeed());
        tag.putInt("pressuredRollbackSpeed", point.getPressuredRollbackSpeed());
        tag.putInt("ownedRollbackSpeed", point.getOwnedRollbackSpeed());
        tag.putInt("teamCooldown", point.getTeamCooldown());
        tag.putString("cooldownScope", point.getTeamCooldownScope().id());
    }

    private static void putRewards(CompoundTag tag, CapturePoint point) {
        tag.putString("rewardItem", itemId(point.getReward()));
        tag.putInt("rewardAmount", point.getRewardAmount());
        tag.putString("incomeItem", itemId(point.getIncomeItem()));
        tag.putInt("incomeAmount", point.getPassiveIncomeAmount());
        tag.putInt("incomeInterval", point.getIncomeIntervalSeconds());
        tag.putString("buff", point.getBuffEffect() == null ? "" : point.getBuffEffect());
        tag.putInt("buffAmplifier", point.getBuffAmplifier());
        tag.putString("command", point.getCaptureCommand() == null ? "" : point.getCaptureCommand());
    }

    private static String itemId(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";

        net.minecraft.resources.ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }
}
