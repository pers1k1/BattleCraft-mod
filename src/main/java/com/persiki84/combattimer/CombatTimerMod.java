package com.persiki84.combattimer;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

@Mod(CombatTimerMod.MODID)
public class CombatTimerMod {
    public static final String MODID = "combattimer";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CombatTimerMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CombatTimerConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new CombatEventHandler());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CombatCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("CombatTimer Mod Loaded. Time: " + combatDuration() + "s, KillOnLogout: " + killOnLogout());
    }

    public static int combatDuration() {
        return CombatTimerConfig.DURATION.get();
    }

    public static boolean killOnLogout() {
        return CombatTimerConfig.KILL_ON_LOGOUT.get();
    }
}
