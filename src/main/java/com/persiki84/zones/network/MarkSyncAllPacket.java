package com.persiki84.zones.network;

import com.persiki84.zones.client.ClientMarkData;
import com.persiki84.zones.mark.MapMark;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class MarkSyncAllPacket {
    private final List<MapMark> marks;

    public MarkSyncAllPacket(Collection<MapMark> marks) {
        this.marks = new ArrayList<>(marks);
    }

    public static void encode(MarkSyncAllPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.marks.size());
        for (MapMark mark : packet.marks) {
            mark.write(buf);
        }
    }

    public static MarkSyncAllPacket decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<MapMark> marks = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            marks.add(MapMark.read(buf));
        }
        return new MarkSyncAllPacket(marks);
    }

    public static void handle(MarkSyncAllPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientMarkData.replaceAll(packet.marks)));
        context.get().setPacketHandled(true);
    }
}
