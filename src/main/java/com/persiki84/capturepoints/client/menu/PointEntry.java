package com.persiki84.capturepoints.client.menu;

import com.persiki84.capturepoints.menu.CapturePointMenuState;
import com.persiki84.shared.zone.ZoneShape;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

record PointEntry(String name, boolean last, CompoundTag tag, ItemStack reward, ItemStack income) {
    static final String PREFIX = "point:";

    static PointEntry of(CompoundTag tag) {
        return new PointEntry(tag.getString("name"), tag.getBoolean(CapturePointMenuState.FINAL_FLAG), tag,
                stackOf(tag, "rewardStack"), stackOf(tag, "incomeStack"));
    }

    private static ItemStack stackOf(CompoundTag tag, String key) {
        return tag.contains(key) ? ItemStack.of(tag.getCompound(key)) : ItemStack.EMPTY;
    }

    String key() {
        return PREFIX + name;
    }

    String quoted() {
        return "\"" + name + "\"";
    }

    String root() {
        return last ? "finalpoint" : "capturepoint";
    }

    String owner() {
        return tag.getString("owner");
    }

    int number(String field) {
        return tag.getInt(field);
    }

    boolean flag(String field) {
        return tag.getBoolean(field);
    }

    ZoneShape shape() {
        ZoneShape shape = ZoneShape.byId(tag.getString("shape"));
        return shape == null ? ZoneShape.CIRCLE : shape;
    }

    boolean here() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null
                && minecraft.level.dimension().location().toString().equals(tag.getString("dimension"));
    }
}
