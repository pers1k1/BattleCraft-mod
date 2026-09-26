package com.persiki84.capturepoints.capture;

import com.persiki84.capturepoints.CapturePointsMod;
import com.persiki84.capturepoints.event.BlockProtectionHandler;
import com.persiki84.capturepoints.network.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CapturePointManager {

    private static final double CAPTURE_EDGE_TOLERANCE = 0.5;
    private static final int SECOND_TICKS = 20;
    private static final int BUFF_REFRESH_TICKS = 40;
    private static final int BUFF_DURATION_TICKS = 80;
    private static final int DEFAULT_INCOME_TICKS = 6000;
    private static final int HOLOGRAM_REFRESH_TICKS = 100;
    private static final double HOLOGRAM_LIFT = 2.5;
    private static final String HOLOGRAM_TAG = "capturepoints_bonus";
    private static final String LEGACY_HOLOGRAM_RU = "Бонус:";
    private static final String LEGACY_HOLOGRAM_EN = "Bonus:";

    private static final Map<String, CapturePoint> capturePoints = new ConcurrentHashMap<>();
    private static final Map<String, FinalCapturePoint> finalPoints = new ConcurrentHashMap<>();

    private static MinecraftServer currentServer;
    private static final String DATA_FOLDER = "capturepoints";
    private static final String DATA_FILE = "points.dat";
    private static final String BROKEN_SUFFIX = ".broken-";
    private static final DateTimeFormatter BROKEN_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static long ticks;
    private static boolean loadFailed;

    public static void addCapturePoint(CapturePoint point) {
        capturePoints.put(point.getName(), point);
        persist();
        syncPoints();
    }

    public static void addFinalPoint(FinalCapturePoint point) {
        finalPoints.put(point.getName(), point);
        persist();
        syncFinalPoints();
    }

    public static void removeCapturePoint(String name) {
        CapturePoint point = capturePoints.get(name);
        if (point == null) return;

        removeHologramAt(point);
        CaptureSessions.cancelForPoint(name);
        TeamCooldowns.forgetPoint(name);
        capturePoints.remove(name);
        persist();

        if (currentServer != null) {
            PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new CapturePointRemovePacket(name));
        }
    }

    public static void removeFinalPoint(String name) {
        FinalCapturePoint point = finalPoints.get(name);
        if (point == null) return;

        removeHologramAt(point);
        CaptureSessions.cancelForPoint(name);
        TeamCooldowns.forgetPoint(name);
        finalPoints.remove(name);
        persist();

        if (currentServer != null) {
            PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new FinalPointRemovePacket(name));
        }
    }

    public static CapturePoint getCapturePoint(String name) { return capturePoints.get(name); }
    public static FinalCapturePoint getFinalPoint(String name) { return finalPoints.get(name); }
    public static Collection<CapturePoint> getAllPoints() { return capturePoints.values(); }
    public static Collection<FinalCapturePoint> getAllFinalPoints() { return finalPoints.values(); }
    public static MinecraftServer server() { return currentServer; }

    public static void tick() {
        if (currentServer == null) return;

        ticks++;
        CaptureSessions.tick(currentServer);
        updatePassiveEffects();

        if (ticks % HOLOGRAM_REFRESH_TICKS == 0) {
            updateHolograms();
        }
    }

    // WHY: вне матча захватов нет, и точка, оставшаяся за командой с прошлого раза, не должна
    // WHY: капать доходом и баффом в лобби
    private static void updatePassiveEffects() {
        if (currentServer == null || !MatchState.running()) return;

        for (CapturePoint point : capturePoints.values()) {
            String owner = point.getOwnerTeam();
            if (owner == null) continue;

            applyBuff(point, owner);
            applyIncome(point, owner);
        }
    }

    private static void applyBuff(CapturePoint point, String owner) {
        if (point.getBuffEffect() == null || point.getBuffEffect().isEmpty()) return;
        if (ticks % BUFF_REFRESH_TICKS != 0) return;

        ResourceLocation id = ResourceLocation.tryParse(point.getBuffEffect());
        MobEffect effect = id == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(id);
        if (effect == null) return;

        for (ServerPlayer player : getTeamPlayersInside(owner, point)) {
            player.addEffect(new MobEffectInstance(effect, BUFF_DURATION_TICKS, point.getBuffAmplifier(), true, false));
        }
    }

    private static void applyIncome(CapturePoint point, String owner) {
        if (point.getPassiveIncomeAmount() <= 0) return;

        point.incrementIncomeTimer();
        int intervalTicks = point.getIncomeIntervalSeconds() * SECOND_TICKS;
        if (intervalTicks <= 0) intervalTicks = DEFAULT_INCOME_TICKS;

        if (point.getIncomeTimer() >= intervalTicks) {
            point.resetIncomeTimer();
            distributeIncome(owner, point.getIncomeItem(), point.getPassiveIncomeAmount(), point.getName());
        }
    }

    private static void distributeIncome(String teamName, ItemStack itemTemplate, int amount, String pointName) {
        if (currentServer == null || teamName == null) return;

        ItemStack incomeStack;
        if (itemTemplate == null || itemTemplate.isEmpty()) {
            incomeStack = new ItemStack(Items.EMERALD, amount);
        } else {
            incomeStack = itemTemplate.copy();
            incomeStack.setCount(amount);
        }

        var scoreboard = currentServer.getScoreboard();
        for (ServerPlayer player : currentServer.getPlayerList().getPlayers()) {
            var team = scoreboard.getPlayersTeam(player.getScoreboardName());
            if (team != null && team.getName().equals(teamName)) {
                payIncome(player, incomeStack, amount, pointName);
            }
        }
    }

    private static void payIncome(ServerPlayer player, ItemStack incomeStack, int amount, String pointName) {
        ItemStack toGive = incomeStack.copy();
        if (!player.getInventory().add(toGive)) {
            player.drop(toGive, false);
        }

        player.sendSystemMessage(Component.empty()
                .append(Component.translatable("capturepoints.income.prefix").withStyle(ChatFormatting.GREEN))
                .append(Component.translatable("capturepoints.income.received",
                        Component.literal(pointName).withStyle(ChatFormatting.YELLOW),
                        Component.literal(amount + "x " + incomeStack.getHoverName().getString())
                                .withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.WHITE)));
    }

    private static void updateHolograms() {
        if (currentServer == null) return;

        for (CapturePoint point : capturePoints.values()) {
            ServerLevel level = levelOf(point);
            if (level == null || !entitiesLoadedAt(level, point.getPosition())) continue;

            if (!hasBonus(point)) {
                removeHologramAt(point);
                continue;
            }
            ArmorStand hologram = keepSingleHologram(level, point);
            if (hologram == null) hologram = spawnHologram(level, point);
            if (hologram != null) hologram.setCustomName(hologramText(point));
        }
    }

    private static boolean hasBonus(CapturePoint point) {
        return point.getPassiveIncomeAmount() > 0 || point.getBuffEffect() != null;
    }

    // WHY: стойку в незагруженном чанке не найти, и рядом ставилась бы вторая, пока первая ждёт
    // WHY: загрузки сущностей
    private static boolean entitiesLoadedAt(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.areEntitiesLoaded(ChunkPos.asLong(pos));
    }

    public static ServerLevel levelOf(CapturePoint point) {
        return currentServer == null ? null : currentServer.getLevel(point.getDimension());
    }

    private static ArmorStand keepSingleHologram(ServerLevel level, CapturePoint point) {
        List<ArmorStand> stands = hologramsAt(level, point);
        if (stands.isEmpty()) return null;

        ArmorStand kept = stands.get(0);
        kept.addTag(HOLOGRAM_TAG);
        for (int index = 1; index < stands.size(); index++) {
            stands.get(index).discard();
        }
        return kept;
    }

    private static List<ArmorStand> hologramsAt(ServerLevel level, CapturePoint point) {
        return level.getEntitiesOfClass(ArmorStand.class, hologramBounds(point), CapturePointManager::isHologram);
    }

    // WHY: стойка висит на HOLOGRAM_LIFT над центром, а рамка в блок вокруг центра её не
    // WHY: доставала: поиск промахивался, и каждые 100 тиков вставала новая стойка навечно
    private static AABB hologramBounds(CapturePoint point) {
        return new AABB(point.getPosition()).inflate(1).expandTowards(0, HOLOGRAM_LIFT, 0);
    }

    private static boolean isHologram(ArmorStand stand) {
        if (stand.getTags().contains(HOLOGRAM_TAG)) return true;
        if (!stand.hasCustomName()) return false;

        String name = stand.getCustomName().getString();
        return name.contains(LEGACY_HOLOGRAM_RU) || name.contains(LEGACY_HOLOGRAM_EN);
    }

    private static ArmorStand spawnHologram(ServerLevel level, CapturePoint point) {
        ArmorStand hologram = EntityType.ARMOR_STAND.create(level);
        if (hologram == null) return null;

        hologram.setPos(point.getPosition().getX() + 0.5,
                point.getPosition().getY() + HOLOGRAM_LIFT,
                point.getPosition().getZ() + 0.5);
        hologram.setInvisible(true);
        hologram.setNoGravity(true);
        hologram.setCustomNameVisible(true);
        hologram.addTag(HOLOGRAM_TAG);

        CompoundTag nbt = new CompoundTag();
        hologram.saveWithoutId(nbt);
        nbt.putBoolean("Marker", true);
        hologram.load(nbt);

        level.addFreshEntity(hologram);
        return hologram;
    }

    private static MutableComponent hologramText(CapturePoint point) {
        MutableComponent text = Component.translatable("capturepoints.hologram.bonus")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

        if (point.getPassiveIncomeAmount() > 0) {
            Component itemName = point.getIncomeItem() == null || point.getIncomeItem().isEmpty()
                    ? Component.translatable("capturepoints.hologram.currency")
                    : point.getIncomeItem().getHoverName();
            int mins = Math.max(1, point.getIncomeIntervalSeconds() / 60);
            text = text.append(Component.translatable("capturepoints.hologram.income",
                    point.getPassiveIncomeAmount(), itemName, mins).withStyle(ChatFormatting.YELLOW));
        }

        String effect = point.getBuffEffect();
        if (effect != null && !effect.replace("minecraft:", "").isEmpty()) {
            text = text.append(Component.literal(capitalized(effect.replace("minecraft:", "")))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return text;
    }

    private static String capitalized(String value) {
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    private static List<ServerPlayer> getTeamPlayersInside(String teamName, CapturePoint point) {
        List<ServerPlayer> result = new ArrayList<>();
        ServerLevel level = levelOf(point);
        if (level == null || teamName == null) return result;

        for (ServerPlayer p : level.players()) {
            var team = currentServer.getScoreboard().getPlayersTeam(p.getScoreboardName());
            if (team == null || !team.getName().equals(teamName)) continue;

            if (point.getArea().contains(p.getX(), p.getY(), p.getZ(), CAPTURE_EDGE_TOLERANCE)) {
                result.add(p);
            }
        }
        return result;
    }

    public static void startCapture(ServerPlayer player, CapturePoint point) {
        CaptureSessions.startByKey(player, point);
    }

    public static void completeCapture(CaptureSession session, String team, MinecraftServer server) {
        CapturePoint point = session.getPoint();
        point.setOwnerTeam(team);
        CaptureRewards.pay(server, session, team);
        CaptureCommandRunner.onCaptured(server, point, team);

        boolean isFinalPoint = point instanceof FinalCapturePoint;
        if (isFinalPoint) {
            PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new MatchVictoryPacket(team));
            activateCommandBlocks((FinalCapturePoint) point, team);
        } else {
            announceCompleted(server, point, team);
        }

        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                new CaptureCompletePacket(point.getName(), team));
        persist();

        if (isFinalPoint) {
            com.persiki84.battlecraft.BattleCraftManager.getInstance().stopMatch(server, team);
        }
    }

    private static void announceCompleted(MinecraftServer server, CapturePoint point, String team) {
        if (server == null) return;

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("capturepoints.capture.completed_team",
                        Component.literal(team).withStyle(getTeamChatFormatting(team)),
                        Component.literal(point.getName()).withStyle(ChatFormatting.YELLOW)
                ).withStyle(ChatFormatting.GOLD), false);
    }

    private static void activateCommandBlocks(FinalCapturePoint point, String team) {
        ServerLevel level = levelOf(point);
        if (level == null) return;

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

    public static void cancelCaptureForPoint(String pointName) {
        CaptureSessions.cancelForPoint(pointName);
    }

    public static ChatFormatting getTeamChatFormatting(String teamName) {
        if (currentServer != null) {
            var scoreboard = currentServer.getScoreboard();
            var team = scoreboard.getPlayerTeam(teamName);
            if (team != null) {
                return team.getColor();
            }
        }
        return ChatFormatting.WHITE;
    }

    public static String teamOf(ServerPlayer player) {
        if (player == null) return null;

        var team = player.getServer().getScoreboard().getPlayersTeam(player.getScoreboardName());
        return team == null ? null : team.getName();
    }

    // WHY: финальную открывают только обязательные точки: необязательная берётся ради дохода
    // WHY: и бонусов, и ждать её захвата команде незачем
    public static String getTeamWithAllPoints() {
        String firstTeam = null;
        for (CapturePoint point : capturePoints.values()) {
            if (!point.isRequired()) continue;

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

    public static boolean hasRequiredPoints() {
        for (CapturePoint point : capturePoints.values()) {
            if (point.isRequired()) return true;
        }
        return false;
    }

    // WHY: условий нет - значит выполнять нечего, и финальная открыта с начала матча; иначе карта
    // WHY: из одних необязательных точек кончалась бы матчем, который невозможно выиграть
    public static boolean isFinalPointAvailable() {
        return !hasRequiredPoints() || getTeamWithAllPoints() != null;
    }

    // WHY: без этого правила команда, собравшая все обязательные точки, открывала финальную
    // WHY: сопернику: тот приходил на готовое и забирал матч, не взяв ни одной точки
    public static boolean mayTakeFinal(String team) {
        String opener = finalOpener();
        return opener == null || opener.equals(team);
    }

    // WHY: пусто значит «ограничения нет»: либо выключатель снят, либо обязательных точек нет
    public static String finalOpener() {
        return finalForOpenerOnly ? getTeamWithAllPoints() : null;
    }

    public static boolean isFinalForOpenerOnly() { return finalForOpenerOnly; }

    public static void setFinalForOpenerOnly(boolean value) {
        finalForOpenerOnly = value;
        persist();
    }

    public static void resetAllPoints() {
        CaptureSessions.cancelAll();
        TeamCooldowns.clear();

        for (CapturePoint point : capturePoints.values()) {
            point.setOwnerTeam(null);
            point.resetCooldown();
            point.resetIncomeTimer();
        }

        for (FinalCapturePoint point : finalPoints.values()) {
            point.setOwnerTeam(null);
            point.resetCooldown();
        }

        flush();
        syncPoints();
        syncFinalPoints();
    }

    public static void resetAllFinalPoints() {
        for (FinalCapturePoint point : finalPoints.values()) {
            point.setOwnerTeam(null);
            point.resetCooldown();
        }

        persist();
        syncFinalPoints();
    }

    public static void syncPoints() {
        if (currentServer == null) return;
        Map<String, PointSyncData> pointOwners = new HashMap<>();
        for (CapturePoint p : capturePoints.values()) {
            pointOwners.put(p.getName(), PointSyncData.of(p));
        }

        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                new CapturePointSyncPacket(pointOwners));
    }

    public static void syncFinalPoints() {
        if (currentServer == null) return;
        Map<String, PointSyncData> finalPointOwners = new HashMap<>();
        for (FinalCapturePoint p : finalPoints.values()) {
            finalPointOwners.put(p.getName(), PointSyncData.of(p));
        }

        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                new FinalPointSyncPacket(finalPointOwners));
    }

    private static void removeHologramAt(CapturePoint point) {
        ServerLevel level = levelOf(point);
        if (level == null) return;

        for (ArmorStand stand : hologramsAt(level, point)) {
            stand.discard();
        }
    }

    public static void cleanupAllHolograms() {
        if (currentServer == null) return;
        for (CapturePoint point : capturePoints.values()) {
            removeHologramAt(point);
        }
        for (FinalCapturePoint point : finalPoints.values()) {
            removeHologramAt(point);
        }
    }

    private static boolean finalForOpenerOnly;
    private static boolean globalCaptureMarkers = true;
    private static boolean globalFinalMarkers = true;

    public static boolean isGlobalCaptureMarkers() { return globalCaptureMarkers; }
    public static void setGlobalCaptureMarkers(boolean b) { globalCaptureMarkers = b; persist(); syncGlobalMarkers(); }

    public static boolean isGlobalFinalMarkers() { return globalFinalMarkers; }
    public static void setGlobalFinalMarkers(boolean b) { globalFinalMarkers = b; persist(); syncGlobalMarkers(); }

    public static void syncGlobalMarkers() {
        PacketHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(),
                new com.persiki84.capturepoints.network.GlobalMarkerSyncPacket(globalCaptureMarkers, globalFinalMarkers));
    }

    // WHY: после проваленного чтения в памяти пусто, и запись при остановке или сбросе матча
    // WHY: затёрла бы файл, если его не удалось отодвинуть; снимает запрет только persist()
    public static void flush() {
        if (!loadFailed) persist();
    }

    public static void persist() {
        if (currentServer == null) return;
        loadFailed = false;

        java.nio.file.Path folder = currentServer.getWorldPath(LevelResource.ROOT).resolve(DATA_FOLDER);
        java.nio.file.Path target = folder.resolve(DATA_FILE);
        java.nio.file.Path temporary = folder.resolve(DATA_FILE + ".tmp");

        try {
            java.nio.file.Files.createDirectories(folder);
            try (FileOutputStream stream = new FileOutputStream(temporary.toFile())) {
                net.minecraft.nbt.NbtIo.writeCompressed(snapshot(), stream);
            }
            com.persiki84.shared.WorldFiles.moveIntoPlace(temporary, target);
        } catch (Exception e) {
            CapturePointsMod.LOGGER.error("Failed to save capture points", e);
        }
    }

    private static CompoundTag snapshot() {
        CompoundTag mainTag = new CompoundTag();
        mainTag.putBoolean("finalForOpenerOnly", finalForOpenerOnly);
        mainTag.putBoolean("globalCaptureMarkers", globalCaptureMarkers);
        mainTag.putBoolean("globalFinalMarkers", globalFinalMarkers);
        mainTag.putBoolean("blockProtection", BlockProtectionHandler.isProtectionEnabled());
        mainTag.putBoolean("playerPlacedBreakable", BlockProtectionHandler.isPlayerPlacedBreakable());
        mainTag.putBoolean("placementDenied", BlockProtectionHandler.isPlacementDenied());
        com.persiki84.capturepoints.event.PlacedBlocks.save(mainTag);

        ListTag pointsList = new ListTag();
        for (CapturePoint point : capturePoints.values()) {
            pointsList.add(point.save());
        }

        ListTag finalPointsList = new ListTag();
        for (FinalCapturePoint point : finalPoints.values()) {
            finalPointsList.add(point.save());
        }

        mainTag.put("points", pointsList);
        mainTag.put("finalPoints", finalPointsList);
        TeamCooldowns.save(mainTag);
        CaptureRewards.save(mainTag);
        return mainTag;
    }

    public static void load(MinecraftServer server) {
        if (server == null) return;
        currentServer = server;
        CaptureSessions.cancelAll();
        capturePoints.clear();
        finalPoints.clear();
        loadFailed = false;

        Path dataFile = server.getWorldPath(LevelResource.ROOT).resolve(DATA_FOLDER).resolve(DATA_FILE);
        if (!Files.exists(dataFile)) {
            CapturePointsMod.LOGGER.info("No capture points data found, starting fresh");
            return;
        }
        try (InputStream stream = Files.newInputStream(dataFile)) {
            restore(net.minecraft.nbt.NbtIo.readCompressed(stream));
            CapturePointsMod.LOGGER.info("Loaded {} capture points and {} final points",
                    capturePoints.size(), finalPoints.size());
        } catch (Exception e) {
            loadFailed = true;
            CapturePointsMod.LOGGER.error("Failed to load capture points", e);
            setAside(dataFile);
        }
    }

    // WHY: нечитаемый файл уезжает в сторону, а не перезаписывается первым же сохранением:
    // WHY: карта точек стоит одного лишнего файла на диске
    private static void setAside(Path file) {
        Path spoiled = file.resolveSibling(file.getFileName() + BROKEN_SUFFIX + LocalDateTime.now().format(BROKEN_STAMP));
        try {
            Files.move(file, spoiled, StandardCopyOption.REPLACE_EXISTING);
            CapturePointsMod.LOGGER.warn("Unreadable {} moved to {}", file.getFileName(), spoiled.getFileName());
        } catch (IOException error) {
            CapturePointsMod.LOGGER.error("Cannot set aside {}", file, error);
        }
    }

    private static void restore(CompoundTag mainTag) {
        finalForOpenerOnly = mainTag.getBoolean("finalForOpenerOnly");
        globalCaptureMarkers = !mainTag.contains("globalCaptureMarkers") || mainTag.getBoolean("globalCaptureMarkers");
        globalFinalMarkers = !mainTag.contains("globalFinalMarkers") || mainTag.getBoolean("globalFinalMarkers");
        BlockProtectionHandler.restore(mainTag.getBoolean("blockProtection"),
                mainTag.getBoolean("playerPlacedBreakable"), mainTag.getBoolean("placementDenied"));
        com.persiki84.capturepoints.event.PlacedBlocks.load(mainTag);

        ListTag pointsList = mainTag.getList("points", Tag.TAG_COMPOUND);
        for (int i = 0; i < pointsList.size(); i++) {
            CapturePoint point = CapturePoint.load(pointsList.getCompound(i));
            capturePoints.put(point.getName(), point);
        }

        ListTag finalPointsList = mainTag.getList("finalPoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < finalPointsList.size(); i++) {
            FinalCapturePoint point = FinalCapturePoint.loadFinal(finalPointsList.getCompound(i));
            finalPoints.put(point.getName(), point);
        }

        TeamCooldowns.load(mainTag);
        CaptureRewards.load(mainTag);
    }
}
