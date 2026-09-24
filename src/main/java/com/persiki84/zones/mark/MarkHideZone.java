package com.persiki84.zones.mark;

import com.persiki84.shared.zone.ZoneArea;
import com.persiki84.shared.zone.ZoneShape;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public record MarkHideZone(int radius, ZoneShape shape, int height, boolean shown, int color) {
    public static final int OFF = 0;
    public static final int MAX_RADIUS = 512;
    public static final int WHOLE_COLUMN = 0;
    public static final int MAX_HEIGHT = 256;
    public static final int MARK_COLOR = 0;
    private static final int OPAQUE = 0xFF000000;
    public static final MarkHideZone NONE = new MarkHideZone(OFF, ZoneShape.CIRCLE, WHOLE_COLUMN, false, MARK_COLOR);

    private static final int SHAPE_ID_LIMIT = 16;
    private static final double COLUMN_WALL_UP = 8.0;
    private static final double COLUMN_WALL_DOWN = 64.0;

    public MarkHideZone {
        radius = Math.max(OFF, Math.min(MAX_RADIUS, radius));
        shape = shape == null ? ZoneShape.CIRCLE : shape;
        height = Math.max(WHOLE_COLUMN, Math.min(MAX_HEIGHT, height));
        color = color == MARK_COLOR ? MARK_COLOR : color | OPAQUE;
    }

    public boolean active() {
        return radius > OFF;
    }

    public boolean drawn() {
        return shown && active();
    }

    public MarkHideZone withRadius(int value) {
        return new MarkHideZone(value, shape, height, shown, color);
    }

    public MarkHideZone withShape(ZoneShape value) {
        return new MarkHideZone(radius, value, height, shown, color);
    }

    public MarkHideZone withHeight(int value) {
        return new MarkHideZone(radius, shape, value, shown, color);
    }

    public MarkHideZone withShown(boolean value) {
        return new MarkHideZone(radius, shape, height, value, color);
    }

    public MarkHideZone withColor(int value) {
        return new MarkHideZone(radius, shape, height, shown, value);
    }

    public int colorFor(MapMark mark) {
        return color == MARK_COLOR ? mark.color() : color;
    }

    public boolean contains(BlockPos center, double x, double y, double z) {
        if (!active()) return false;
        if (height != WHOLE_COLUMN && Math.abs(y - center.getY()) > height) return false;

        double dx = Math.abs(x - (center.getX() + 0.5));
        double dz = Math.abs(z - (center.getZ() + 0.5));
        double reach = shape == ZoneShape.SQUARE ? Math.max(dx, dz) : Math.sqrt(dx * dx + dz * dz);
        return reach <= radius;
    }

    // WHY: у зоны «во весь столб» нет верха, а стене он нужен: низ уходит под землю и садится
    // WHY: на рельеф при сборке меша, верх стоит на восьми блоках над меткой
    public ZoneArea area(BlockPos center) {
        double up = height == WHOLE_COLUMN ? COLUMN_WALL_UP : height;
        double down = height == WHOLE_COLUMN ? COLUMN_WALL_DOWN : height;
        return new ZoneArea(shape, center, radius, up, down);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(radius);
        buf.writeUtf(shape.id(), SHAPE_ID_LIMIT);
        buf.writeVarInt(height);
        buf.writeBoolean(shown);
        buf.writeInt(color);
    }

    public static MarkHideZone read(FriendlyByteBuf buf) {
        int radius = buf.readVarInt();
        ZoneShape shape = ZoneShape.byId(buf.readUtf(SHAPE_ID_LIMIT));
        int height = buf.readVarInt();
        return new MarkHideZone(radius, shape, height, buf.readBoolean(), buf.readInt());
    }
}
