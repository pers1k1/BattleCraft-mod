package com.persiki84.zones;

import com.persiki84.shared.zone.ZoneArea;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public final class Zone {
    public static final int TEAM_COLOR = 0;

    private final String id;
    private final ZoneSource source;
    private ZoneArea area;
    private ZoneType type;
    private String ownerTeam;
    private int color;
    private BlockPos spawnAnchor;
    private ZoneRules rules = new ZoneRules();

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
    public void setColor(int color) { this.color = color; }

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
        return zone;
    }
}
