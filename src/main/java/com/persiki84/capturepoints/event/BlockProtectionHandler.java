package com.persiki84.capturepoints.event;

import com.persiki84.capturepoints.CapturePointsMod;
import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.capturepoints.capture.CapturePointManager;
import com.persiki84.capturepoints.capture.FinalCapturePoint;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CapturePointsMod.MOD_ID)
public class BlockProtectionHandler {
    private static boolean protectionEnabled = false;
    private static boolean playerPlacedBreakable = false;
    private static boolean placementDenied = false;

    public static void setProtectionEnabled(boolean enabled) {
        if (protectionEnabled == enabled) return;

        protectionEnabled = enabled;
        CapturePointManager.persist();
    }

    public static boolean isProtectionEnabled() {
        return protectionEnabled;
    }

    public static void setPlayerPlacedBreakable(boolean enabled) {
        if (playerPlacedBreakable == enabled) return;

        playerPlacedBreakable = enabled;
        if (enabled) placementDenied = false;
        CapturePointManager.persist();
    }

    public static boolean isPlayerPlacedBreakable() {
        return playerPlacedBreakable;
    }

    public static void setPlacementDenied(boolean enabled) {
        if (placementDenied == enabled) return;

        placementDenied = enabled;
        if (enabled) {
            playerPlacedBreakable = false;
            PlacedBlocks.clear();
        }
        CapturePointManager.persist();
    }

    public static boolean isPlacementDenied() {
        return placementDenied;
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!protectionEnabled) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();
        CapturePoint point = pointAt(pos);
        if (point == null) return;

        if (playerPlacedBreakable && PlacedBlocks.forget(event.getLevel(), pos)) return;

        event.setCanceled(true);
        player.sendSystemMessage(refusal(point, "cannot_break", "cannot_break_final"));
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        Entity placer = event.getEntity();
        if (!(placer instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();
        CapturePoint point = pointAt(pos);
        if (point == null) return;

        if (placementDenied) {
            event.setCanceled(true);
            player.sendSystemMessage(refusal(point, "cannot_place", "cannot_place_final"));
            return;
        }

        PlacedBlocks.remember(event.getLevel(), pos);
    }

    private static Component refusal(CapturePoint point, String key, String finalKey) {
        boolean last = point instanceof FinalCapturePoint;
        Component name = Component.literal(point.getName()).withStyle(ChatFormatting.YELLOW);
        MutableComponent message =
                Component.translatable("capturepoints.protection." + (last ? finalKey : key), name);
        return last
                ? message.withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                : message.withStyle(ChatFormatting.RED);
    }

    private static CapturePoint pointAt(BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);

        for (CapturePoint point : CapturePointManager.getAllPoints()) {
            if (point.getArea().containsHorizontally(center.x, center.z)) return point;
        }
        for (FinalCapturePoint point : CapturePointManager.getAllFinalPoints()) {
            if (point.getArea().containsHorizontally(center.x, center.z)) return point;
        }
        return null;
    }
}
