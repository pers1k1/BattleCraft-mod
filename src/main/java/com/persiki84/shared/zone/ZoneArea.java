package com.persiki84.shared.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.AABB;

public final class ZoneArea {
    public static final double DEFAULT_HEIGHT = 5.0;

    private final ZoneShape shape;
    private final BlockPos center;
    private final double size;
    private final double heightUp;
    private final double heightDown;
    private AABB bounds;

    public ZoneArea(ZoneShape shape, BlockPos center, double size, double heightUp, double heightDown) {
        this.shape = shape;
        this.center = center;
        this.size = Math.max(0.0, size);
        this.heightUp = Math.max(0.0, heightUp);
        this.heightDown = Math.max(0.0, heightDown);
    }

    public ZoneShape shape() { return shape; }
    public BlockPos center() { return center; }
    public double size() { return size; }
    public double heightUp() { return heightUp; }
    public double heightDown() { return heightDown; }

    public double centerX() { return center.getX() + 0.5; }
    public double centerZ() { return center.getZ() + 0.5; }
    public double minY() { return center.getY() - heightDown; }
    public double maxY() { return center.getY() + heightUp; }

    public ZoneArea withShape(ZoneShape newShape) {
        return new ZoneArea(newShape, center, size, heightUp, heightDown);
    }

    public ZoneArea withCenter(BlockPos newCenter) {
        return new ZoneArea(shape, newCenter, size, heightUp, heightDown);
    }

    public ZoneArea withSize(double newSize) {
        return new ZoneArea(shape, center, newSize, heightUp, heightDown);
    }

    public ZoneArea withHeight(double newHeightUp, double newHeightDown) {
        return new ZoneArea(shape, center, size, newHeightUp, newHeightDown);
    }

    public boolean contains(double x, double y, double z) {
        return contains(x, y, z, 0.0);
    }

    public boolean contains(double x, double y, double z, double margin) {
        if (y < minY() - margin || y > maxY() + margin) return false;
        return containsHorizontally(x, z, margin);
    }

    public boolean containsHorizontally(double x, double z) {
        return containsHorizontally(x, z, 0.0);
    }

    public boolean containsHorizontally(double x, double z, double margin) {
        return horizontalDistanceTo(x, z) <= size + margin;
    }

    public double horizontalDistanceTo(double x, double z) {
        double dx = Math.abs(x - centerX());
        double dz = Math.abs(z - centerZ());
        if (shape == ZoneShape.SQUARE) return Math.max(dx, dz);
        return Math.sqrt(dx * dx + dz * dz);
    }

    public AABB bounds() {
        if (bounds == null) {
            bounds = new AABB(centerX() - size, minY(), centerZ() - size, centerX() + size, maxY(), centerZ() + size);
        }
        return bounds;
    }

    public double footprint() {
        if (shape == ZoneShape.SQUARE) return 4.0 * size * size;
        return Math.PI * size * size;
    }

    public double perimeter() {
        if (shape == ZoneShape.SQUARE) return 8.0 * size;
        return 2.0 * Math.PI * size;
    }

    public double perimeterX(double progress) {
        if (shape == ZoneShape.SQUARE) return centerX() + squareOffsetX(progress);
        return centerX() + size * Math.cos(2.0 * Math.PI * progress);
    }

    public double perimeterZ(double progress) {
        if (shape == ZoneShape.SQUARE) return centerZ() + squareOffsetZ(progress);
        return centerZ() + size * Math.sin(2.0 * Math.PI * progress);
    }

    private double squareOffsetX(double progress) {
        int side = sideOf(progress);
        double along = alongSide(progress);
        if (side == 0) return along;
        if (side == 1) return size;
        if (side == 2) return -along;
        return -size;
    }

    private double squareOffsetZ(double progress) {
        int side = sideOf(progress);
        double along = alongSide(progress);
        if (side == 0) return -size;
        if (side == 1) return along;
        if (side == 2) return size;
        return -along;
    }

    private static int sideOf(double progress) {
        double wrapped = progress - Math.floor(progress);
        return Math.min(3, (int) (wrapped * 4.0));
    }

    private double alongSide(double progress) {
        double wrapped = progress - Math.floor(progress);
        double local = wrapped * 4.0 - sideOf(progress);
        return -size + 2.0 * size * local;
    }

    public void saveInto(CompoundTag tag) {
        tag.putString("shape", shape.id());
        tag.putInt("x", center.getX());
        tag.putInt("y", center.getY());
        tag.putInt("z", center.getZ());
        tag.putDouble("size", size);
        tag.putDouble("heightUp", heightUp);
        tag.putDouble("heightDown", heightDown);
    }

    public static ZoneArea loadFrom(CompoundTag tag) {
        ZoneShape shape = tag.contains("shape") ? ZoneShape.byId(tag.getString("shape")) : null;
        BlockPos center = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
        double size = tag.contains("size") ? tag.getDouble("size") : tag.getInt("radius");
        double up = tag.contains("heightUp") ? tag.getDouble("heightUp") : DEFAULT_HEIGHT;
        double down = tag.contains("heightDown") ? tag.getDouble("heightDown") : DEFAULT_HEIGHT;
        return new ZoneArea(shape == null ? ZoneShape.CIRCLE : shape, center, size, up, down);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(shape);
        buf.writeBlockPos(center);
        buf.writeDouble(size);
        buf.writeDouble(heightUp);
        buf.writeDouble(heightDown);
    }

    public static ZoneArea read(FriendlyByteBuf buf) {
        ZoneShape shape = buf.readEnum(ZoneShape.class);
        BlockPos center = buf.readBlockPos();
        double size = buf.readDouble();
        double up = buf.readDouble();
        double down = buf.readDouble();
        return new ZoneArea(shape, center, size, up, down);
    }
}
