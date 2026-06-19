package com.persiki84.battlecraft;

import com.mojang.logging.LogUtils;
import com.persiki84.battlecraft.client.ClientEventHandler;
import com.persiki84.battlecraft.client.ClientOverlayRenderer;
import com.persiki84.battlecraft.client.DiscordRpcManager;
import com.persiki84.battlecraft.client.KeyInputHandler;
import com.persiki84.battlecraft.network.PacketHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(BattleCraftMod.MOD_ID)
public class BattleCraftMod {
    public static final String MOD_ID = "battlecraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BattleCraftMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        MinecraftForge.EVENT_BUS.register(this);
        BattleCraftManager.getInstance();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        PacketHandler.register();
        LOGGER.info("BattleCraft Core Mod initialized!");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("BattleCraft Client setup complete");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BattleCraftCommands.register(event.getDispatcher());
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("battlecraft_hud", ClientOverlayRenderer.HUD_OVERLAY);
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            MinecraftForge.EVENT_BUS.register(new ClientEventHandler());
            MinecraftForge.EVENT_BUS.register(new KeyInputHandler());
            DiscordRpcManager.getInstance().init();
        }
    }
}
