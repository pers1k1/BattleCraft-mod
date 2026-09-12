package com.persiki84.knockdown;

import com.persiki84.battlecraft.client.hud.SharpHud;
import com.persiki84.knockdown.cap.KnockdownCapability;
import com.persiki84.knockdown.client.KnockdownHud;
import com.persiki84.knockdown.config.KnockdownConfig;
import com.persiki84.knockdown.network.NetworkHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import com.persiki84.knockdown.item.ModItems;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(KnockDownMod.MODID)
public class KnockDownMod {
    public static final String MODID = "knockdown";

    public KnockDownMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, KnockdownConfig.SPEC);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
        ModItems.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        NetworkHandler.register();
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(KnockdownCapability.class);
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            SharpHud.add(KnockdownHud.LAYER);
        }
    }
}
