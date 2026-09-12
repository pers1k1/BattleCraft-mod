package com.persiki84.battlecraft.modules;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.network.PacketHandler;
import com.persiki84.battlecraft.network.S2CModulesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID)
public final class ModuleEvents {

    private ModuleEvents() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ModuleSwitches.load();
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), S2CModulesPacket.current());
    }

    public static void syncToAll(MinecraftServer server) {
        if (server == null) return;
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), S2CModulesPacket.current());
    }
}
