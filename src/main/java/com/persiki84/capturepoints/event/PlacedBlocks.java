package com.persiki84.capturepoints.event;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class PlacedBlocks {
    private static final String TAG = "playerPlacedBlocks";
    private static final int MEMORY_LIMIT = 200_000;

    private static final Map<String, Set<Long>> BY_DIMENSION = new HashMap<>();

    private PlacedBlocks() {}

    public static void remember(LevelAccessor level, BlockPos pos) {
        String dimension = dimensionOf(level);
        if (dimension == null) return;

        Set<Long> known = BY_DIMENSION.computeIfAbsent(dimension, key -> new HashSet<>());
        if (known.size() >= MEMORY_LIMIT) return;
        known.add(pos.asLong());
    }

    public static boolean forget(LevelAccessor level, BlockPos pos) {
        String dimension = dimensionOf(level);
        if (dimension == null) return false;

        Set<Long> known = BY_DIMENSION.get(dimension);
        return known != null && known.remove(pos.asLong());
    }

    public static boolean remembers(LevelAccessor level, BlockPos pos) {
        String dimension = dimensionOf(level);
        if (dimension == null) return false;

        Set<Long> known = BY_DIMENSION.get(dimension);
        return known != null && known.contains(pos.asLong());
    }

    public static void clear() {
        BY_DIMENSION.clear();
    }

    public static void save(CompoundTag tag) {
        CompoundTag stored = new CompoundTag();
        for (Map.Entry<String, Set<Long>> entry : BY_DIMENSION.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            stored.putLongArray(entry.getKey(), entry.getValue().stream().mapToLong(Long::longValue).toArray());
        }
        tag.put(TAG, stored);
    }

    public static void load(CompoundTag tag) {
        BY_DIMENSION.clear();
        if (!tag.contains(TAG)) return;

        CompoundTag stored = tag.getCompound(TAG);
        for (String dimension : stored.getAllKeys()) {
            Set<Long> known = new HashSet<>();
            for (long packed : stored.getLongArray(dimension)) {
                known.add(packed);
            }
            BY_DIMENSION.put(dimension, known);
        }
    }

    private static String dimensionOf(LevelAccessor level) {
        if (!(level instanceof Level world)) return null;

        ResourceLocation id = world.dimension().location();
        return id == null ? null : id.toString();
    }
}
