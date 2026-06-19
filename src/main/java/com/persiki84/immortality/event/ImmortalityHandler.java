package com.persiki84.immortality.event;

import com.persiki84.immortality.ImmortalityMod;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ImmortalityMod.MOD_ID)
public class ImmortalityHandler {
    private static boolean enabled = false;
    private static int duration = 60;
    private static final Map<UUID, Long> immortalPlayers = new HashMap<>();
    private static MinecraftServer currentServer;

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            immortalPlayers.clear();
        }
        save();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setDuration(int seconds) {
        duration = seconds;
        save();
    }

    public static int getDuration() {
        return duration;
    }

    public static void giveImmortality(ServerPlayer player) {
        long endTime = System.currentTimeMillis() + (duration * 1000L);
        immortalPlayers.put(player.getUUID(), endTime);
    }

    public static void removeImmortality(ServerPlayer player) {
        immortalPlayers.remove(player.getUUID());
    }

    public static boolean isImmortal(ServerPlayer player) {
        if (!enabled) return false;

        Long endTime = immortalPlayers.get(player.getUUID());
        if (endTime == null) return false;

        if (System.currentTimeMillis() > endTime) {
            immortalPlayers.remove(player.getUUID());
            return false;
        }

        return true;
    }

    public static int getRemainingTime(ServerPlayer player) {
        Long endTime = immortalPlayers.get(player.getUUID());
        if (endTime == null) return 0;

        long remaining = endTime - System.currentTimeMillis();
        return Math.max(0, (int) (remaining / 1000));
    }

    public static void clearAll() {
        immortalPlayers.clear();
    }

    public static void setServer(MinecraftServer server) {
        currentServer = server;
        load();
    }

    public static void save() {
        if (currentServer == null) return;

        try {
            File dataFolder = new File(currentServer.getWorldPath(LevelResource.ROOT).toFile(), "immortality");
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File dataFile = new File(dataFolder, "config.dat");
            CompoundTag tag = new CompoundTag();

            tag.putBoolean("enabled", enabled);
            tag.putInt("duration", duration);

            try (FileOutputStream fos = new FileOutputStream(dataFile)) {
                net.minecraft.nbt.NbtIo.writeCompressed(tag, fos);
            }

            ImmortalityMod.LOGGER.info("Saved immortality config: enabled={}, duration={}", enabled, duration);
        } catch (Exception e) {
            ImmortalityMod.LOGGER.error("Failed to save immortality config", e);
        }
    }

    public static void load() {
        if (currentServer == null) return;

        try {
            File dataFolder = new File(currentServer.getWorldPath(LevelResource.ROOT).toFile(), "immortality");
            File dataFile = new File(dataFolder, "config.dat");

            if (!dataFile.exists()) {
                ImmortalityMod.LOGGER.info("No immortality config found, using defaults");
                return;
            }

            try (FileInputStream fis = new FileInputStream(dataFile)) {
                CompoundTag tag = net.minecraft.nbt.NbtIo.readCompressed(fis);

                enabled = tag.getBoolean("enabled");
                duration = tag.getInt("duration");
            }

            ImmortalityMod.LOGGER.info("Loaded immortality config: enabled={}, duration={}", enabled, duration);
        } catch (Exception e) {
            ImmortalityMod.LOGGER.error("Failed to load immortality config", e);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (enabled) {
                giveImmortality(player);
                player.sendSystemMessage(
                        Component.translatable("immortality.respawn.given", duration)
                                .withStyle(ChatFormatting.GOLD)
                );

                ImmortalityMod.LOGGER.info("Gave immortality to {} for {} seconds",
                        player.getName().getString(), duration);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (isImmortal(player)) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (isImmortal(player)) {
                event.setCanceled(true);
            }
        }
    }
}
