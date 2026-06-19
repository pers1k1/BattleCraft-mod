package com.persiki84.airdrop;

import com.persiki84.airdrop.command.AirDropCommands;
import com.persiki84.airdrop.config.AirDropConfig;
import com.persiki84.airdrop.entity.ModEntities;
import com.persiki84.airdrop.loot.AirDropLootManager;
import com.persiki84.airdrop.scheduler.AirDropScheduler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod(AirDropMod.MOD_ID)
public class AirDropMod {
    public static final String MOD_ID = "airdrop";

    public AirDropMod() {
        AirDropConfig.register();

        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.register(modBus);

        modBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(new AirDropScheduler());

        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) ->
                AirDropCommands.register(e.getDispatcher(), e.getBuildContext()));
    }

    private void commonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> AirDropLootManager.reload(FMLPaths.CONFIGDIR.get()));
    }
}
