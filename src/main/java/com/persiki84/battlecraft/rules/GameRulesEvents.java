package com.persiki84.battlecraft.rules;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.network.PacketHandler;
import com.persiki84.battlecraft.network.S2CGameRulesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID)
public final class GameRulesEvents {

    private GameRulesEvents() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        GameRules.load();
        ConfigManifest.load(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), S2CGameRulesPacket.current());
        ClientAudit.expect(player);
        ConfigAudit.expect(player);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ClientAudit.forget(event.getEntity().getUUID());
        ConfigAudit.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        ClientAudit.tick(event.getServer());
        ConfigAudit.tick(event.getServer());
    }

    public static void syncToAll(MinecraftServer server) {
        if (server == null) return;
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), S2CGameRulesPacket.current());
        ClientAudit.expectAll(server);
        ConfigAudit.expectAll(server);
    }
}
