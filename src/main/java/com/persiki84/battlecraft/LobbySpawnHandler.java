package com.persiki84.battlecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID)
public final class LobbySpawnHandler {

    private LobbySpawnHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BattleCraftManager manager = BattleCraftManager.getInstance();
        if (manager == null || manager.isSoftDisabled()) return;
        if (manager.getPhase() != BattleCraftManager.GamePhase.LOBBY) return;

        BattleCraftConfig config = manager.getConfig();
        if (config == null || !config.lobbySet) return;

        BlockPos spawn = config.getLobbySpawn();
        player.teleportTo(player.serverLevel(), spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                player.getYRot(), player.getXRot());
    }
}
