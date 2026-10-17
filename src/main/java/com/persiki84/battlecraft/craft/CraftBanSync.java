package com.persiki84.battlecraft.craft;

import com.persiki84.battlecraft.BattleCraftManager;
import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.MatchEvent;
import com.persiki84.battlecraft.network.PacketHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID)
public final class CraftBanSync {
    private static boolean sentRunning;

    private CraftBanSync() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        CraftBanBook.open(event.getServer());
        sentRunning = running();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CraftBanBook.close();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), current());
        }
    }

    @SubscribeEvent
    public static void onMatchStarted(MatchEvent.Started event) {
        phaseFlipped(event.server());
    }

    @SubscribeEvent
    public static void onMatchEnded(MatchEvent.Ended event) {
        phaseFlipped(event.server());
    }

    // WHY: «идёт ли матч» меняется не только стартом и концом матча: выключатель ядра делает матч
    // WHY: идущим всегда, а событий у него нет. Флаг сверяется каждый тик: это одно сравнение, а
    // WHY: открытый верстак иначе до секунды держал бы результат, который фаза уже запретила
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (running() != sentRunning) phaseFlipped(event.getServer());
    }

    private static void phaseFlipped(MinecraftServer server) {
        if (running() == sentRunning) return;
        everyone(server);
    }

    static void everyone(MinecraftServer server) {
        sentRunning = running();
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), current());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refreshMenu(player);
        }
    }

    private static S2CCraftBansPacket current() {
        return new S2CCraftBansPacket(CraftBanBook.all(), running());
    }

    // WHY: открытое окно держит результат, посчитанный до правки: сетка верстака пересчитывается
    // WHY: на месте, а камнерез и кузнечный стол собирают список рецептов только при смене входа,
    // WHY: и устаревший список выдал бы запрещённое по номеру кнопки, поэтому они закрываются
    private static void refreshMenu(ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu instanceof CraftingMenu || menu instanceof InventoryMenu) {
            menu.slotsChanged(player.getInventory());
        } else if (menu instanceof StonecutterMenu || menu instanceof SmithingMenu) {
            player.closeContainer();
        }
    }

    private static boolean running() {
        return BattleCraftManager.getInstance().matchRunning();
    }
}
