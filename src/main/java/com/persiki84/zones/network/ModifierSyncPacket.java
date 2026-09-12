package com.persiki84.zones.network;

import com.persiki84.zones.client.ClientModifierData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ModifierSyncPacket {
    private final List<String> potionEntries;
    private final List<String> attributeEntries;

    public ModifierSyncPacket(List<String> potionEntries, List<String> attributeEntries) {
        this.potionEntries = potionEntries;
        this.attributeEntries = attributeEntries;
    }

    public List<String> potionEntries() { return potionEntries; }
    public List<String> attributeEntries() { return attributeEntries; }

    public static void encode(ModifierSyncPacket packet, FriendlyByteBuf buf) {
        writeAll(buf, packet.potionEntries);
        writeAll(buf, packet.attributeEntries);
    }

    public static ModifierSyncPacket decode(FriendlyByteBuf buf) {
        return new ModifierSyncPacket(readAll(buf), readAll(buf));
    }

    public static void handle(ModifierSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientModifierData.accept(packet));
        ctx.get().setPacketHandled(true);
    }

    private static void writeAll(FriendlyByteBuf buf, List<String> entries) {
        buf.writeInt(entries.size());
        for (String entry : entries) {
            buf.writeUtf(entry);
        }
    }

    private static List<String> readAll(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<String> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(buf.readUtf());
        }
        return entries;
    }
}
