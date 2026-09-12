package com.persiki84.battlecraft.network;

import com.persiki84.battlecraft.rules.ConfigManifest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class S2CConfigWatchPacket {
    public final List<String> paths;

    public S2CConfigWatchPacket(List<String> paths) {
        this.paths = paths;
    }

    public static void encode(S2CConfigWatchPacket packet, FriendlyByteBuf buffer) {
        int count = Math.min(packet.paths.size(), ConfigManifest.MAX_FILES);
        buffer.writeVarInt(count);
        for (int index = 0; index < count; index++) {
            buffer.writeUtf(packet.paths.get(index), ConfigManifest.MAX_PATH);
        }
    }

    public static S2CConfigWatchPacket decode(FriendlyByteBuf buffer) {
        int count = Math.min(buffer.readVarInt(), ConfigManifest.MAX_FILES);
        List<String> paths = new ArrayList<>(Math.max(count, 0));
        for (int index = 0; index < count; index++) {
            paths.add(buffer.readUtf(ConfigManifest.MAX_PATH));
        }
        return new S2CConfigWatchPacket(paths);
    }

    public static void handle(S2CConfigWatchPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.battlecraft.client.ConfigGuard.check(packet.paths)));
        ctx.get().setPacketHandled(true);
    }
}
