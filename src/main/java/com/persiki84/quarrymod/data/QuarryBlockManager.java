package com.persiki84.quarrymod.data;

import com.persiki84.quarrymod.block.QuarryBlocks;
import com.persiki84.quarrymod.network.QuarryBroadcast;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class QuarryBlockManager {
    public interface FieldSink {
        void accept(BlockPos pos, ResourceLocation ore, int multiplier, int remainingSeconds, int totalSeconds);
    }

    private final Map<QuarryBlockKey, QuarryBlock> quarryBlocks = new ConcurrentHashMap<>();
    private final Map<QuarryBlockKey, Long> customCooldowns = new ConcurrentHashMap<>();
    private final Map<QuarryBlockKey, Long> pendingRegenerations = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, QuarryBlockRule> blockRules = new ConcurrentHashMap<>();
    private final Map<Long, List<QuarryBlock>> chunkIndex = new ConcurrentHashMap<>();

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
        BLOCK_DROPS.put(Blocks.DEEPSLATE_DIAMOND_ORE, new ItemStack(Items.DIAMOND, 1));
        BLOCK_DROPS.put(Blocks.DEEPSLATE_COAL_ORE, new ItemStack(Items.COAL, 1));
        BLOCK_DROPS.put(Blocks.DEEPSLATE_IRON_ORE, new ItemStack(Items.IRON_INGOT, 1));
        BLOCK_DROPS.put(Blocks.DEEPSLATE_GOLD_ORE, new ItemStack(Items.GOLD_INGOT, 1));
        BLOCK_DROPS.put(Blocks.DEEPSLATE_EMERALD_ORE, new ItemStack(Items.EMERALD, 1));
        BLOCK_DROPS.put(Blocks.DEEPSLATE_REDSTONE_ORE, new ItemStack(Items.REDSTONE, 4));
        BLOCK_DROPS.put(Blocks.DEEPSLATE_LAPIS_ORE, new ItemStack(Items.LAPIS_LAZULI, 6));
        BLOCK_DROPS.put(Blocks.DEEPSLATE_COPPER_ORE, new ItemStack(Items.RAW_COPPER, 3));
        BLOCK_DROPS.put(Blocks.NETHER_QUARTZ_ORE, new ItemStack(Items.QUARTZ, 2));
        BLOCK_DROPS.put(Blocks.NETHER_GOLD_ORE, new ItemStack(Items.GOLD_NUGGET, 4));
        BLOCK_DROPS.put(Blocks.ANCIENT_DEBRIS, new ItemStack(Items.NETHERITE_SCRAP, 1));
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
        Long custom = customCooldowns.get(new QuarryBlockKey(pos, dimension));
        if (custom != null) return custom;

        QuarryBlock block = quarryBlocks.get(new QuarryBlockKey(pos, dimension));
        if (block == null) return globalCooldownTime;

        QuarryBlockRule rule = blockRules.get(blockId(block.getOriginalState().getBlock()));
        if (rule == null || rule.cooldownSeconds() == QuarryBlockRule.GLOBAL_COOLDOWN) return globalCooldownTime;
        return rule.cooldownSeconds() * 1000L;
    }

    private static ResourceLocation blockId(Block block) {
        return net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block);
    }

    public QuarryBlockRule rule(Block block) {
        ResourceLocation id = blockId(block);
        return id == null ? null : blockRules.get(id);
    }

    public QuarryBlockRule ruleOrCreate(Block block) {
        ResourceLocation id = blockId(block);
        if (id == null) return null;
        return blockRules.computeIfAbsent(id, QuarryBlockRule::new);
    }

    public List<QuarryBlockRule> rules() {
        List<QuarryBlockRule> known = new ArrayList<>();
        for (Block block : BLOCK_DROPS.keySet()) {
            QuarryBlockRule rule = ruleOrCreate(block);
            if (rule != null) known.add(rule);
        }
        known.sort((left, right) -> left.block().toString().compareTo(right.block().toString()));
        return known;
    }

    public void loadRules(List<QuarryBlockRule> stored) {
        blockRules.clear();
        for (QuarryBlockRule rule : stored) {
            blockRules.put(rule.block(), rule);
        }
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
            QuarryBlock added = new QuarryBlock(pos.immutable(), state, dimension);
            quarryBlocks.put(key, added);
            index(added);
        }
    }

    public boolean isQuarryBlock(BlockPos pos, String dimension) {
        return quarryBlocks.containsKey(new QuarryBlockKey(pos, dimension));
    }

    public void removeQuarryBlock(BlockPos pos, String dimension) {
        QuarryBlockKey key = new QuarryBlockKey(pos, dimension);
        QuarryBlock removed = quarryBlocks.remove(key);
        customCooldowns.remove(key);
        pendingRegenerations.remove(key);
        if (removed != null) unindex(removed);
    }

    public ItemStack getDropForBlock(Block block) {
        ItemStack drop = BLOCK_DROPS.getOrDefault(block, ItemStack.EMPTY).copy();
        if (drop.isEmpty()) return drop;

        drop.setCount(drop.getCount() * multiplier(block));
        return drop;
    }

    public Block originalBlock(BlockPos pos, String dimension) {
        QuarryBlock block = quarryBlocks.get(new QuarryBlockKey(pos, dimension));
        return block == null ? null : block.getOriginalState().getBlock();
    }

    public int multiplier(Block block) {
        QuarryBlockRule rule = rule(block);
        return rule == null ? QuarryBlockRule.MIN_MULTIPLIER : rule.multiplier();
    }

    public boolean isValidQuarryBlock(Block block) {
        return BLOCK_DROPS.containsKey(block);
    }

    // WHY: два пакета ломания приходят в одном тике, и проверка отката отдельно от его записи
    // WHY: пропускала оба: дроп выдавался дважды за один блок. Бронь атомарна и идёт первой
    public boolean claim(BlockPos pos, String dimension) {
        if (!isQuarryBlock(pos, dimension)) return false;

        QuarryBlockKey key = new QuarryBlockKey(pos, dimension);
        long readyAt = System.currentTimeMillis() + getCooldownTime(pos, dimension);
        return pendingRegenerations.putIfAbsent(key, readyAt) == null;
    }

    public void excavate(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, QuarryBlocks.excavated(), 3);
    }

    // WHY: сломанный блок стоит выработкой, пока не отработает регенерация, поэтому выключение
    // WHY: модуля обязано разобрать очередь, а не бросить карту дырами навсегда
    public void restoreAllPending(MinecraftServer server) {
        if (pendingRegenerations.isEmpty()) return;

        for (QuarryBlockKey key : new ArrayList<>(pendingRegenerations.keySet())) {
            pendingRegenerations.put(key, 0L);
        }
        tickRegenerations(server);
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
            regenerate(server, key);
            pendingRegenerations.remove(key);
        }
    }

    private void regenerate(MinecraftServer server, QuarryBlockKey key) {
        ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(key.dimension()));
        ServerLevel level = server.getLevel(dimKey);
        if (level == null) return;

        QuarryBlock quarryBlock = quarryBlocks.get(key);
        if (quarryBlock == null || !replaceable(level.getBlockState(key.pos()))) return;

        level.setBlock(key.pos(), quarryBlock.getOriginalState(), 3);
        QuarryBroadcast.regenerated(level, key.pos(), quarryBlock.getOriginalState().getBlock(), this);
    }

    // WHY: выработки в мире бывают трёх видов сразу: свой блок, бедрок от прежних версий мода
    // WHY: и воздух там, где выработку снёс оператор в креативе. Руда обязана вернуться во всех
    private static boolean replaceable(BlockState state) {
        return QuarryBlocks.isExcavated(state) || state.is(Blocks.BEDROCK) || state.isAir();
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
        return remaining <= 0 ? 0 : (remaining + 999) / 1000;
    }

    public long plannedCooldown(BlockPos pos, String dimension) {
        return getCooldownTime(pos, dimension) / 1000;
    }

    // WHY: клиенту уходят только блоки рядом и с открытой наружу гранью: полный список это карта
    // WHY: всех руд сборки, то есть рентген, выданный модом добровольно
    public void collectAround(ServerLevel level, BlockPos center, int radius, int limit, FieldSink sink) {
        String dimension = level.dimension().location().toString();
        int chunkReach = radius / 16 + 1;
        int sent = 0;

        for (int ring = 0; ring <= chunkReach && sent < limit; ring++) {
            sent = collectRing(level, dimension, center, ring, radius, limit, sent, sink);
        }
    }

    // WHY: потолок записей режет поле, и обход чанков от угла отдавал его дальним клеткам, а ближние
    // WHY: выпадали из снимка и пересоздавались на клиенте. Поэтому обход идёт кольцами от игрока
    private int collectRing(ServerLevel level, String dimension, BlockPos center, int ring,
                            int radius, int limit, int sent, FieldSink sink) {
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        for (int offsetX = -ring; offsetX <= ring && sent < limit; offsetX++) {
            for (int offsetZ = -ring; offsetZ <= ring && sent < limit; offsetZ++) {
                if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != ring) continue;

                long chunk = ChunkPos.asLong(centerChunkX + offsetX, centerChunkZ + offsetZ);
                List<QuarryBlock> inChunk = chunkIndex.get(chunk);
                if (inChunk != null) sent = collectChunk(level, inChunk, dimension, center, radius, limit, sent, sink);
            }
        }
        return sent;
    }

    private int collectChunk(ServerLevel level, List<QuarryBlock> inChunk, String dimension, BlockPos center,
                             int radius, int limit, int sent, FieldSink sink) {
        for (QuarryBlock block : inChunk) {
            if (sent >= limit) return sent;
            if (!block.getDimension().equals(dimension)) continue;

            BlockPos pos = block.getPos();
            if (pos.distSqr(center) > (double) radius * radius) continue;
            if (!exposed(level, pos)) continue;

            Block ore = block.getOriginalState().getBlock();
            sink.accept(pos, blockId(ore), multiplier(ore), (int) getRemainingCooldown(pos, dimension),
                    (int) plannedCooldown(pos, dimension));
            sent++;
        }
        return sent;
    }

    private static boolean exposed(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;

        for (Direction side : Direction.values()) {
            BlockPos neighbour = pos.relative(side);
            if (!level.getBlockState(neighbour).isSolidRender(level, neighbour)) return true;
        }
        return false;
    }

    // WHY: взрыв и поршень зовут проверку на каждый свой блок, поэтому у них есть дешёвый
    // WHY: отказ: карьера в соседних чанках нет, значит и перебирать сотни позиций незачем
    public boolean anyNear(BlockPos pos, int chunkReach) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        for (int offsetX = -chunkReach; offsetX <= chunkReach; offsetX++) {
            for (int offsetZ = -chunkReach; offsetZ <= chunkReach; offsetZ++) {
                if (chunkIndex.containsKey(ChunkPos.asLong(chunkX + offsetX, chunkZ + offsetZ))) return true;
            }
        }
        return false;
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
        reindex();
    }

    private void reindex() {
        chunkIndex.clear();
        for (QuarryBlock block : quarryBlocks.values()) {
            index(block);
        }
    }

    private void index(QuarryBlock block) {
        chunkIndex.computeIfAbsent(chunkKey(block.getPos()), key -> new ArrayList<>()).add(block);
    }

    private void unindex(QuarryBlock block) {
        long key = chunkKey(block.getPos());
        List<QuarryBlock> inChunk = chunkIndex.get(key);
        if (inChunk == null) return;

        inChunk.removeIf(stored -> stored.getPos().equals(block.getPos())
                && stored.getDimension().equals(block.getDimension()));
        if (inChunk.isEmpty()) chunkIndex.remove(key);
    }

    private static long chunkKey(BlockPos pos) {
        return ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
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
