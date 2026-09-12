package com.persiki84.zones.mark;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.Team;

import java.util.LinkedHashSet;
import java.util.Set;

public final class MapMark {
    public static final int DEFAULT_COLOR = 0xFFE7E9F4;

    private final String id;
    private BlockPos position;
    private ResourceLocation dimension;
    private String label;
    private int color;
    private final Set<String> teams = new LinkedHashSet<>();

    public MapMark(String id, BlockPos position, ResourceLocation dimension, String label, int color) {
        this.id = id;
        this.position = position;
        this.dimension = dimension;
        this.label = label;
        this.color = color;
    }

    public String id() { return id; }
    public BlockPos position() { return position; }
    public ResourceLocation dimension() { return dimension; }
    public String label() { return label == null || label.isEmpty() ? id : label; }
    public int color() { return color; }
    public Set<String> teams() { return teams; }

    public void setPosition(BlockPos value) { this.position = value; }
    public void setDimension(ResourceLocation value) { this.dimension = value; }
    public void setLabel(String value) { this.label = value; }
    public void setColor(int value) { this.color = value; }

    public boolean everyone() {
        return teams.isEmpty();
    }

    public void showEveryone() {
        teams.clear();
    }

    public boolean visibleTo(Team team) {
        return everyone() || (team != null && teams.contains(team.getName()));
    }

    public void allow(String team) {
        teams.add(team);
    }

    public void forbid(String team) {
        teams.remove(team);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeBlockPos(position);
        buf.writeResourceLocation(dimension);
        buf.writeUtf(label == null ? "" : label);
        buf.writeInt(color);
        buf.writeVarInt(teams.size());
        for (String team : teams) {
            buf.writeUtf(team);
        }
    }

    public static MapMark read(FriendlyByteBuf buf) {
        MapMark mark = new MapMark(buf.readUtf(), buf.readBlockPos(), buf.readResourceLocation(),
                buf.readUtf(), buf.readInt());

        int count = buf.readVarInt();
        for (int index = 0; index < count; index++) {
            mark.allow(buf.readUtf());
        }
        return mark;
    }
}
