package com.persiki84.zones.mark;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.scores.Team;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class MapMark {
    public static final int DEFAULT_COLOR = 0xFFE7E9F4;
    public static final int MAX_LINES = 6;
    public static final int LINE_LIMIT = 64;
    public static final int SCALE_FULL = 100;
    public static final int SCALE_MIN = 25;
    public static final int SCALE_MAX = 400;

    private final String id;
    private BlockPos position;
    private ResourceLocation dimension;
    private final List<String> lines = new ArrayList<>();
    private int color;
    private boolean inWorld = true;
    private MarkKind kind = MarkKind.DEFAULT;
    private int scalePercent = SCALE_FULL;
    private final Set<String> teams = new LinkedHashSet<>();

    public MapMark(String id, BlockPos position, ResourceLocation dimension, String label, int color) {
        this.id = id;
        this.position = position;
        this.dimension = dimension;
        this.color = color;
        setLabel(label);
    }

    public String id() { return id; }
    public BlockPos position() { return position; }
    public ResourceLocation dimension() { return dimension; }
    public int color() { return color; }
    public boolean inWorld() { return inWorld; }
    public MarkKind kind() { return kind; }
    public int scalePercent() { return scalePercent; }
    public Set<String> teams() { return teams; }

    // WHY: подпись это первая строка надписи: у метки-точки она одна, и весь прежний код,
    // WHY: спрашивающий label(), обязан продолжать получать ровно её
    public String label() {
        String first = lines.isEmpty() ? null : lines.get(0);
        return first == null || first.isEmpty() ? id : first;
    }

    public List<String> lines() {
        return lines;
    }

    public void setPosition(BlockPos value) { this.position = value; }
    public void setDimension(ResourceLocation value) { this.dimension = value; }
    public void setColor(int value) { this.color = value; }
    public void setInWorld(boolean value) { this.inWorld = value; }

    public void setScalePercent(int value) {
        this.scalePercent = Math.max(SCALE_MIN, Math.min(SCALE_MAX, value));
    }

    // WHY: надпись на карте живёт своим текстом и по умолчанию не лезет в мир: точка над
    // WHY: местностью читалась бы как обычная метка, а её здесь нет
    public void setKind(MarkKind value) {
        this.kind = value == null ? MarkKind.DEFAULT : value;
    }

    public void setLabel(String value) {
        if (lines.isEmpty()) {
            lines.add(clip(value));
            return;
        }
        lines.set(0, clip(value));
    }

    public boolean addLine(String value) {
        if (lines.size() >= MAX_LINES) return false;

        lines.add(clip(value));
        return true;
    }

    public boolean setLine(int index, String value) {
        if (index < 0 || index >= lines.size()) return false;

        lines.set(index, clip(value));
        return true;
    }

    public boolean removeLine(int index) {
        if (index <= 0 || index >= lines.size()) return false;

        lines.remove(index);
        return true;
    }

    public void restoreLines(List<String> stored) {
        lines.clear();
        if (stored == null || stored.isEmpty()) {
            lines.add("");
            return;
        }
        for (String line : stored) {
            if (lines.size() >= MAX_LINES) break;
            lines.add(clip(line));
        }
    }

    private static String clip(String value) {
        if (value == null) return "";
        return value.length() > LINE_LIMIT ? value.substring(0, LINE_LIMIT) : value;
    }

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
        buf.writeVarInt(lines.size());
        for (String line : lines) {
            buf.writeUtf(line);
        }
        buf.writeInt(color);
        buf.writeBoolean(inWorld);
        buf.writeUtf(kind.id());
        buf.writeVarInt(scalePercent);
        buf.writeVarInt(teams.size());
        for (String team : teams) {
            buf.writeUtf(team);
        }
    }

    public static MapMark read(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        BlockPos position = buf.readBlockPos();
        ResourceLocation dimension = buf.readResourceLocation();

        List<String> lines = new ArrayList<>();
        int lineCount = Math.min(buf.readVarInt(), MAX_LINES);
        for (int index = 0; index < lineCount; index++) {
            lines.add(buf.readUtf(LINE_LIMIT));
        }

        MapMark mark = new MapMark(id, position, dimension, null, buf.readInt());
        mark.restoreLines(lines);
        mark.setInWorld(buf.readBoolean());
        mark.setKind(MarkKind.byId(buf.readUtf()));
        mark.setScalePercent(buf.readVarInt());

        int count = buf.readVarInt();
        for (int index = 0; index < count; index++) {
            mark.allow(buf.readUtf());
        }
        return mark;
    }
}
