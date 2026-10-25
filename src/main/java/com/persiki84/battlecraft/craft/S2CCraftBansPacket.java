package com.persiki84.battlecraft.craft;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// WHY: клиент держит те же запреты, что и сервер: камнерез и кузнечный стол собирают список
// WHY: результатов на обеих сторонах, а подсказка предмета называет запрет. Пакет несёт и признак
// WHY: идущего матча, потому что фаза запрета без него на клиенте не читается
public final class S2CCraftBansPacket {
    private static final int MAX_BANS = 4096;

    private final List<CraftBan> bans;
    private final boolean matchRunning;

    public S2CCraftBansPacket(List<CraftBan> bans, boolean matchRunning) {
        this.bans = bans;
        this.matchRunning = matchRunning;
    }

    public static void encode(S2CCraftBansPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.matchRunning);
        buffer.writeVarInt(Math.min(packet.bans.size(), MAX_BANS));
        for (int index = 0; index < Math.min(packet.bans.size(), MAX_BANS); index++) {
            CraftBan ban = packet.bans.get(index);
            buffer.writeResourceLocation(BuiltInRegistries.ITEM.getKey(ban.item()));
            buffer.writeNbt(ban.tag());
            buffer.writeByte(ban.phase().ordinal());
        }
    }

    public static S2CCraftBansPacket decode(FriendlyByteBuf buffer) {
        boolean running = buffer.readBoolean();
        int count = Math.min(buffer.readVarInt(), MAX_BANS);
        List<CraftBan> bans = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            ResourceLocation id = buffer.readResourceLocation();
            CompoundTag tag = buffer.readNbt();
            CraftPhase phase = CraftPhase.byOrdinal(buffer.readByte());
            if (BuiltInRegistries.ITEM.containsKey(id)) bans.add(new CraftBan(BuiltInRegistries.ITEM.get(id), tag, phase));
        }
        return new S2CCraftBansPacket(bans, running);
    }

    public static void handle(S2CCraftBansPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            CraftRules.acceptClient(packet.bans, packet.matchRunning);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.persiki84.battlecraft.client.menu.rules.CraftBanToasts.accept(packet.bans));
        });
        context.get().setPacketHandled(true);
    }
}
