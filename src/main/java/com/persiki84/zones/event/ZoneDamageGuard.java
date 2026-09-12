package com.persiki84.zones.event;

import com.persiki84.zones.ZoneLookup;
import com.persiki84.zones.ZoneRule;
import com.persiki84.zones.ZonesMod;
import net.minecraft.world.entity.Entity;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ZonesMod.MOD_ID)
public class ZoneDamageGuard {

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !ModuleSwitches.allows(ModuleId.ZONES)) return;

        if (victim instanceof Player && ZoneLookup.grantingTo(ZoneRule.INVULNERABLE, victim) != null) {
            event.setCanceled(true);
            return;
        }

        if (attackerBarredFromPvp(event.getSource().getEntity())) {
            event.setCanceled(true);
        }
    }

    private static boolean attackerBarredFromPvp(Entity attacker) {
        if (!(attacker instanceof Player)) return false;
        return ZoneLookup.denyingTo(ZoneRule.PVP, attacker) != null;
    }
}
