package com.persiki84.sellmod;

import com.persiki84.sellmod.client.CurrencyHud;
import com.persiki84.sellmod.network.PacketHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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
        PacketHandler.register();
        LOGGER.info("Sell Mod initialized!");
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("sellmod_currency", CurrencyHud.OVERLAY);
        }
    }
}
