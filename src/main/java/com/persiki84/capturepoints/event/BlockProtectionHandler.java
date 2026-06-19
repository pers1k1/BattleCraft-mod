package com.persiki84.capturepoints.event;

import com.persiki84.capturepoints.CapturePointsMod;
import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.capturepoints.capture.CapturePointManager;
import com.persiki84.capturepoints.capture.FinalCapturePoint;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CapturePointsMod.MOD_ID)
public class BlockProtectionHandler {
    private static boolean protectionEnabled = false;

    public static void setProtectionEnabled(boolean enabled) {
        protectionEnabled = enabled;
    }

    public static boolean isProtectionEnabled() {
        return protectionEnabled;
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!protectionEnabled) return;

        if (event.getPlayer() instanceof ServerPlayer player) {
            BlockPos blockPos = event.getPos();
            Vec3 blockVec = Vec3.atCenterOf(blockPos);

            for (CapturePoint point : CapturePointManager.getAllPoints()) {
                Vec3 pointVec = Vec3.atCenterOf(point.getPosition());
                double distance = blockVec.distanceTo(pointVec);

                if (distance <= point.getRadius()) {
                    event.setCanceled(true);
                    player.sendSystemMessage(
                            Component.translatable("capturepoints.protection.cannot_break",
                                    Component.literal(point.getName()).withStyle(ChatFormatting.YELLOW)
                            ).withStyle(ChatFormatting.RED)
                    );
                    return;
                }
            }

            for (FinalCapturePoint point : CapturePointManager.getAllFinalPoints()) {
                Vec3 pointVec = Vec3.atCenterOf(point.getPosition());
                double distance = blockVec.distanceTo(pointVec);

                if (distance <= point.getRadius()) {
                    event.setCanceled(true);
                    player.sendSystemMessage(
                            Component.translatable("capturepoints.protection.cannot_break_final",
                                    Component.literal(point.getName()).withStyle(ChatFormatting.YELLOW)
                            ).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    );
                    return;
                }
            }
        }
    }
}
