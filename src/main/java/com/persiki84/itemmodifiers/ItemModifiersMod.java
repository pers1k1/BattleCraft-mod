package com.persiki84.itemmodifiers;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

@Mod(ItemModifiersMod.MODID)
public class ItemModifiersMod {
    public static final String MODID = "itemmodifiers";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ItemModifiersMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ModifierConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigChanged);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new EffectHandler());
        MinecraftForge.EVENT_BUS.register(new AttributeHandler());
    }

    // WHY: кеши сбрасывались только командой, и правка файла конфига руками или его перечитывание
    // WHY: оставляли в игре прежние модификаторы до перезапуска. Сброса кеша мало: надетое уже
    // WHY: держит прежние модификаторы, поэтому они пересчитываются на потоке сервера - событие
    // WHY: приходит с потока слежения за файлом
    private void onConfigChanged(ModConfigEvent event) {
        if (event.getConfig().getSpec() != ModifierConfig.SPEC) return;

        AttributeHandler.markDirty();
        EffectHandler.markDirty();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(() -> AttributeHandler.reapplyAll(server));
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModifierCommand.register(event.getDispatcher(), event.getBuildContext());
    }
}
