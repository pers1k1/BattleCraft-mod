package com.persiki84.capturepoints.network;

import com.persiki84.capturepoints.treasury.TreasuryDesk;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TreasuryTakePacket {
    public static final int REFRESH = -1;

    private final int slot;
    private final ItemStack item;
    private final int count;

    public TreasuryTakePacket(int slot, ItemStack item, int count) {
        this.slot = slot;
        this.item = item;
        this.count = count;
    }

    public static TreasuryTakePacket refresh() {
        return new TreasuryTakePacket(REFRESH, ItemStack.EMPTY, 0);
    }

    public static void encode(TreasuryTakePacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.slot);
        buf.writeItem(packet.item);
        buf.writeVarInt(packet.count);
    }

    public static TreasuryTakePacket decode(FriendlyByteBuf buf) {
        return new TreasuryTakePacket(buf.readVarInt(), buf.readItem(), buf.readVarInt());
    }

    public static void handle(TreasuryTakePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (packet.slot == REFRESH) {
                TreasuryDesk.refresh(player);
            } else {
                TreasuryDesk.take(player, packet.slot, packet.item, packet.count);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
