package com.persiki84.zones.mark;

import com.persiki84.shared.menu.MenuStates;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

public final class MarkMenuState {
    public static final String MENU_ID = "marks";
    public static final String MARKS = "marks";

    private MarkMenuState() {}

    public static void register() {
        MenuStates.register(MENU_ID, 2, player -> snapshot());
    }

    private static CompoundTag snapshot() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();

        for (MapMark mark : MarkRegistry.all()) {
            list.add(describe(mark));
        }
        tag.put(MARKS, list);
        return tag;
    }

    private static CompoundTag describe(MapMark mark) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", mark.id());
        tag.putString("label", mark.label());
        tag.putInt("x", mark.position().getX());
        tag.putInt("y", mark.position().getY());
        tag.putInt("z", mark.position().getZ());
        tag.putString("dimension", mark.dimension().toString());
        tag.putInt("color", mark.color());
        tag.putBoolean("everyone", mark.everyone());
        tag.putBoolean("inWorld", mark.inWorld());
        tag.putString("kind", mark.kind().id());
        tag.putInt("scale", mark.scalePercent());
        tag.putInt("markerRange", mark.markerRange());
        tag.putInt("hideRadius", mark.hideZone().radius());
        tag.putString("hideShape", mark.hideZone().shape().id());
        tag.putInt("hideHeight", mark.hideZone().height());
        tag.putBoolean("hideShown", mark.hideZone().shown());
        tag.putInt("hideColor", mark.hideZone().color());

        ListTag lines = new ListTag();
        for (String line : mark.lines()) {
            lines.add(StringTag.valueOf(line));
        }
        tag.put("lines", lines);

        ListTag teams = new ListTag();
        for (String team : mark.teams()) {
            teams.add(StringTag.valueOf(team));
        }
        tag.put("teams", teams);
        return tag;
    }
}
