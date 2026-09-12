package com.persiki84.killreward;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
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

    private static boolean matchActive = true;

    public static void setMatchActive(boolean active) {
        matchActive = active;
    }

    public static boolean rewardsEnabled() {
        return modEnabled && matchActive && ModuleSwitches.allows(ModuleId.KILL_REWARD);
    }

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

    // WHY: запись о последней награде лежала по UUID навсегда, поэтому список рос с каждым
    // WHY: новым игроком и переживал остановку сервера в одиночной игре
    @SubscribeEvent
    public void onPlayerLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        lastRewards.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        lastRewards.clear();
    }

    // WHY: id приходит строкой из команды и конфига, а ResourceLocation бросает на кривом вводе:
    // WHY: без разбора это исключение на каждом убийстве. Отсутствующий предмет даёт воздух,
    // WHY: поэтому он тоже считается «награды нет», иначе игрок получает «выдано 1x Air»
    public static Item getRewardItem() {
        ResourceLocation itemLocation = ResourceLocation.tryParse(rewardItem);
        if (itemLocation == null) return null;

        Item item = ForgeRegistries.ITEMS.getValue(itemLocation);
        return item == null || item == net.minecraft.world.item.Items.AIR ? null : item;
    }
}
