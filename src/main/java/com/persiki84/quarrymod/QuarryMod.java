package com.persiki84.quarrymod;

import com.mojang.logging.LogUtils;
import com.persiki84.quarrymod.commands.QuarryCommands;
import com.persiki84.quarrymod.data.QuarryDataManager;
import com.persiki84.quarrymod.events.QuarryEventHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(QuarryMod.MODID)
public class QuarryMod {
    public static final String MODID = "quarrymod";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static QuarryMod instance;
    private QuarryDataManager dataManager;

    public QuarryMod() {
        instance = this;
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new QuarryEventHandler());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("QuarryMod loading!");
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        dataManager = new QuarryDataManager(event.getServer());
        dataManager.load();
        dataManager.getBlockManager().tickRegenerations(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (dataManager != null) {
            dataManager.save();
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (dataManager != null) {
            dataManager.getBlockManager().tickRegenerations(event.getServer());
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        QuarryCommands.register(event.getDispatcher());
    }

    public static QuarryMod getInstance() {
        return instance;
    }

    public QuarryDataManager getDataManager() {
        return dataManager;
    }
}
