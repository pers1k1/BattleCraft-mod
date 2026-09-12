package com.persiki84.dmgndctr.event;

import com.persiki84.dmgndctr.network.DamagePacket;
import com.persiki84.dmgndctr.network.PacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

public class ServerEventHandler {

    @SubscribeEvent
    public void onDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!ModuleSwitches.allows(ModuleId.DAMAGE_INDICATOR)) return;

        Entity attacker = event.getSource().getEntity();

        if (attacker instanceof ServerPlayer player) {
            float damage = event.getAmount();
            if (damage <= 0) return;

            double x = event.getEntity().getX() + (Math.random() - 0.5) * 0.5;
            double y = event.getEntity().getY() + event.getEntity().getBbHeight() + (Math.random() * 0.5);
            double z = event.getEntity().getZ() + (Math.random() - 0.5) * 0.5;

            boolean isCrit = damage > 10;

            PacketHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new DamagePacket(damage, x, y, z, isCrit)
            );
        }
    }
}
