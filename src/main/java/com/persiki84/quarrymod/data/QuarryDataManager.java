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
import java.util.HashMap;
import java.util.Map;

public class QuarryDataManager {
    private static final String FILE_NAME = "quarry_blocks.dat";

    private final File dataFile;
    private final QuarryBlockManager blockManager;

    // WHY: файл лежал в папке установки сервера, поэтому все миры делили одни карьерные блоки:
    // WHY: в соседнем мире по тем же координатам оживали чужие неломаемые блоки
    public QuarryDataManager(MinecraftServer server) {
        this.dataFile = server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME).toFile();
        this.blockManager = new QuarryBlockManager();
    }

    public void save() {
        CompoundTag compound = new CompoundTag();

        ListTag blockList = new ListTag();
        for (QuarryBlock block : blockManager.getAllQuarryBlocks().values()) {
            blockList.add(block.toNBT());
        }
        compound.put("quarryBlocks", blockList);

        ListTag customCooldownList = new ListTag();
        for (Map.Entry<QuarryBlockKey, Long> entry : blockManager.getCustomCooldowns().entrySet()) {
            CompoundTag cdTag = new CompoundTag();
            cdTag.putInt("x", entry.getKey().pos().getX());
            cdTag.putInt("y", entry.getKey().pos().getY());
            cdTag.putInt("z", entry.getKey().pos().getZ());
            cdTag.putString("dimension", entry.getKey().dimension());
            cdTag.putLong("cooldown_ms", entry.getValue());
            customCooldownList.add(cdTag);
        }
        compound.put("customCooldowns", customCooldownList);

        compound.putLong("globalCooldownTime", blockManager.getGlobalCooldownTime());

        ListTag ruleList = new ListTag();
        for (QuarryBlockRule rule : blockManager.rules()) {
            ruleList.add(rule.toNBT());
        }
        compound.put("blockRules", ruleList);

        ListTag pendingRegenList = new ListTag();
        for (Map.Entry<QuarryBlockKey, Long> entry : blockManager.getPendingRegenerations().entrySet()) {
            CompoundTag regenTag = new CompoundTag();
            regenTag.putInt("x", entry.getKey().pos().getX());
            regenTag.putInt("y", entry.getKey().pos().getY());
            regenTag.putInt("z", entry.getKey().pos().getZ());
            regenTag.putString("dimension", entry.getKey().dimension());
            regenTag.putLong("target_time", entry.getValue());
            pendingRegenList.add(regenTag);
        }
        compound.put("pendingRegenerations", pendingRegenList);

        store(compound);
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
            CompoundTag compound = NbtIo.readCompressed(dataFile);

            ListTag blockList = compound.getList("quarryBlocks", 10);
            Map<QuarryBlockKey, QuarryBlock> blocks = new HashMap<>();
            for (int i = 0; i < blockList.size(); i++) {
                QuarryBlock block = QuarryBlock.fromNBT(blockList.getCompound(i));
                QuarryBlockKey key = new QuarryBlockKey(block.getPos(), block.getDimension());
                blocks.put(key, block);
            }

            Map<QuarryBlockKey, Long> customCooldowns = new HashMap<>();
            ListTag customCooldownList = compound.getList("customCooldowns", 10);
            for (int i = 0; i < customCooldownList.size(); i++) {
                CompoundTag cdTag = customCooldownList.getCompound(i);
                BlockPos pos = new BlockPos(cdTag.getInt("x"), cdTag.getInt("y"), cdTag.getInt("z"));
                String dimension = cdTag.getString("dimension");
                long cooldownMs = cdTag.getLong("cooldown_ms");
                customCooldowns.put(new QuarryBlockKey(pos, dimension), cooldownMs);
            }

            long globalCooldown = compound.contains("globalCooldownTime") ? compound.getLong("globalCooldownTime") : 20000;

            Map<QuarryBlockKey, Long> pendingRegenerations = new HashMap<>();
            ListTag pendingRegenList = compound.getList("pendingRegenerations", 10);
            for (int i = 0; i < pendingRegenList.size(); i++) {
                CompoundTag regenTag = pendingRegenList.getCompound(i);
                BlockPos pos = new BlockPos(regenTag.getInt("x"), regenTag.getInt("y"), regenTag.getInt("z"));
                String dimension = regenTag.getString("dimension");
                long targetTime = regenTag.getLong("target_time");
                pendingRegenerations.put(new QuarryBlockKey(pos, dimension), targetTime);
            }

            blockManager.loadData(blocks, customCooldowns, pendingRegenerations, globalCooldown);
            blockManager.loadRules(readRules(compound));
        } catch (IOException | RuntimeException error) {
            QuarryMod.LOGGER.warn("[quarry] cannot read {}: {}", FILE_NAME, error.toString());
        }
    }

    private static java.util.List<QuarryBlockRule> readRules(CompoundTag compound) {
        java.util.List<QuarryBlockRule> rules = new java.util.ArrayList<>();
        ListTag stored = compound.getList("blockRules", 10);
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
