package com.persiki84.zones;

import com.persiki84.battlecraft.rules.MarkerRange;
import com.persiki84.shared.zone.ZoneArea;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class Zone {
    public static final int TEAM_COLOR = 0;
    private static final int OPAQUE = 0xFF000000;
    public static final int KIND_RANGE = 0;

    private final String id;
    private final ZoneSource source;
    private ZoneArea area;
    private ZoneType type;
    private String ownerTeam;
    private int color;
    private BlockPos spawnAnchor;
    private ZoneRules rules = new ZoneRules();
    private ResourceLocation dimension;
    private int markerRange = KIND_RANGE;
    private boolean hiddenInside = true;
    private String cacheKey;

    public Zone(String id, ZoneArea area, ZoneType type, String ownerTeam, int color, ZoneSource source) {
        this.id = id;
        this.area = area;
        this.type = type;
        this.ownerTeam = ownerTeam;
        this.color = color;
        this.source = source;
    }

    public String id() { return id; }
    public ZoneArea area() { return area; }
    public ZoneType type() { return type; }
    public String ownerTeam() { return ownerTeam; }
    public int color() { return color; }
    public ZoneSource source() { return source; }

    public void setArea(ZoneArea area) { this.area = area; }
    public void setType(ZoneType type) { this.type = type; }
    public void setOwnerTeam(String ownerTeam) { this.ownerTeam = ownerTeam; }
    // WHY: ноль занят цветом команды, поэтому чёрный из палитры обязан нести альфу, иначе он
    // WHY: молча превращался бы в цвет владельца
    public void setColor(int color) { this.color = color == TEAM_COLOR ? TEAM_COLOR : color | OPAQUE; }

    public ResourceLocation dimension() { return dimension; }
    public void setDimension(ResourceLocation value) { this.dimension = value; }

    public int markerRange() { return markerRange; }
    public void setMarkerRange(int blocks) { this.markerRange = MarkerRange.normalize(blocks); }

    public boolean hiddenInside() { return hiddenInside; }
    public void setHiddenInside(boolean value) { this.hiddenInside = value; }

    // WHY: имя точки захвата и id зоны живут в разных пространствах и могут совпасть, а меш
    // WHY: и цвет на клиенте кешируются по ключу: общий ключ пересобирал бы меш каждый кадр
    public String cacheKey() { return cacheKey == null ? id : cacheKey; }
    public void setCacheKey(String value) { this.cacheKey = value; }

    // WHY: зона без записанного мира это наследство старого формата, и она работает везде,
    // WHY: как работала до того, как у зон появилось измерение
    public boolean inDimension(ResourceLocation here) {
        return dimension == null || here == null || dimension.equals(here);
    }

    public boolean inLevel(Level level) {
        return level == null || inDimension(level.dimension().location());
    }

    public BlockPos spawnAnchor() { return spawnAnchor; }
    public void setSpawnAnchor(BlockPos anchor) { this.spawnAnchor = anchor; }
    public boolean spawnsAtAnchor() { return spawnAnchor != null; }

    public ZoneRules rules() { return rules; }
    public void setRules(ZoneRules rules) { this.rules = rules; }

    public boolean allows(ZoneRule rule) {
        return rules.allows(rule, type);
    }

    public boolean hasCustomColor() {
        return color != TEAM_COLOR;
    }

    public boolean belongsTo(String teamName) {
        return ownerTeam != null && ownerTeam.equals(teamName);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        area.write(buf);
        buf.writeEnum(type);
        buf.writeUtf(ownerTeam != null ? ownerTeam : "");
        buf.writeInt(color);
        buf.writeEnum(source);
        buf.writeBoolean(spawnAnchor != null);
        if (spawnAnchor != null) buf.writeBlockPos(spawnAnchor);
        rules.write(buf);
        buf.writeBoolean(dimension != null);
        if (dimension != null) buf.writeResourceLocation(dimension);
        buf.writeVarInt(markerRange);
        buf.writeBoolean(hiddenInside);
    }

    public static Zone read(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        ZoneArea area = ZoneArea.read(buf);
        ZoneType type = buf.readEnum(ZoneType.class);
        String ownerTeam = buf.readUtf();
        int color = buf.readInt();
        ZoneSource source = buf.readEnum(ZoneSource.class);
        Zone zone = new Zone(id, area, type, ownerTeam.isEmpty() ? null : ownerTeam, color, source);
        if (buf.readBoolean()) zone.setSpawnAnchor(buf.readBlockPos());
        zone.setRules(ZoneRules.read(buf));
        if (buf.readBoolean()) zone.setDimension(buf.readResourceLocation());
        zone.setMarkerRange(buf.readVarInt());
        zone.setHiddenInside(buf.readBoolean());
        return zone;
    }
}
