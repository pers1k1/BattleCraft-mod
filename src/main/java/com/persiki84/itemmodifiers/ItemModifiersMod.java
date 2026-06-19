package com.persiki84.itemmodifiers;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(ItemModifiersMod.MODID)
public class ItemModifiersMod {
    public static final String MODID = "itemmodifiers";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ItemModifiersMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModifierConfig.SPEC);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new EffectHandler());
        MinecraftForge.EVENT_BUS.register(new AttributeHandler());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModifierCommand.register(event.getDispatcher(), event.getBuildContext());
    }
}
