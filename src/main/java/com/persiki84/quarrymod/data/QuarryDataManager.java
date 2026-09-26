package com.persiki84.quarrymod.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import com.persiki84.quarrymod.QuarryMod;
import com.persiki84.shared.WorldFiles;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class QuarryDataManager {
    private static final String FILE_NAME = "quarry_blocks.dat";
    private static final String BROKEN_SUFFIX = ".broken-";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int COMPOUND = 10;

    private final File dataFile;
    private final QuarryBlockManager blockManager;

    // WHY: файл лежал в папке установки сервера, поэтому все миры делили одни карьерные блоки:
    // WHY: в соседнем мире по тем же координатам оживали чужие неломаемые блоки
    public QuarryDataManager(MinecraftServer server) {
        this.dataFile = server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME).toFile();
        this.blockManager = new QuarryBlockManager();
    }

    public void saveIfDirty() {
        if (blockManager.consumeDirty()) save();
    }

    public void save() {
        CompoundTag compound = new CompoundTag();

        ListTag blockList = new ListTag();
        for (QuarryBlock block : blockManager.getAllQuarryBlocks().values()) {
            blockList.add(block.toNBT());
        }
        compound.put("quarryBlocks", blockList);
        compound.put("customCooldowns", writeTimes(blockManager.getCustomCooldowns(), "cooldown_ms"));
        compound.putLong("globalCooldownTime", blockManager.getGlobalCooldownTime());

        ListTag ruleList = new ListTag();
        for (QuarryBlockRule rule : blockManager.rules()) {
            ruleList.add(rule.toNBT());
        }
        compound.put("blockRules", ruleList);
        compound.put("pendingRegenerations", writeTimes(blockManager.getPendingRegenerations(), "target_time"));

        store(compound);
    }

    private static ListTag writeTimes(Map<QuarryBlockKey, Long> times, String field) {
        ListTag list = new ListTag();
        for (Map.Entry<QuarryBlockKey, Long> entry : times.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("x", entry.getKey().pos().getX());
            tag.putInt("y", entry.getKey().pos().getY());
            tag.putInt("z", entry.getKey().pos().getZ());
            tag.putString("dimension", entry.getKey().dimension());
            tag.putLong(field, entry.getValue());
            list.add(tag);
        }
        return list;
    }

    // WHY: запись шла прямо в целевой файл, и падение посреди неё оставляло обрубок вместо карьера
    private void store(CompoundTag compound) {
        Path target = dataFile.toPath();
        Path temporary = target.resolveSibling(FILE_NAME + ".tmp");
        try {
            NbtIo.writeCompressed(compound, temporary.toFile());
            WorldFiles.moveIntoPlace(temporary, target);
        } catch (IOException error) {
            QuarryMod.LOGGER.warn("[quarry] cannot write {}: {}", FILE_NAME, error.toString());
            deleteQuietly(temporary);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    public void load() {
        if (!dataFile.exists()) return;

        try {
            apply(NbtIo.readCompressed(dataFile));
        } catch (IOException | RuntimeException error) {
            QuarryMod.LOGGER.warn("[quarry] cannot read {}: {}", FILE_NAME, error.toString());
            quarantine();
        }
    }

    private void apply(CompoundTag compound) {
        long globalCooldown = compound.contains("globalCooldownTime") ? compound.getLong("globalCooldownTime") : 20000;
        blockManager.loadData(readBlocks(compound), readTimes(compound, "customCooldowns", "cooldown_ms"),
                readTimes(compound, "pendingRegenerations", "target_time"), globalCooldown);
        blockManager.loadRules(readRules(compound));
    }

    // WHY: после битого чтения карьер пуст, и первая же запись легла бы пустотой поверх всех блоков
    // WHY: и очереди регенерации: файл уезжает в сторону, чтобы его можно было восстановить руками,
    // WHY: а сохранение ждёт настоящей правки - флаг изменений после загрузки чист
    private void quarantine() {
        Path source = dataFile.toPath();
        Path spoiled = source.resolveSibling(FILE_NAME + BROKEN_SUFFIX + LocalDateTime.now().format(STAMP));
        try {
            Files.move(source, spoiled, StandardCopyOption.REPLACE_EXISTING);
            QuarryMod.LOGGER.warn("[quarry] unreadable {} moved to {}", FILE_NAME, spoiled.getFileName());
        } catch (IOException error) {
            QuarryMod.LOGGER.warn("[quarry] cannot set aside {}: {}", FILE_NAME, error.toString());
        }
    }

    // WHY: одна запись с неизвестным блоком роняла чтение целиком, и следующая запись стирала весь
    // WHY: карьер: такая запись пропускается, остальные читаются
    private static Map<QuarryBlockKey, QuarryBlock> readBlocks(CompoundTag compound) {
        Map<QuarryBlockKey, QuarryBlock> blocks = new HashMap<>();
        ListTag blockList = compound.getList("quarryBlocks", COMPOUND);
        for (int index = 0; index < blockList.size(); index++) {
            QuarryBlock block = QuarryBlock.fromNBT(blockList.getCompound(index));
            if (block != null) blocks.put(new QuarryBlockKey(block.getPos(), block.getDimension()), block);
        }
        return blocks;
    }

    private static Map<QuarryBlockKey, Long> readTimes(CompoundTag compound, String list, String field) {
        Map<QuarryBlockKey, Long> times = new HashMap<>();
        ListTag stored = compound.getList(list, COMPOUND);
        for (int index = 0; index < stored.size(); index++) {
            CompoundTag tag = stored.getCompound(index);
            BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
            times.put(new QuarryBlockKey(pos, tag.getString("dimension")), tag.getLong(field));
        }
        return times;
    }

    private static java.util.List<QuarryBlockRule> readRules(CompoundTag compound) {
        java.util.List<QuarryBlockRule> rules = new java.util.ArrayList<>();
        ListTag stored = compound.getList("blockRules", COMPOUND);
        for (int index = 0; index < stored.size(); index++) {
            QuarryBlockRule rule = QuarryBlockRule.fromNBT(stored.getCompound(index));
            if (rule != null) rules.add(rule);
        }
        return rules;
    }

    public QuarryBlockManager getBlockManager() {
        return blockManager;
    }
}
