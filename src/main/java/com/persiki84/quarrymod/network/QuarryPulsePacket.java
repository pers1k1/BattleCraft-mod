package com.persiki84.quarrymod.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class QuarryPulsePacket {
    public static final byte BROKEN = 0;
    public static final byte REGENERATED = 1;
    public static final byte REFUSED = 2;

    public final long position;
    public final byte kind;
    public final String ore;
    public final int multiplier;
    public final int seconds;

    public QuarryPulsePacket(BlockPos pos, byte kind, String ore, int multiplier, int seconds) {
        this.position = pos.asLong();
        this.kind = kind;
        this.ore = ore;
        this.multiplier = multiplier;
        this.seconds = seconds;
    }

    private QuarryPulsePacket(long position, byte kind, String ore, int multiplier, int seconds) {
        this.position = position;
        this.kind = kind;
        this.ore = ore;
        this.multiplier = multiplier;
        this.seconds = seconds;
    }

    public static void encode(QuarryPulsePacket packet, FriendlyByteBuf buffer) {
        buffer.writeLong(packet.position);
        buffer.writeByte(packet.kind);
        buffer.writeUtf(packet.ore);
        buffer.writeVarInt(packet.multiplier);
        buffer.writeVarInt(packet.seconds);
    }

    public static QuarryPulsePacket decode(FriendlyByteBuf buffer) {
        return new QuarryPulsePacket(buffer.readLong(), buffer.readByte(), buffer.readUtf(),
                buffer.readVarInt(), buffer.readVarInt());
    }

    public BlockPos position() {
        return BlockPos.of(position);
    }

    public static void handle(QuarryPulsePacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.quarrymod.client.ClientQuarryField.pulse(packet)));
        context.get().setPacketHandled(true);
    }
}
