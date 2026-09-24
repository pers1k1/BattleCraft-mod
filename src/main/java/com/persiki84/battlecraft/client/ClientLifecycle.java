package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.client.hud.AnnounceHud;
import com.persiki84.battlecraft.client.hud.RadioHud;
import com.persiki84.battlecraft.client.island.IslandModel;
import com.persiki84.battlecraft.client.menu.MenuPresenceClient;
import com.persiki84.battlecraft.client.media.MediaWatch;
import com.persiki84.battlecraft.client.voice.RadioChirp;
import com.persiki84.battlecraft.client.voice.RadioTalk;
import com.persiki84.shared.client.menu.MenuData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class ClientLifecycle {

    private ClientLifecycle() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientGameData.forget();
        ClientGameRules.forget();
        ClientMarkerRanges.forget();
        ConfigGuard.forget();
        ServerLocks.release();
        ClientModules.forget();
        ClientCombatState.forget();
        ClientLobbyData.clear();
        MenuData.forget();
        MenuPresenceClient.forget();
        IslandModel.forget();
        AnnounceHud.forget();
        RadioHud.forget();
        RadioChirp.forget();
        RadioTalk.forget();
    }

    @SubscribeEvent
    public static void onGameShuttingDown(GameShuttingDownEvent event) {
        DiscordRpcManager.getInstance().shutdown();
        MediaWatch.want(false);
    }
}
