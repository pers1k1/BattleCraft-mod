package com.persiki84.immortality;

import com.mojang.logging.LogUtils;
import com.persiki84.immortality.commands.ImmortalityCommand;
import com.persiki84.immortality.event.ImmortalityHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(ImmortalityMod.MOD_ID)
public class ImmortalityMod {
    public static final String MOD_ID = "immortality";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ImmortalityMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("Immortality Mod initialized!");
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        ImmortalityHandler.setServer(event.getServer());
        LOGGER.info("Immortality config loaded");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ImmortalityHandler.save();
        LOGGER.info("Immortality config saved");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ImmortalityCommand.register(event.getDispatcher());
    }
}
