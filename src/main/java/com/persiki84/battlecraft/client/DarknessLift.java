package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.rules.GameRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class DarknessLift {
    private static final String MOD_ID = "trulydark";
    private static final String HOLDER = "dylanjkl.trulydark.TrulyDark";
    private static final String MASTER_SWITCH = "enabled";
    private static final String[] SWITCHES = {
            "darkOverworld", "darkDefault", "darkNether", "darkEnd", "darkSkyless", "darkSky", "darkMoon"
    };

    private static final Map<Field, Boolean> owned = new LinkedHashMap<>();

    private static boolean probed;
    private static boolean available;
    private static boolean lifted;

    private DarknessLift() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        if (wanted()) {
            lift();
        } else {
            restore();
        }
    }

    private static boolean wanted() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return false;

        return player.isSpectator() && ClientGameRules.allows(GameRule.SPECTATOR_LIGHT);
    }

    private static void lift() {
        if (!bound()) return;

        try {
            for (Map.Entry<Field, Boolean> entry : owned.entrySet()) {
                if (!lifted) entry.setValue(entry.getKey().getBoolean(null));
                entry.getKey().setBoolean(null, false);
            }
            if (!lifted) BattleCraftMod.LOGGER.info("[battlecraft] trulydark lifted for spectator");
            lifted = true;
        } catch (Throwable error) {
            available = false;
            BattleCraftMod.LOGGER.warn("[battlecraft] trulydark lift failed: {}", error.toString());
        }
    }

    private static void restore() {
        if (!lifted) return;

        lifted = false;
        try {
            for (Map.Entry<Field, Boolean> entry : owned.entrySet()) {
                entry.getKey().setBoolean(null, entry.getValue());
            }
        } catch (Throwable error) {
            available = false;
        }
    }

    private static boolean bound() {
        if (!probed) {
            probed = true;
            available = ModList.get().isLoaded(MOD_ID) && bind();
            if (!available) BattleCraftMod.LOGGER.warn("[battlecraft] trulydark bridge is not bound");
        }
        return available;
    }

    private static boolean bind() {
        try {
            Class<?> holder = Class.forName(HOLDER);
            owned.put(master(holder), Boolean.FALSE);
            for (String name : SWITCHES) {
                owned.put(holder.getField(name), Boolean.FALSE);
            }
            return true;
        } catch (Throwable error) {
            owned.clear();
            return false;
        }
    }

    private static Field master(Class<?> holder) throws NoSuchFieldException {
        Field field = holder.getDeclaredField(MASTER_SWITCH);
        field.setAccessible(true);
        return field;
    }
}
