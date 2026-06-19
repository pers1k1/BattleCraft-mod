package com.persiki84.dmgndctr;

import com.persiki84.dmgndctr.event.ServerEventHandler;
import com.persiki84.dmgndctr.network.PacketHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(DmgIndicatorMod.MODID)
public class DmgIndicatorMod {
    public static final String MODID = "dmgndctr";

    public DmgIndicatorMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(new ServerEventHandler());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        PacketHandler.register();
    }
}
