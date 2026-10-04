package com.persiki84.zones.client.menu;

import com.persiki84.shared.zone.ZoneShape;
import com.persiki84.zones.mark.MapMark;
import com.persiki84.zones.mark.MarkHideZone;
import com.persiki84.zones.mark.MarkKind;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

record MarkEntry(String id, CompoundTag tag) {
    String label() {
        return tag.getString("label");
    }

    int number(String field) {
        return tag.getInt(field);
    }

    boolean flag(String field) {
        return tag.getBoolean(field);
    }

    MarkKind kind() {
        return MarkKind.byId(tag.getString("kind"));
    }

    int color() {
        return tag.contains("color") ? tag.getInt("color") : MapMark.DEFAULT_COLOR;
    }

    int scale() {
        return tag.contains("scale") ? tag.getInt("scale") : MapMark.SCALE_FULL;
    }

    ZoneShape hideShape() {
        ZoneShape shape = ZoneShape.byId(tag.getString("hideShape"));
        return shape == null ? ZoneShape.CIRCLE : shape;
    }

    int hideColor() {
        int own = tag.getInt("hideColor");
        return own == MarkHideZone.MARK_COLOR ? color() : own;
    }

    List<String> lines() {
        return strings("lines");
    }

    List<String> teams() {
        return strings("teams");
    }

    private List<String> strings(String field) {
        ListTag stored = tag.getList(field, Tag.TAG_STRING);
        List<String> values = new ArrayList<>();
        for (int index = 0; index < stored.size(); index++) {
            values.add(stored.getString(index));
        }
        return values;
    }

    boolean here() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null
                && minecraft.level.dimension().location().toString().equals(tag.getString("dimension"));
    }
}
