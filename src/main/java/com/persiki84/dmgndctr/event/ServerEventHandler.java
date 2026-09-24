package com.persiki84.dmgndctr.event;

import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ServerEventHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCriticalHit(CriticalHitEvent event) {
        if (event.getEntity().level().isClientSide || !strikes(event)) return;
        CriticalMarks.mark(event.getEntity(), event.getTarget());
    }

    // WHY: крит решает итог события, а не ванильный признак: ForgeHooks.getCriticalHit пропускает
    // WHY: ALLOW всегда, а DEFAULT только при ванильном крите
    private static boolean strikes(CriticalHitEvent event) {
        Event.Result result = event.getResult();
        return result == Event.Result.ALLOW || (result == Event.Result.DEFAULT && event.isVanillaCritical());
    }

    // WHY: читается последним, когда урон уже поправлен и не отменён другими обработчиками:
    // WHY: на обычном приоритете цифра показывалась и за удар, который потом отменили
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDamage(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        if (!ModuleSwitches.allows(ModuleId.DAMAGE_INDICATOR)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker) || attacker == target) return;

        float amount = event.getAmount();
        if (!(amount > 0.0f) || Float.isInfinite(amount)) return;

        DamageLedger.record(attacker, target, amount, CriticalMarks.marked(attacker, target));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) DamageLedger.flush();
    }
}
