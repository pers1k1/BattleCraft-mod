package com.persiki84.zones.event;

import com.persiki84.zones.ZoneLookup;
import com.persiki84.zones.ZoneRule;
import com.persiki84.zones.ZonesMod;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ZonesMod.MOD_ID)
public class ZoneDamageGuard {

    // WHY: /kill и добивание нокдауна идут источником с BYPASSES_INVULNERABILITY: отмена такого
    // WHY: удара на базе поднимала сбитого с 1 HP и делала игрока на базе бессмертным для /kill
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !ModuleSwitches.allows(ModuleId.ZONES)) return;
        if (!(victim instanceof Player)) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        if (ZoneLookup.grantingTo(ZoneRule.INVULNERABLE, victim) != null) {
            event.setCanceled(true);
            return;
        }

        Player attacker = attackingPlayer(event.getSource());
        if (attacker != null && attacker != victim && ZoneLookup.denyingTo(ZoneRule.PVP, attacker) != null) {
            event.setCanceled(true);
        }
    }

    private static Player attackingPlayer(DamageSource source) {
        Player causing = controllingPlayer(source.getEntity());
        return causing != null ? causing : controllingPlayer(source.getDirectEntity());
    }

    // WHY: волк и стрела бьют от своего имени, поэтому игрок без PvP в зоне натравливал питомца
    // WHY: или стрелял из-под запрета: считаем атакующим того, кто ими управляет
    private static Player controllingPlayer(Entity entity) {
        Entity controller = entity instanceof Projectile projectile ? projectile.getOwner() : entity;
        if (controller instanceof Player player) return player;
        if (controller instanceof OwnableEntity pet && pet.getOwner() instanceof Player owner) return owner;
        return null;
    }
}
