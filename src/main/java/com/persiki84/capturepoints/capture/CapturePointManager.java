package com.persiki84.capturepoints.capture;

import com.persiki84.capturepoints.CapturePointsMod;
import com.persiki84.capturepoints.network.*;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CapturePointManager {

    private static final Map<String, CapturePoint> capturePoints = new ConcurrentHashMap<>();
    private static final Map<String, FinalCapturePoint> finalPoints = new ConcurrentHashMap<>();
    private static final Map<UUID, CaptureProgress> activeCaptures = new ConcurrentHashMap<>();

    private static ServerLevel currentLevel;

    public static void addCapturePoint(CapturePoint point) {
        capturePoints.put(point.getName(), point);
        save(currentLevel);
        syncPoints();
    }

    public static void addFinalPoint(FinalCapturePoint point) {
        finalPoints.put(point.getName(), point);
        save(currentLevel);
        syncFinalPoints();
    }

    public static void removeCapturePoint(String name) {
        CapturePoint point = capturePoints.get(name);
        if (point == null) return;

        removeHologramAt(point.getPosition());

        List<UUID> toCancel = new ArrayList<>();
        for (Map.Entry<UUID, CaptureProgress> entry : activeCaptures.entrySet()) {
            if (entry.getValue().getPoint().getName().equals(name)) {
                toCancel.add(entry.getKey());
            }
        }
        for (UUID playerId : toCancel) {
            CaptureProgress progress = activeCaptures.get(playerId);
            if (progress != null) {
                ServerPlayer player = progress.getPlayer();
                if (player != null) {
                    player.removeEffect(MobEffects.GLOWING);
                    player.sendSystemMessage(Component.translatable("capturepoints.capture.cancelled_point_deleted").withStyle(ChatFormatting.RED));
                }
                PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                        new CaptureUpdatePacket(playerId, name, 0.0f, false));
            }
            activeCaptures.remove(playerId);
        }
        capturePoints.remove(name);
        save(currentLevel);

        if (currentLevel != null) {
            PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new CapturePointRemovePacket(name));
        }
    }

    public static void removeFinalPoint(String name) {
        FinalCapturePoint point = finalPoints.get(name);
        if (point == null) return;

        removeHologramAt(point.getPosition());

        List<UUID> toCancel = new ArrayList<>();
        for (Map.Entry<UUID, CaptureProgress> entry : activeCaptures.entrySet()) {
            if (entry.getValue().getPoint().getName().equals(name)) {
                toCancel.add(entry.getKey());
            }
        }
        for (UUID playerId : toCancel) {
            CaptureProgress progress = activeCaptures.get(playerId);
            if (progress != null) {
                ServerPlayer player = progress.getPlayer();
                if (player != null) {
                    player.removeEffect(MobEffects.GLOWING);
                    player.sendSystemMessage(Component.translatable("capturepoints.capture.cancelled_final").withStyle(ChatFormatting.RED));
                }
                PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                        new CaptureUpdatePacket(playerId, name, 0.0f, false));
            }
            activeCaptures.remove(playerId);
        }
        finalPoints.remove(name);
        save(currentLevel);

        if (currentLevel != null) {
            PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new FinalPointRemovePacket(name));
        }
    }

    public static CapturePoint getCapturePoint(String name) { return capturePoints.get(name); }
    public static FinalCapturePoint getFinalPoint(String name) { return finalPoints.get(name); }
    public static Collection<CapturePoint> getAllPoints() { return capturePoints.values(); }
    public static Collection<FinalCapturePoint> getAllFinalPoints() { return finalPoints.values(); }
    public static CaptureProgress getCaptureProgress(UUID playerId) { return activeCaptures.get(playerId); }
    public static boolean isCapturing(UUID playerId) { return activeCaptures.containsKey(playerId); }

    public static List<ServerPlayer> getTeamPlayersInRadiusPublic(String teamName, CapturePoint point) {
        return getTeamPlayersInRadius(teamName, point);
    }

    public static void tick() {
        if (currentLevel == null) return;

        updateActiveCaptures();
        updatePassiveEffects();

        if (currentLevel.getGameTime() % 100 == 0) {
            updateHolograms();
        }
    }

    private static void updateActiveCaptures() {
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, CaptureProgress> entry : activeCaptures.entrySet()) {
            UUID playerId = entry.getKey();
            CaptureProgress progress = entry.getValue();
            ServerPlayer player = progress.getPlayer();
            CapturePoint point = progress.getPoint();

            if (player == null || !player.isAlive()) {
                if (player != null) {
                    player.removeEffect(MobEffects.GLOWING);
                }
                toRemove.add(playerId);
                PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                        new CaptureUpdatePacket(playerId, point.getName(), 0.0f, false));
                continue;
            }

            if (point instanceof FinalCapturePoint && !isFinalPointAvailable()) {
                player.sendSystemMessage(Component.translatable("capturepoints.capture.final_unavailable").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                player.removeEffect(MobEffects.GLOWING);
                toRemove.add(playerId);
                PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                        new CaptureUpdatePacket(playerId, point.getName(), 0.0f, false));
                continue;
            }

            BlockPos pointPos = point.getPosition();
            double dx = player.getX() - (pointPos.getX() + 0.5);
            double dz = player.getZ() - (pointPos.getZ() + 0.5);
            double horizontalDistSq = dx * dx + dz * dz;
            double radiusSq = Math.pow(point.getRadius() + 0.5, 2);

            double playerY = player.getY();
            double minY = pointPos.getY() - point.getHeightDown();
            double maxY = pointPos.getY() + point.getHeightUp();
            boolean inHeightRange = (playerY >= minY && playerY <= maxY);

            if (horizontalDistSq > radiusSq || !inHeightRange) {
                progress.incrementTicksOutside();
                if (progress.shouldCancel()) {
                    player.sendSystemMessage(Component.translatable("capturepoints.capture.cancelled_left_zone").withStyle(ChatFormatting.RED));
                    player.removeEffect(MobEffects.GLOWING);
                    toRemove.add(playerId);
                    PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                            new CaptureUpdatePacket(playerId, point.getName(), 0.0f, false));
                    continue;
                } else {
                    int remainingTicks = progress.getRemainingTicksOutside();
                    if (remainingTicks % 20 == 0) {
                        player.sendSystemMessage(
                                Component.translatable("capturepoints.capture.return_to_zone", remainingTicks / 20)
                                        .withStyle(ChatFormatting.YELLOW)
                        );
                    }
                }
                continue;
            }

            progress.resetTicksOutside();
            progress.incrementProgress();

            PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                    new CaptureUpdatePacket(playerId, point.getName(),
                            progress.getProgressPercent(), true));

            if (progress.isComplete()) {
                completeCapture(player, point, progress.getTeam());
                toRemove.add(playerId);
            }
        }

        for (UUID uuid : toRemove) {
            activeCaptures.remove(uuid);
        }
    }

    private static void updatePassiveEffects() {
        if (currentLevel == null) return;

        for (CapturePoint point : capturePoints.values()) {
            String owner = point.getOwnerTeam();
            if (owner == null) continue;

            if (point.getBuffEffect() != null && !point.getBuffEffect().isEmpty()) {
                if (currentLevel.getGameTime() % 40 == 0) {
                    MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(point.getBuffEffect()));
                    if (effect != null) {
                        List<ServerPlayer> players = getTeamPlayersInRadius(owner, point);
                        for (ServerPlayer p : players) {
                            p.addEffect(new MobEffectInstance(effect, 80, point.getBuffAmplifier(), true, false));
                        }
                    }
                }
            }

            if (point.getPassiveIncomeAmount() > 0) {
                point.incrementIncomeTimer();

                int intervalTicks = point.getIncomeIntervalSeconds() * 20;
                if (intervalTicks <= 0) intervalTicks = 6000;

                if (point.getIncomeTimer() >= intervalTicks) {
                    point.resetIncomeTimer();
                    distributeIncome(owner, point.getIncomeItem(), point.getPassiveIncomeAmount(), point.getName());
                }
            }
        }
    }

    private static void distributeIncome(String teamName, ItemStack itemTemplate, int amount, String pointName) {
        if (currentLevel == null || teamName == null) return;

        var playerList = currentLevel.getServer().getPlayerList();
        var scoreboard = currentLevel.getScoreboard();

        ItemStack incomeStack;
        if (itemTemplate == null || itemTemplate.isEmpty()) {
            incomeStack = new ItemStack(Items.EMERALD, amount);
        } else {
            incomeStack = itemTemplate.copy();
            incomeStack.setCount(amount);
        }

        for (ServerPlayer player : playerList.getPlayers()) {
            var team = scoreboard.getPlayersTeam(player.getScoreboardName());
            if (team != null && team.getName().equals(teamName)) {
                ItemStack toGive = incomeStack.copy();
                if (!player.getInventory().add(toGive)) {
                    player.drop(toGive, false);
                }
                player.sendSystemMessage(
                        Component.empty()
                                .append(Component.translatable("capturepoints.income.prefix").withStyle(ChatFormatting.GREEN))
                                .append(Component.translatable("capturepoints.income.received",
                                        Component.literal(pointName).withStyle(ChatFormatting.YELLOW),
                                        Component.literal(amount + "x " + incomeStack.getHoverName().getString()).withStyle(ChatFormatting.GOLD)
                                ).withStyle(ChatFormatting.WHITE))
                );
            }
        }
    }

    private static void updateHolograms() {
        if (currentLevel == null) return;

        for (CapturePoint point : capturePoints.values()) {
            if (point.getPassiveIncomeAmount() <= 0 && point.getBuffEffect() == null) continue;

            List<ArmorStand> stands = currentLevel.getEntitiesOfClass(ArmorStand.class,
                    new AABB(point.getPosition()).inflate(1),
                    e -> e.hasCustomName() && (e.getCustomName().getString().contains("Бонус:") || e.getCustomName().getString().contains("Bonus:")));

            ArmorStand hologram;
            if (stands.isEmpty()) {
                hologram = EntityType.ARMOR_STAND.create(currentLevel);
                if (hologram != null) {
                    hologram.setPos(point.getPosition().getX() + 0.5,
                            point.getPosition().getY() + 2.5,
                            point.getPosition().getZ() + 0.5);
                    hologram.setInvisible(true);
                    hologram.setNoGravity(true);
                    hologram.setCustomNameVisible(true);

                    CompoundTag nbt = new CompoundTag();
                    hologram.saveWithoutId(nbt);
                    nbt.putBoolean("Marker", true);
                    hologram.load(nbt);

                    currentLevel.addFreshEntity(hologram);
                }
            } else {
                hologram = stands.get(0);
            }

            if (hologram != null) {
                MutableComponent hologramText = Component.translatable("capturepoints.hologram.bonus").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

                if (point.getPassiveIncomeAmount() > 0) {
                    String itemName = "монет";
                    if (point.getIncomeItem() != null && !point.getIncomeItem().isEmpty()) {
                        itemName = point.getIncomeItem().getHoverName().getString();
                    }
                    int mins = Math.max(1, point.getIncomeIntervalSeconds() / 60);
                    hologramText = hologramText.append(
                            Component.translatable("capturepoints.hologram.income", point.getPassiveIncomeAmount(), itemName, mins).withStyle(ChatFormatting.YELLOW)
                    );
                }

                if (point.getBuffEffect() != null) {
                    String effectName = point.getBuffEffect().replace("minecraft:", "");
                    if (!effectName.isEmpty()) {
                        String capitalized = effectName.substring(0, 1).toUpperCase() + effectName.substring(1);
                        hologramText = hologramText.append(
                                Component.literal(capitalized).withStyle(ChatFormatting.LIGHT_PURPLE)
                        );
                    }
                }
                hologram.setCustomName(hologramText);
            }
        }
    }

    private static List<ServerPlayer> getTeamPlayersInRadius(String teamName, CapturePoint point) {
        List<ServerPlayer> result = new ArrayList<>();
        if (currentLevel == null || teamName == null) return result;

        BlockPos center = point.getPosition();
        double radiusSq = Math.pow(point.getRadius() + 0.5, 2);

        for (ServerPlayer p : currentLevel.getServer().getPlayerList().getPlayers()) {
            if (p.level() != currentLevel) continue;

            var team = currentLevel.getScoreboard().getPlayersTeam(p.getScoreboardName());
            if (team != null && team.getName().equals(teamName)) {
                double dx = p.getX() - (center.getX() + 0.5);
                double dz = p.getZ() - (center.getZ() + 0.5);
                double distSq = dx * dx + dz * dz;

                double playerY = p.getY();
                double minY = center.getY() - point.getHeightDown();
                double maxY = center.getY() + point.getHeightUp();
                boolean inHeightRange = (playerY >= minY && playerY <= maxY);

                if (distSq <= radiusSq && inHeightRange) {
                    result.add(p);
                }
            }
        }
        return result;
    }

    public static void startCapture(ServerPlayer player, CapturePoint point) {
        if (point instanceof FinalCapturePoint) {
            if (!isFinalPointAvailable()) {
                player.sendSystemMessage(Component.translatable("capturepoints.capture.final_unavailable").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                return;
            }
        }

        if (point.isOnCooldown()) {
            player.sendSystemMessage(
                    Component.translatable("capturepoints.capture.on_cooldown",
                            Component.literal(point.getName()).withStyle(ChatFormatting.YELLOW),
                            point.getRemainingCooldown()
                    ).withStyle(ChatFormatting.RED)
            );
            return;
        }

        String playerTeam = getPlayerTeam(player);
        if (playerTeam == null) {
            player.sendSystemMessage(Component.translatable("capturepoints.capture.must_be_in_team").withStyle(ChatFormatting.RED));
            return;
        }

        if (point.getOwnerTeam() != null && point.getOwnerTeam().equals(playerTeam)) {
            player.sendSystemMessage(Component.translatable("capturepoints.capture.already_owned").withStyle(ChatFormatting.YELLOW));
            return;
        }

        if (activeCaptures.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("capturepoints.capture.already_capturing").withStyle(ChatFormatting.RED));
            return;
        }

        CaptureProgress progress = new CaptureProgress(player, point, playerTeam);
        activeCaptures.put(player.getUUID(), progress);

        ChatFormatting teamColor = getTeamChatFormatting(playerTeam);
        boolean isFinal = point instanceof FinalCapturePoint;
        if (isFinal) {
            player.getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("capturepoints.capture.final_started",
                            Component.literal(playerTeam).withStyle(teamColor),
                            Component.literal(point.getName()).withStyle(ChatFormatting.YELLOW)
                    ).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    false
            );
        } else {
            player.getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("capturepoints.capture.started",
                            Component.literal(playerTeam).withStyle(teamColor),
                            Component.literal(point.getName()).withStyle(ChatFormatting.YELLOW)
                    ).withStyle(ChatFormatting.GOLD),
                    false
            );
        }

        player.addEffect(new MobEffectInstance(MobEffects.GLOWING,
                point.getCaptureTime() + 100, 0, false, false));
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                new CaptureUpdatePacket(player.getUUID(), point.getName(), 0.0f, true));
    }

    private static void completeCapture(ServerPlayer player, CapturePoint point, String team) {
        point.setOwnerTeam(team);
        player.removeEffect(MobEffects.GLOWING);

        ItemStack reward = point.getReward().copy();
        reward.setCount(point.getRewardAmount());
        player.addItem(reward);

        boolean isFinalPoint = point instanceof FinalCapturePoint;
        ChatFormatting teamColor = getTeamChatFormatting(team);
        if (isFinalPoint) {
            for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                CommandSourceStack source = player.getServer().createCommandSourceStack()
                        .withPermission(4)
                        .withSuppressedOutput();
                player.getServer().getCommands().performPrefixedCommand(source,
                        "title " + p.getName().getString() + " title {\"text\":\"ПОБЕДА!\",\"color\":\"gold\",\"bold\":true}");
                player.getServer().getCommands().performPrefixedCommand(source,
                        "title " + p.getName().getString() + " subtitle {\"text\":\"Команда " + team + " победила!\",\"color\":\"yellow\"}");
            }

            player.getServer().getPlayerList().broadcastSystemMessage(
                    Component.empty()
                            .append(Component.translatable("capturepoints.capture.game_over.line1").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD, ChatFormatting.UNDERLINE))
                            .append(Component.literal("\n"))
                            .append(Component.translatable("capturepoints.capture.game_over.line2",
                                    Component.literal(team).withStyle(teamColor, ChatFormatting.BOLD)
                            ).withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal("\n"))
                            .append(Component.translatable("capturepoints.capture.game_over.line3").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD, ChatFormatting.UNDERLINE)),
                    false
            );

            FinalCapturePoint finalPoint = (FinalCapturePoint) point;
            activateCommandBlocks(player, finalPoint, team);
        } else {
            player.getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("capturepoints.capture.completed",
                            Component.literal(player.getName().getString()).withStyle(ChatFormatting.AQUA),
                            Component.literal(point.getName()).withStyle(ChatFormatting.YELLOW),
                            Component.literal(team).withStyle(teamColor)
                    ).withStyle(ChatFormatting.GOLD),
                    false
            );
        }

        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                new CaptureCompletePacket(point.getName(), team));
        save(currentLevel);

        if (isFinalPoint) {
            com.persiki84.battlecraft.BattleCraftManager.getInstance().stopMatch(player.getServer(), team);
        }
    }

    private static void activateCommandBlocks(ServerPlayer player, FinalCapturePoint point, String team) {
        ServerLevel level = player.serverLevel();
        for (BlockPos cmdPos : point.getCommandBlockPositions()) {
            BlockPos redstonePos = cmdPos.above();
            level.setBlock(redstonePos, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
            CapturePointsMod.LOGGER.info("Activated command block at {} for team {}", cmdPos, team);
            level.getServer().tell(new net.minecraft.server.TickTask(
                    level.getServer().getTickCount() + 5,
                    () -> {
                        if (level.getBlockState(redstonePos).is(Blocks.REDSTONE_BLOCK)) {
                            level.setBlock(redstonePos, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
            ));
        }
    }

    public static void cancelCapture(UUID playerId) {
        CaptureProgress progress = activeCaptures.remove(playerId);
        if (progress != null) {
            ServerPlayer player = progress.getPlayer();
            if (player != null) {
                player.removeEffect(MobEffects.GLOWING);
                PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                        new CaptureUpdatePacket(playerId, progress.getPoint().getName(), 0.0f, false));
            }
        }
    }

    public static void cancelCaptureForPoint(String pointName) {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, CaptureProgress> entry : activeCaptures.entrySet()) {
            if (entry.getValue().getPoint().getName().equals(pointName)) {
                toRemove.add(entry.getKey());
            }
        }
        for (UUID uuid : toRemove) {
            cancelCapture(uuid);
        }
    }

    public static ChatFormatting getTeamChatFormatting(String teamName) {
        if (currentLevel != null && currentLevel.getServer() != null) {
            var scoreboard = currentLevel.getServer().getScoreboard();
            var team = scoreboard.getPlayerTeam(teamName);
            if (team != null) {
                return team.getColor();
            }
        }
        return ChatFormatting.WHITE;
    }

    private static String getPlayerTeam(ServerPlayer player) {
        var scoreboard = player.getServer().getScoreboard();
        var team = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (team != null) {
            return team.getName();
        }
        return null;
    }

    public static int getTeamPoints(String teamName) {
        return (int) capturePoints.values().stream()
                .filter(p -> teamName.equals(p.getOwnerTeam()))
                .count();
    }

    public static String getTeamWithAllPoints() {
        if (capturePoints.isEmpty()) {
            return null;
        }

        String firstTeam = null;
        for (CapturePoint point : capturePoints.values()) {
            String owner = point.getOwnerTeam();
            if (owner == null) {
                return null;
            }
            if (firstTeam == null) {
                firstTeam = owner;
            } else if (!firstTeam.equals(owner)) {
                return null;
            }
        }
        return firstTeam;
    }

    public static boolean isFinalPointAvailable() {
        return getTeamWithAllPoints() != null;
    }

    public static void resetAllPoints() {
        for (CapturePoint point : capturePoints.values()) {
            point.setOwnerTeam(null);
            point.resetCooldown();
            point.resetIncomeTimer();
        }

        for (FinalCapturePoint point : finalPoints.values()) {
            point.setOwnerTeam(null);
            point.resetCooldown();
        }

        save(currentLevel);
        syncPoints();
        syncFinalPoints();
    }

    public static void resetAllFinalPoints() {
        for (FinalCapturePoint point : finalPoints.values()) {
            point.setOwnerTeam(null);
            point.resetCooldown();
        }

        save(currentLevel);
        syncFinalPoints();
    }

    public static void syncPoints() {
        if (currentLevel == null) return;
        Map<String, PointSyncData> pointOwners = new HashMap<>();
        for (CapturePoint p : capturePoints.values()) {
            pointOwners.put(p.getName(), new PointSyncData(p.getOwnerTeam(), p.getPosition()));
        }

        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                new CapturePointSyncPacket(pointOwners));
    }

    public static void syncFinalPoints() {
        if (currentLevel == null) return;
        Map<String, PointSyncData> finalPointOwners = new HashMap<>();
        for (FinalCapturePoint p : finalPoints.values()) {
            finalPointOwners.put(p.getName(), new PointSyncData(p.getOwnerTeam(), p.getPosition()));
        }

        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                new FinalPointSyncPacket(finalPointOwners));
    }

    private static void removeHologramAt(BlockPos pos) {
        if (currentLevel == null) return;
        List<ArmorStand> stands = currentLevel.getEntitiesOfClass(ArmorStand.class,
                new AABB(pos).inflate(1),
                e -> e.hasCustomName() && (e.getCustomName().getString().contains("\u0411\u043E\u043D\u0443\u0441:") || e.getCustomName().getString().contains("Bonus:")));
        for (ArmorStand stand : stands) {
            stand.discard();
        }
    }

    public static void cleanupAllHolograms() {
        if (currentLevel == null) return;
        for (CapturePoint point : capturePoints.values()) {
            removeHologramAt(point.getPosition());
        }
        for (FinalCapturePoint point : finalPoints.values()) {
            removeHologramAt(point.getPosition());
        }
    }

    private static boolean globalCaptureMarkers = true;
    private static boolean globalFinalMarkers = true;

    public static boolean isGlobalCaptureMarkers() { return globalCaptureMarkers; }
    public static void setGlobalCaptureMarkers(boolean b) { globalCaptureMarkers = b; save(currentLevel); syncGlobalMarkers(); }

    public static boolean isGlobalFinalMarkers() { return globalFinalMarkers; }
    public static void setGlobalFinalMarkers(boolean b) { globalFinalMarkers = b; save(currentLevel); syncGlobalMarkers(); }

    public static void syncGlobalMarkers() {
        PacketHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(),
                new com.persiki84.capturepoints.network.GlobalMarkerSyncPacket(globalCaptureMarkers, globalFinalMarkers));
    }

    public static void save(ServerLevel level) {
        if (level == null && currentLevel == null) return;
        if (level != null) currentLevel = level;

        try {
            File dataFolder = new File(currentLevel.getServer().getWorldPath(LevelResource.ROOT).toFile(), "capturepoints");
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File dataFile = new File(dataFolder, "points.dat");
            CompoundTag mainTag = new CompoundTag();
            mainTag.putBoolean("globalCaptureMarkers", globalCaptureMarkers);
            mainTag.putBoolean("globalFinalMarkers", globalFinalMarkers);

            ListTag pointsList = new ListTag();
            ListTag finalPointsList = new ListTag();

            for (CapturePoint point : capturePoints.values()) {
                pointsList.add(point.save());
            }

            for (FinalCapturePoint point : finalPoints.values()) {
                finalPointsList.add(point.save());
            }

            mainTag.put("points", pointsList);
            mainTag.put("finalPoints", finalPointsList);

            try (FileOutputStream fos = new FileOutputStream(dataFile)) {
                net.minecraft.nbt.NbtIo.writeCompressed(mainTag, fos);
            }

            CapturePointsMod.LOGGER.info("Saved {} capture points and {} final points",
                    capturePoints.size(), finalPoints.size());
        } catch (Exception e) {
            CapturePointsMod.LOGGER.error("Failed to save capture points", e);
        }
    }

    public static void load(ServerLevel level) {
        if (level == null) return;
        currentLevel = level;
        capturePoints.clear();
        finalPoints.clear();

        try {
            File dataFolder = new File(level.getServer().getWorldPath(LevelResource.ROOT).toFile(), "capturepoints");
            File dataFile = new File(dataFolder, "points.dat");
            if (!dataFile.exists()) {
                CapturePointsMod.LOGGER.info("No capture points data found, starting fresh");
                return;
            }

            try (FileInputStream fis = new FileInputStream(dataFile)) {
                CompoundTag mainTag = net.minecraft.nbt.NbtIo.readCompressed(fis);
                globalCaptureMarkers = !mainTag.contains("globalCaptureMarkers") || mainTag.getBoolean("globalCaptureMarkers");
                globalFinalMarkers = !mainTag.contains("globalFinalMarkers") || mainTag.getBoolean("globalFinalMarkers");

                ListTag pointsList = mainTag.getList("points", Tag.TAG_COMPOUND);
                ListTag finalPointsList = mainTag.getList("finalPoints", Tag.TAG_COMPOUND);

                for (int i = 0; i < pointsList.size(); i++) {
                    CompoundTag pointTag = pointsList.getCompound(i);
                    CapturePoint point = CapturePoint.load(pointTag);
                    capturePoints.put(point.getName(), point);
                }

                for (int i = 0; i < finalPointsList.size(); i++) {
                    CompoundTag pointTag = finalPointsList.getCompound(i);
                    FinalCapturePoint point = FinalCapturePoint.loadFinal(pointTag);
                    finalPoints.put(point.getName(), point);
                }

                CapturePointsMod.LOGGER.info("Loaded {} capture points and {} final points",
                        capturePoints.size(), finalPoints.size());
            }
        } catch (Exception e) {
            CapturePointsMod.LOGGER.error("Failed to load capture points", e);
        }
    }
}
