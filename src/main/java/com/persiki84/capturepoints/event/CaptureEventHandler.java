package com.persiki84.capturepoints.event;

import com.persiki84.capturepoints.CapturePointsMod;
import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.capturepoints.capture.CapturePointManager;
import com.persiki84.capturepoints.capture.CaptureProgress;
import com.persiki84.capturepoints.capture.FinalCapturePoint;
import com.persiki84.capturepoints.network.CaptureUpdatePacket;
import com.persiki84.capturepoints.network.PacketHandler;
import com.persiki84.capturepoints.util.ColorUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(modid = CapturePointsMod.MOD_ID)
public class CaptureEventHandler {

    @SubscribeEvent
    public void onWorldTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel serverLevel) {
            long gameTime = serverLevel.getGameTime();
            if (gameTime % 5 == 0) {
                spawnCapturePointParticles(serverLevel);
            }
            if (gameTime % 15 == 0) {
                spawnFinalPointParticles(serverLevel);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        CaptureProgress progress = CapturePointManager.getCaptureProgress(player.getUUID());
        if (progress == null) return;

        CapturePoint point = progress.getPoint();
        String team = progress.getTeam();

        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                new CaptureUpdatePacket(player.getUUID(), point.getName(), 0.0f, false));

        CapturePointManager.cancelCapture(player.getUUID());

        boolean hasTeammatesInside = !CapturePointManager.getTeamPlayersInRadiusPublic(team, point).isEmpty();

        if (!hasTeammatesInside) {
            boolean isFinal = point instanceof FinalCapturePoint;
            String pointName = point.getName();
            if (isFinal) {
                player.getServer().getPlayerList().broadcastSystemMessage(
                        Component.translatable("capturepoints.capture.final_cancelled_wipe",
                                Component.literal(pointName).withStyle(ChatFormatting.YELLOW)
                        ).withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                        false
                );
            } else {
                player.getServer().getPlayerList().broadcastSystemMessage(
                        Component.translatable("capturepoints.capture.cancelled_wipe",
                                Component.literal(pointName).withStyle(ChatFormatting.YELLOW)
                        ).withStyle(ChatFormatting.RED),
                        false
                );
            }
        } else {
            String playerName = player.getName().getString();
            String pointName = point.getName();
            player.getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("capturepoints.capture.player_died_continue",
                            Component.literal(playerName).withStyle(ChatFormatting.YELLOW),
                            Component.literal(pointName).withStyle(ChatFormatting.GOLD)
                    ).withStyle(ChatFormatting.YELLOW),
                    false
            );
        }
    }

    private void spawnCapturePointParticles(ServerLevel level) {
        for (CapturePoint point : CapturePointManager.getAllPoints()) {
            Vec3 center = Vec3.atCenterOf(point.getPosition());
            int radius = point.getRadius();
            int particleCount = radius * 4;

            for (int i = 0; i < particleCount; i++) {
                double angle = (2 * Math.PI * i) / particleCount;
                double x = center.x + radius * Math.cos(angle);
                double z = center.z + radius * Math.sin(angle);
                double y = center.y;

                String owner = point.getOwnerTeam();
                if (owner != null) {
                    Vector3f color = getTeamColorRGB(level, owner);
                    DustParticleOptions dustOptions = new DustParticleOptions(color, 1.0f);
                    level.sendParticles(dustOptions, x, y, z, 1, 0, 0, 0, 0);
                } else {
                    level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private void spawnFinalPointParticles(ServerLevel level) {
        if (!CapturePointManager.isFinalPointAvailable()) return;

        for (FinalCapturePoint point : CapturePointManager.getAllFinalPoints()) {
            Vec3 center = Vec3.atCenterOf(point.getPosition());
            int radius = point.getRadius();
            int particleCount = 20;

            for (int i = 0; i < particleCount; i++) {
                double angle = (2 * Math.PI * i) / particleCount;
                double x = center.x + radius * Math.cos(angle);
                double z = center.z + radius * Math.sin(angle);
                double y = center.y + 1.0;

                String owner = point.getOwnerTeam();
                if (owner != null) {
                    Vector3f color = getTeamColorRGB(level, owner);
                    color = ColorUtil.blendWithGold(color, 0.3f);
                    DustParticleOptions dustOptions = new DustParticleOptions(color, 1.5f);
                    level.sendParticles(dustOptions, x, y, z, 1, 0, 0, 0, 0);
                } else {
                    Vector3f goldColor = new Vector3f(1.0f, 0.84f, 0.0f);
                    DustParticleOptions dustOptions = new DustParticleOptions(goldColor, 1.5f);
                    level.sendParticles(dustOptions, x, y, z, 1, 0, 0, 0, 0);
                }

                if (level.random.nextFloat() < 0.2f) {
                    level.sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0.5, 0, 0.05);
                }
            }

            
            if (level.random.nextFloat() < 0.5f) {
                level.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 1.0, center.z, 5, 0.2, 2.0, 0.2, 0.1);
            }
        }
    }

    private Vector3f getTeamColorRGB(ServerLevel level, String teamName) {
        var scoreboard = level.getServer().getScoreboard();
        var team = scoreboard.getPlayerTeam(teamName);
        if (team != null) {
            ChatFormatting color = team.getColor();
            Integer colorValue = color.getColor();
            if (colorValue != null) {
                float r = ((colorValue >> 16) & 0xFF) / 255.0f;
                float g = ((colorValue >> 8) & 0xFF) / 255.0f;
                float b = (colorValue & 0xFF) / 255.0f;
                return new Vector3f(r, g, b);
            }
        }
        return new Vector3f(1.0f, 1.0f, 1.0f);
    }
}
