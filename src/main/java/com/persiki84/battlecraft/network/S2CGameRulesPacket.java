package com.persiki84.battlecraft.network;

import com.persiki84.battlecraft.BattleCraftManager;
import com.persiki84.battlecraft.rules.GameRule;
import com.persiki84.battlecraft.rules.GameRules;
import com.persiki84.battlecraft.rules.StaminaCosts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CGameRulesPacket {
    public final int allowed;
    public final StaminaCosts stamina;

    public S2CGameRulesPacket(int allowed, StaminaCosts stamina) {
        this.allowed = allowed;
        this.stamina = stamina;
    }

    public static S2CGameRulesPacket current() {
        int mask = 0;
        for (GameRule rule : GameRule.values()) {
            if (GameRules.allows(rule)) mask |= 1 << rule.ordinal();
        }
        return new S2CGameRulesPacket(mask, StaminaCosts.of(BattleCraftManager.getInstance().getConfig()));
    }

    public static void encode(S2CGameRulesPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.allowed);
        buffer.writeVarInt(packet.stamina.sprintDashPercent());
        buffer.writeVarInt(packet.stamina.armedSprintDashPercent());
        buffer.writeVarInt(packet.stamina.jumpPermille());
        buffer.writeVarInt(packet.stamina.armedJumpPermille());
    }

    public static S2CGameRulesPacket decode(FriendlyByteBuf buffer) {
        int allowed = buffer.readVarInt();
        return new S2CGameRulesPacket(allowed, new StaminaCosts(buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt()));
    }

    public static void handle(S2CGameRulesPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.persiki84.battlecraft.client.ClientGameRules.accept(packet.allowed, packet.stamina)));
        ctx.get().setPacketHandled(true);
    }
}
