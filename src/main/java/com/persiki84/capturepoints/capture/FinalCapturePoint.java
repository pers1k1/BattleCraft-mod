package com.persiki84.capturepoints.capture;

import com.persiki84.shared.zone.ZoneArea;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

public class FinalCapturePoint extends CapturePoint {
    private List<BlockPos> commandBlockPositions;

    public FinalCapturePoint(String name, ZoneArea area, int captureTime, int cooldown) {
        super(name, area, captureTime, cooldown);
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
        ZoneArea area = ZoneArea.loadFrom(tag);
        int captureTime = tag.getInt("captureTime");
        int cooldown = tag.getInt("cooldown");

        FinalCapturePoint point = new FinalCapturePoint(name, area, captureTime, cooldown);
        point.setDimension(readDimension(tag));

        if (tag.contains("ownerTeam")) {
            point.setOwnerTeam(tag.getString("ownerTeam"));
        }

        point.setLastCaptureTime(tag.getLong("lastCaptureTime"));
        readBonuses(tag, point);
        readTuning(tag, point);
        readCommandBlocks(tag, point);

        return point;
    }

    private static void readCommandBlocks(CompoundTag tag, FinalCapturePoint point) {
        if (!tag.contains("commandBlocks")) return;

        CompoundTag cmdBlocksTag = tag.getCompound("commandBlocks");
        int count = cmdBlocksTag.getInt("count");
        for (int i = 0; i < count; i++) {
            CompoundTag posTag = cmdBlocksTag.getCompound("pos" + i);
            point.commandBlockPositions.add(new BlockPos(
                    posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z")));
        }
    }
}
