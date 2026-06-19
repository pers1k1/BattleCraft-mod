package com.persiki84.quarrymod.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class QuarryBlockManager {
    private final Map<QuarryBlockKey, QuarryBlock> quarryBlocks = new ConcurrentHashMap<>();
    private final Map<QuarryBlockKey, Long> customCooldowns = new ConcurrentHashMap<>();
    private final Map<QuarryBlockKey, Long> pendingRegenerations = new ConcurrentHashMap<>();

    private long globalCooldownTime = 20000;
    private static final long MIN_COOLDOWN = 1000;
    private static final long MAX_COOLDOWN = 3600000;

    private static final Map<Block, ItemStack> BLOCK_DROPS = new HashMap<>();
    static {
        BLOCK_DROPS.put(Blocks.DIAMOND_ORE, new ItemStack(Items.DIAMOND, 1));
        BLOCK_DROPS.put(Blocks.COAL_ORE, new ItemStack(Items.COAL, 1));
        BLOCK_DROPS.put(Blocks.IRON_ORE, new ItemStack(Items.IRON_INGOT, 1));
        BLOCK_DROPS.put(Blocks.GOLD_ORE, new ItemStack(Items.GOLD_INGOT, 1));
        BLOCK_DROPS.put(Blocks.EMERALD_ORE, new ItemStack(Items.EMERALD, 1));
        BLOCK_DROPS.put(Blocks.REDSTONE_ORE, new ItemStack(Items.REDSTONE, 4));
        BLOCK_DROPS.put(Blocks.LAPIS_ORE, new ItemStack(Items.LAPIS_LAZULI, 6));
        BLOCK_DROPS.put(Blocks.COPPER_ORE, new ItemStack(Items.RAW_COPPER, 3));
    }

    public boolean setGlobalCooldown(long seconds) {
        long milliseconds = seconds * 1000;
        if (milliseconds < MIN_COOLDOWN || milliseconds > MAX_COOLDOWN) {
            return false;
        }
        this.globalCooldownTime = milliseconds;
        return true;
    }

    public boolean setCustomCooldown(BlockPos pos, String dimension, long seconds) {
        if (!isQuarryBlock(pos, dimension)) {
            return false;
        }
        long milliseconds = seconds * 1000;
        if (milliseconds < MIN_COOLDOWN || milliseconds > MAX_COOLDOWN) {
            return false;
        }
        customCooldowns.put(new QuarryBlockKey(pos, dimension), milliseconds);
        return true;
    }

    public void removeCustomCooldown(BlockPos pos, String dimension) {
        customCooldowns.remove(new QuarryBlockKey(pos, dimension));
    }

    private long getCooldownTime(BlockPos pos, String dimension) {
        return customCooldowns.getOrDefault(new QuarryBlockKey(pos, dimension), globalCooldownTime);
    }

    public long getGlobalCooldown() {
        return globalCooldownTime / 1000;
    }

    public long getCustomCooldown(BlockPos pos, String dimension) {
        Long custom = customCooldowns.get(new QuarryBlockKey(pos, dimension));
        return custom != null ? custom / 1000 : -1;
    }

    public void addQuarryBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (BLOCK_DROPS.containsKey(state.getBlock())) {
            String dimension = level.dimension().location().toString();
            QuarryBlockKey key = new QuarryBlockKey(pos, dimension);
            quarryBlocks.put(key, new QuarryBlock(pos.immutable(), state, dimension));
        }
    }

    public boolean isQuarryBlock(BlockPos pos, String dimension) {
        return quarryBlocks.containsKey(new QuarryBlockKey(pos, dimension));
    }

    public void removeQuarryBlock(BlockPos pos, String dimension) {
        QuarryBlockKey key = new QuarryBlockKey(pos, dimension);
        quarryBlocks.remove(key);
        customCooldowns.remove(key);
        pendingRegenerations.remove(key);
    }

    public ItemStack getDropForBlock(Block block) {
        return BLOCK_DROPS.getOrDefault(block, ItemStack.EMPTY).copy();
    }

    public boolean isValidQuarryBlock(Block block) {
        return BLOCK_DROPS.containsKey(block);
    }

    public void handleBlockBreak(ServerLevel level, BlockPos pos, BlockState originalState) {
        String dimension = level.dimension().location().toString();
        if (!isOnCooldown(pos, dimension)) {
            level.setBlock(pos, Blocks.BEDROCK.defaultBlockState(), 3);
            QuarryBlockKey key = new QuarryBlockKey(pos, dimension);
            long cooldownTime = getCooldownTime(pos, dimension);
            pendingRegenerations.put(key, System.currentTimeMillis() + cooldownTime);
            spawnBreakParticles(level, pos);
        }
    }

    public void tickRegenerations(MinecraftServer server) {
        long now = System.currentTimeMillis();
        List<QuarryBlockKey> toRegenerate = new ArrayList<>();
        for (Map.Entry<QuarryBlockKey, Long> entry : pendingRegenerations.entrySet()) {
            if (now >= entry.getValue()) {
                toRegenerate.add(entry.getKey());
            }
        }
        for (QuarryBlockKey key : toRegenerate) {
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(key.dimension()));
            ServerLevel level = server.getLevel(dimKey);
            if (level != null) {
                QuarryBlock quarryBlock = quarryBlocks.get(key);
                if (quarryBlock != null && level.getBlockState(key.pos()).is(Blocks.BEDROCK)) {
                    spawnRegenerationParticles(level, key.pos());
                    level.setBlock(key.pos(), quarryBlock.getOriginalState(), 3);
                }
            }
            pendingRegenerations.remove(key);
        }
    }

    private void spawnBreakParticles(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        for (int i = 0; i < 20; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * 0.5;
            double offsetY = (level.random.nextDouble() - 0.5) * 0.5;
            double offsetZ = (level.random.nextDouble() - 0.5) * 0.5;

            level.sendParticles(ParticleTypes.SMOKE,
                    x + offsetX, y + offsetY, z + offsetZ,
                    1, 0, 0.1, 0, 0.05);
        }
    }

    private void spawnRegenerationParticles(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        for (int i = 0; i < 30; i++) {
            double angle = (i / 30.0) * Math.PI * 2;
            double radius = 0.5;
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;

            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    x + offsetX, y, z + offsetZ,
                    1, 0, 0.2, 0, 0.02);
        }

        level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0, 0, 0, 0);
    }

    public boolean isOnCooldown(BlockPos pos, String dimension) {
        QuarryBlockKey key = new QuarryBlockKey(pos, dimension);
        Long targetTime = pendingRegenerations.get(key);
        if (targetTime == null) return false;
        return System.currentTimeMillis() < targetTime;
    }

    public long getRemainingCooldown(BlockPos pos, String dimension) {
        QuarryBlockKey key = new QuarryBlockKey(pos, dimension);
        Long targetTime = pendingRegenerations.get(key);
        if (targetTime == null) return 0;
        long remaining = targetTime - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }

    public Map<QuarryBlockKey, QuarryBlock> getAllQuarryBlocks() {
        return new HashMap<>(quarryBlocks);
    }

    public void loadData(Map<QuarryBlockKey, QuarryBlock> blocks,
                         Map<QuarryBlockKey, Long> customCooldownsData,
                         Map<QuarryBlockKey, Long> pendingRegenerationsData,
                         long globalCooldown) {
        quarryBlocks.clear();
        quarryBlocks.putAll(blocks);
        customCooldowns.clear();
        customCooldowns.putAll(customCooldownsData);
        pendingRegenerations.clear();
        pendingRegenerations.putAll(pendingRegenerationsData);
        globalCooldownTime = globalCooldown;
    }

    public Map<QuarryBlockKey, Long> getCustomCooldowns() {
        return new HashMap<>(customCooldowns);
    }

    public long getGlobalCooldownTime() {
        return globalCooldownTime;
    }

    public Map<QuarryBlockKey, Long> getPendingRegenerations() {
        return new HashMap<>(pendingRegenerations);
    }
}
