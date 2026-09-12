package com.persiki84.minimap.network;

import com.persiki84.shared.ActionGate;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class MapMarkerUpdatePacket {
    private static final String MARK_KEY = "mapMarkTick";
    private static final int MARK_INTERVAL_TICKS = 5;
    private static final double WORLD_LIMIT = 3.0e7;

    public final double x;
    public final double z;
    public final boolean remove;
    public final boolean isTeam;

    public MapMarkerUpdatePacket(double x, double z, boolean remove, boolean isTeam) {
        this.x = x;
        this.z = z;
        this.remove = remove;
        this.isTeam = isTeam;
    }

    public static void encode(MapMarkerUpdatePacket msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.z);
        buf.writeBoolean(msg.remove);
        buf.writeBoolean(msg.isTeam);
    }

    public static MapMarkerUpdatePacket decode(FriendlyByteBuf buf) {
        return new MapMarkerUpdatePacket(buf.readDouble(), buf.readDouble(), buf.readBoolean(), buf.readBoolean());
    }

    // WHY: метка рассылается всей команде, а частота заявок принадлежит клиенту: без счёта по
    // WHY: тику и проверки координат один игрок гонит рассылку на каждый свой пакет
    public static void handle(MapMarkerUpdatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            net.minecraft.server.level.ServerPlayer sender = ctx.get().getSender();
            if (sender == null || !finite(msg.x) || !finite(msg.z)) return;
            if (!ActionGate.allow(sender, MARK_KEY, MARK_INTERVAL_TICKS)) return;

            com.persiki84.minimap.MapManager.handleMarkerUpdate(sender, msg.x, msg.z, msg.remove, msg.isTeam);
        });
        ctx.get().setPacketHandled(true);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && Math.abs(value) <= WORLD_LIMIT;
    }
}
