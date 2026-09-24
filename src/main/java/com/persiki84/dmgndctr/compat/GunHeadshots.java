package com.persiki84.dmgndctr.compat;

import com.mojang.logging.LogUtils;
import com.persiki84.dmgndctr.event.CriticalMarks;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Method;

public final class GunHeadshots {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MOD_ID = "tacz";
    private static final String EVENT_CLASS = "com.tacz.guns.api.event.common.EntityHurtByGunEvent$Pre";

    private static Method isHeadShot;
    private static Method getAttacker;
    private static Method getHurtEntity;
    private static Method getLogicalSide;

    private GunHeadshots() {}

    // WHY: у стрелкового оружия ванильного крита нет, и настоящий крит там это попадание в голову;
    // WHY: берётся из Pre на последнем приоритете, когда чужие обработчики уже поправили признак
    @SuppressWarnings("unchecked")
    public static void listen() {
        if (!ModList.get().isLoaded(MOD_ID)) return;
        try {
            Class<?> event = Class.forName(EVENT_CLASS);
            isHeadShot = event.getMethod("isHeadShot");
            getAttacker = event.getMethod("getAttacker");
            getHurtEntity = event.getMethod("getHurtEntity");
            getLogicalSide = event.getMethod("getLogicalSide");
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false,
                    (Class<Event>) event, GunHeadshots::onHit);
        } catch (Throwable error) {
            LOGGER.warn("[dmgndctr] tacz headshot bridge is not bound: {}", error.toString());
        }
    }

    private static void onHit(Event event) {
        try {
            if (getLogicalSide.invoke(event) != LogicalSide.SERVER) return;
            if (!Boolean.TRUE.equals(isHeadShot.invoke(event))) return;

            if (getAttacker.invoke(event) instanceof Entity attacker
                    && getHurtEntity.invoke(event) instanceof Entity target) {
                CriticalMarks.mark(attacker, target);
            }
        } catch (Throwable ignored) {
        }
    }
}
