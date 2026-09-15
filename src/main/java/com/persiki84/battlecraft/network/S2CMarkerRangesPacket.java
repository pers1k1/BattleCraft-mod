package com.persiki84.battlecraft.network;

import com.persiki84.battlecraft.rules.MarkerRange;
import com.persiki84.battlecraft.rules.MarkerRanges;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CMarkerRangesPacket {
    public final int[] blocks;

    public S2CMarkerRangesPacket(int[] blocks) {
        this.blocks = blocks;
    }

    public static S2CMarkerRangesPacket current() {
        MarkerRange[] kinds = MarkerRange.values();
        int[] blocks = new int[kinds.length];
        for (int index = 0; index < kinds.length; index++) {
            blocks[index] = MarkerRanges.blocks(kinds[index]);
        }
        return new S2CMarkerRangesPacket(blocks);
    }

    public static void encode(S2CMarkerRangesPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.blocks.length);
        for (int value : packet.blocks) {
            buffer.writeVarInt(value);
        }
    }

    // WHY: длина пишется в поток, а не берётся из перечисления: сервер другой сборки знает
    // WHY: другое число видов меток, и чтение по своему счётчику разъехалось бы с пакетом
    public static S2CMarkerRangesPacket decode(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        int[] blocks = new int[count];
        for (int index = 0; index < count; index++) {
            blocks[index] = buffer.readVarInt();
        }
        return new S2CMarkerRangesPacket(blocks);
    }

    public static void handle(S2CMarkerRangesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.battlecraft.client.ClientMarkerRanges.accept(packet.blocks)));
        ctx.get().setPacketHandled(true);
    }
}
