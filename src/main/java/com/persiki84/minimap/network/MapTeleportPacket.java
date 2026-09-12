package com.persiki84.minimap.network;

import com.persiki84.shared.ActionGate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class MapTeleportPacket {
    private static final int PERMISSION_LEVEL = 2;
    private static final String GATE = "minimapTeleport";
    private static final int GATE_TICKS = 5;

    private final double x;
    private final double z;

    public MapTeleportPacket(double x, double z) {
        this.x = x;
        this.z = z;
    }

    public static void encode(MapTeleportPacket packet, FriendlyByteBuf buffer) {
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.z);
    }

    public static MapTeleportPacket decode(FriendlyByteBuf buffer) {
        return new MapTeleportPacket(buffer.readDouble(), buffer.readDouble());
    }

    public static void handle(MapTeleportPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> teleport(context.get().getSender(), packet.x, packet.z));
        context.get().setPacketHandled(true);
    }

    private static void teleport(ServerPlayer player, double x, double z) {
        if (player == null || !player.hasPermissions(PERMISSION_LEVEL)) return;
        if (!Double.isFinite(x) || !Double.isFinite(z)) return;
        if (!ActionGate.allow(player, GATE, GATE_TICKS)) return;

        ServerLevel level = player.serverLevel();
        BlockPos target = BlockPos.containing(x, 0.0, z);
        if (!Level.isInSpawnableBounds(target) || !level.getWorldBorder().isWithinBounds(x, z)) return;

        level.getChunkAt(target);
        double surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                target.getX(), target.getZ());
        double targetY = Math.max(player.getY(), surfaceY);
        player.teleportTo(level, target.getX() + 0.5, targetY, target.getZ() + 0.5,
                player.getYRot(), player.getXRot());
    }
}
