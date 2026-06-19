package com.persiki84.quarrymod.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

public class QuarryBlock {
    private final BlockPos pos;
    private final BlockState originalState;
    private final String dimension;

    public QuarryBlock(BlockPos pos, BlockState originalState, String dimension) {
        this.pos = pos;
        this.originalState = originalState;
        this.dimension = dimension;
    }

    public BlockPos getPos() {
        return pos;
    }

    public BlockState getOriginalState() {
        return originalState;
    }

    public String getDimension() {
        return dimension;
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        tag.putString("block", BuiltInRegistries.BLOCK.getKey(originalState.getBlock()).toString());
        tag.putString("dimension", dimension);
        return tag;
    }

    public static QuarryBlock fromNBT(CompoundTag tag) {
        BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
        Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation(tag.getString("block")));
        String dimension = tag.getString("dimension");
        return new QuarryBlock(pos, block.defaultBlockState(), dimension);
    }
}
