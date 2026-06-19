package com.persiki84.killreward;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod(KillRewardMod.MODID)
public class KillRewardMod {
    public static final String MODID = "killreward";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static boolean modEnabled = true;
    public static boolean rewardTeamKills = false;
    public static String rewardItem = "minecraft:diamond";
    public static int rewardAmount = 1;

    public static final Map<UUID, String> lastRewards = new HashMap<>();

    public KillRewardMod() {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new KillEventHandler());
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigLoad);
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onConfigReload);
    }

    private void onConfigLoad(net.minecraftforge.fml.event.config.ModConfigEvent.Loading event) {
        Config.loadConfig();
    }

    private void onConfigReload(net.minecraftforge.fml.event.config.ModConfigEvent.Reloading event) {
        Config.loadConfig();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        KillRewardCommand.register(event.getDispatcher());
    }

    public static Item getRewardItem() {
        ResourceLocation itemLocation = new ResourceLocation(rewardItem);
        return ForgeRegistries.ITEMS.getValue(itemLocation);
    }
}
