package com.persiki84.sellmod;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(SellMod.MODID)
public class SellMod {
    public static final String MODID = "sellmod";
    public static final Logger LOGGER = LogManager.getLogger();

    public SellMod() {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new SellEvents());
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        SellManager.init();
        SellManager.loadConfig();
    }

    private void setup(FMLCommonSetupEvent event) {
        LOGGER.info("Sell Mod initialized!");
    }
}
