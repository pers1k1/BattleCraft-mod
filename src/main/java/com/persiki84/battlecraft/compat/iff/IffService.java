package com.persiki84.battlecraft.compat.iff;

import com.persiki84.battlecraft.BattleCraftManager;
import com.persiki84.battlecraft.rules.GameRule;
import com.persiki84.battlecraft.rules.GameRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

public final class IffService {
    private static final int CHECK_INTERVAL_TICKS = 20;

    private int ticks;
    private boolean issuing;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !IffDevice.available()) return;

        ticks++;
        if (ticks < CHECK_INTERVAL_TICKS) return;
        ticks = 0;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        boolean wanted = GameRules.allows(GameRule.IFF_DEVICE) && matchRunning();
        // WHY: обход слотов ради изъятия стоит дороже проверки, поэтому снятие идёт один раз -
        // WHY: на переходе правила в выключенное, а не каждую секунду всему серверу
        if (!wanted && !issuing) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            enforce(player, wanted);
        }
        issuing = wanted;
    }

    private static boolean matchRunning() {
        return BattleCraftManager.getInstance().getPhase() == BattleCraftManager.GamePhase.ACTIVE;
    }

    // WHY: снять устройство нечем: вынутое из слота возвращается ближайшей проверкой, а пока его
    // WHY: держат в инвентаре, там лежит именно выданный экземпляр, и он изымается
    private static void enforce(ServerPlayer player, boolean wanted) {
        if (!wanted || player.isSpectator()) {
            IffDevice.revoke(player);
            return;
        }

        player.getInventory().clearOrCountMatchingItems(IffDevice::isIssued, -1,
                player.inventoryMenu.getCraftSlots());
        if (!IffDevice.equipped(player)) IffDevice.issue(player);
    }

    @SubscribeEvent
    public void onToss(ItemTossEvent event) {
        if (IffDevice.isIssued(event.getEntity().getItem())) event.setCanceled(true);
    }

    // WHY: сами слоты Curios роняет чужой мод, и наш обработчик обязан идти последним, иначе
    // WHY: выданное устройство ложится в кучу дропа уже после нашей чистки
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        event.getDrops().removeIf(drop -> IffDevice.isIssued(drop.getItem()));
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && IffDevice.available()) {
            enforce(player, GameRules.allows(GameRule.IFF_DEVICE) && matchRunning());
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && IffDevice.available()) {
            IffDevice.revoke(player);
        }
    }
}
