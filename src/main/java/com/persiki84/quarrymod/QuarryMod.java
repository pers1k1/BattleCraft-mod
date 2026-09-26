package com.persiki84.quarrymod;

import com.mojang.logging.LogUtils;
import com.persiki84.quarrymod.block.QuarryBlocks;
import com.persiki84.quarrymod.commands.QuarryCommands;
import com.persiki84.quarrymod.network.PacketHandler;
import com.persiki84.quarrymod.network.QuarryBroadcast;
import com.persiki84.quarrymod.data.QuarryBlockManager;
import com.persiki84.quarrymod.data.QuarryDataManager;
import com.persiki84.quarrymod.events.QuarryEventHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import net.minecraft.server.MinecraftServer;
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
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final int SAVE_TICKS = 20;
    private static QuarryMod instance;
    private QuarryDataManager dataManager;

    public QuarryMod() {
        instance = this;
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        QuarryBlocks.register(modEventBus);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new QuarryEventHandler());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(PacketHandler::register);
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
            dataManager.saveIfDirty();
        }
        QuarryBroadcast.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || dataManager == null) return;

        tickQuarry(event.getServer());
        if (event.getServer().getTickCount() % SAVE_TICKS == 0) dataManager.saveIfDirty();
    }

    private void tickQuarry(MinecraftServer server) {
        QuarryBlockManager manager = dataManager.getBlockManager();
        if (!ModuleSwitches.allows(ModuleId.QUARRY)) {
            manager.restoreAllPending(server);
            return;
        }
        manager.tickRegenerations(server);
        QuarryBroadcast.tick(server, manager);
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
