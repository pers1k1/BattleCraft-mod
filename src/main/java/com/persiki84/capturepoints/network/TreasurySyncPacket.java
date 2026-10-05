package com.persiki84.capturepoints.network;

import com.persiki84.capturepoints.client.ClientTreasury;
import com.persiki84.capturepoints.treasury.TeamTreasury;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class TreasurySyncPacket {
    private final boolean enabled;
    private final List<ItemStack> items;

    public TreasurySyncPacket(boolean enabled, List<ItemStack> items) {
        this.enabled = enabled;
        this.items = items;
    }

    public static void encode(TreasurySyncPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.enabled);
        buf.writeVarInt(packet.items.size());
        for (ItemStack stack : packet.items) {
            buf.writeItem(stack);
        }
    }

    public static TreasurySyncPacket decode(FriendlyByteBuf buf) {
        boolean enabled = buf.readBoolean();
        int count = Math.min(buf.readVarInt(), TeamTreasury.SLOTS);
        List<ItemStack> items = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            items.add(buf.readItem());
        }
        return new TreasurySyncPacket(enabled, items);
    }

    public static void handle(TreasurySyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientTreasury.accept(packet.enabled, packet.items));
        ctx.get().setPacketHandled(true);
    }
}
