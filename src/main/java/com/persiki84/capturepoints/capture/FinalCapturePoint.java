package com.persiki84.capturepoints.capture;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class FinalCapturePoint extends CapturePoint {
    private List<BlockPos> commandBlockPositions;

    public FinalCapturePoint(String name, BlockPos position, int radius, int captureTime, int cooldown) {
        super(name, position, radius, captureTime, cooldown);
        this.commandBlockPositions = new ArrayList<>();
    }

    public List<BlockPos> getCommandBlockPositions() {
        return commandBlockPositions;
    }

    public void addCommandBlock(BlockPos pos) {
        if (!commandBlockPositions.contains(pos)) {
            commandBlockPositions.add(pos);
        }
    }

    public void removeCommandBlock(BlockPos pos) {
        commandBlockPositions.remove(pos);
    }

    public void clearCommandBlocks() {
        commandBlockPositions.clear();
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        tag.putBoolean("isFinal", true);

        CompoundTag cmdBlocksTag = new CompoundTag();
        for (int i = 0; i < commandBlockPositions.size(); i++) {
            BlockPos pos = commandBlockPositions.get(i);
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("x", pos.getX());
            posTag.putInt("y", pos.getY());
            posTag.putInt("z", pos.getZ());
            cmdBlocksTag.put("pos" + i, posTag);
        }
        cmdBlocksTag.putInt("count", commandBlockPositions.size());
        tag.put("commandBlocks", cmdBlocksTag);

        return tag;
    }

    public static FinalCapturePoint loadFinal(CompoundTag tag) {
        String name = tag.getString("name");
        BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
        int radius = tag.getInt("radius");
        int captureTime = tag.getInt("captureTime");
        int cooldown = tag.getInt("cooldown");

        FinalCapturePoint point = new FinalCapturePoint(name, pos, radius, captureTime, cooldown);

        if (tag.contains("ownerTeam")) {
            point.setOwnerTeam(tag.getString("ownerTeam"));
        }

        point.setLastCaptureTime(tag.getLong("lastCaptureTime"));
        if (tag.contains("rewardItem")) {
            Item rewardItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(tag.getString("rewardItem")));
            if (rewardItem != null) point.setReward(new ItemStack(rewardItem));
        }
        point.setRewardAmount(tag.getInt("rewardAmount"));

        if (tag.contains("commandBlocks")) {
            CompoundTag cmdBlocksTag = tag.getCompound("commandBlocks");
            int count = cmdBlocksTag.getInt("count");
            for (int i = 0; i < count; i++) {
                CompoundTag posTag = cmdBlocksTag.getCompound("pos" + i);
                BlockPos cmdPos = new BlockPos(
                        posTag.getInt("x"),
                        posTag.getInt("y"),
                        posTag.getInt("z")
                );
                point.commandBlockPositions.add(cmdPos);
            }
        }

        return point;
    }
}
