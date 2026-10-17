package com.persiki84.sellmod.money;

import com.persiki84.battlecraft.BattleCraftManager;
import com.persiki84.battlecraft.MatchEvent;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import com.persiki84.shared.economy.WalletAdmin;
import com.persiki84.shared.economy.WalletDeaths;
import com.persiki84.shared.economy.Wallets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class MoneyEvents {

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        Wallets.load(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        Wallets.save();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        Wallets.saveIfDirty();
        Wallets.forget();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) Wallets.tick();
    }

    // WHY: доля счёта снимается последним обработчиком и только у состоявшейся смерти: нокдаун
    // WHY: отменяет событие, и сбитый, которого подняли, не теряет ничего
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer victim)) return;
        if (!ModuleSwitches.allows(ModuleId.SELL) || !BattleCraftManager.getInstance().matchRunning()) return;

        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer player ? player : null;
        WalletDeaths.died(victim, killer);
    }

    // WHY: выключатель модуля гасит деньги целиком: ни потери доли при смерти, ни сброса счетов
    @SubscribeEvent
    public void onMatchStarted(MatchEvent.Started event) {
        if (ModuleSwitches.allows(ModuleId.SELL)) WalletAdmin.matchStarted();
    }
}
