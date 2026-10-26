package com.persiki84.battlecraft.bases;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// WHY: игроку нужен список баз своей команды для экрана смерти, перелёта и магазина на точке:
// WHY: сервер шлёт его готовым, клиент ничего не решает сам, сервер всё равно проверяет каждую заявку
public final class S2CBasesPacket {
    private static final int MAX_BASES = 64;
    private static final int ID_LIMIT = 160;

    public record Entry(String id, Component name, boolean home) {}

    public final List<Entry> bases;
    public final boolean respawn;
    public final boolean travel;
    public final boolean shop;

    public S2CBasesPacket(List<Entry> bases, boolean respawn, boolean travel, boolean shop) {
        this.bases = bases;
        this.respawn = respawn;
        this.travel = travel;
        this.shop = shop;
    }

    public static void encode(S2CBasesPacket packet, FriendlyByteBuf buffer) {
        int count = Math.min(packet.bases.size(), MAX_BASES);
        buffer.writeVarInt(count);
        for (int index = 0; index < count; index++) {
            Entry entry = packet.bases.get(index);
            buffer.writeUtf(entry.id(), ID_LIMIT);
            buffer.writeComponent(entry.name());
            buffer.writeBoolean(entry.home());
        }
        buffer.writeBoolean(packet.respawn);
        buffer.writeBoolean(packet.travel);
        buffer.writeBoolean(packet.shop);
    }

    public static S2CBasesPacket decode(FriendlyByteBuf buffer) {
        int count = Math.min(Math.max(buffer.readVarInt(), 0), MAX_BASES);
        List<Entry> bases = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            bases.add(new Entry(buffer.readUtf(ID_LIMIT), buffer.readComponent(), buffer.readBoolean()));
        }
        return new S2CBasesPacket(bases, buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
    }

    public static void handle(S2CBasesPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.battlecraft.client.bases.ClientBases.accept(packet)));
        context.get().setPacketHandled(true);
    }
}
