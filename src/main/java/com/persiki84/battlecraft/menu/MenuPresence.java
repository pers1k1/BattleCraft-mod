package com.persiki84.battlecraft.menu;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.network.PacketHandler;
import com.persiki84.battlecraft.network.S2CMenuPresencePacket;
import com.persiki84.shared.ActionGate;
import com.persiki84.shared.menu.MenuFace;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID)
public final class MenuPresence {
    private static final String GATE_KEY = "battlecraft_menu_presence";
    private static final int GATE_TICKS = 4;

    private static final Map<UUID, MenuFace> open = new HashMap<>();
    private static final Map<UUID, Claim> pending = new HashMap<>();

    private MenuPresence() {}

    // WHY: сторож частоты откладывает заявку, а не выбрасывает её: открыл и закрыл экран за пару
    // WHY: тиков, и выброшенное закрытие оставляло у соседей висеть окно до следующего экрана
    public static void claim(ServerPlayer player, MenuFace face) {
        if (face != null && !face.valid()) return;

        UUID id = player.getUUID();
        if (Objects.equals(open.get(id), face)) {
            pending.remove(id);
            return;
        }
        pending.put(id, new Claim(face));
        settle(player);
    }

    public static boolean open(ServerPlayer player) {
        return open.containsKey(player.getUUID());
    }

    public static void forget(ServerPlayer player) {
        pending.remove(player.getUUID());
        MenuFrames.clear(player);
        if (open.remove(player.getUUID()) == null) return;

        announce(player, null);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending.isEmpty()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (UUID id : pending.keySet().toArray(new UUID[0])) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                pending.remove(id);
                continue;
            }
            settle(player);
        }
    }

    private static void settle(ServerPlayer player) {
        if (player == null || !ActionGate.allow(player, GATE_KEY, GATE_TICKS)) return;

        Claim claim = pending.remove(player.getUUID());
        if (claim == null) return;

        if (claim.face == null) {
            open.remove(player.getUUID());
            MenuFrames.clear(player);
        } else {
            MenuFace previous = open.put(player.getUUID(), claim.face);
            if (!MenuFace.sameScreen(previous, claim.face)) MenuFrames.clear(player);
        }
        announce(player, claim.face);
    }

    // WHY: досылается и закрытое состояние: сосед, ушедший из радиуса слежения, не получил
    // WHY: закрытия, и без этого при возвращении видел бы окно давно закрытого экрана
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof ServerPlayer target)) return;
        if (!(event.getEntity() instanceof ServerPlayer watcher)) return;

        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> watcher),
                new S2CMenuPresencePacket(target.getUUID(), open.get(target.getUUID())));
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) forget(player);
    }

    private static void announce(ServerPlayer player, MenuFace face) {
        PacketHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new S2CMenuPresencePacket(player.getUUID(), face));
    }

    private record Claim(MenuFace face) {}
}
