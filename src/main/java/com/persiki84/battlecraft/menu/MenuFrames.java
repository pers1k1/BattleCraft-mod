package com.persiki84.battlecraft.menu;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.network.PacketHandler;
import com.persiki84.battlecraft.network.S2CMenuFramePacket;
import com.persiki84.shared.ActionGate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// WHY: кадр окна это картинка от клиента, и проверить её содержимое сервер не может: поэтому она
// WHY: уходит только своей команде и только тем, кто стоит рядом, а размер и частота режутся здесь
@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID)
public final class MenuFrames {
    public static final int MAX_BYTES = 30000;

    private static final String GATE_KEY = "battlecraft_menu_frame";
    private static final int GATE_TICKS = 10;
    private static final int SWEEP_TICKS = 10;
    private static final double REACH = 16.0;

    private static final Map<UUID, byte[]> pending = new HashMap<>();
    private static final Map<UUID, Frame> shown = new HashMap<>();

    private MenuFrames() {}

    public static void claim(ServerPlayer sender, byte[] image) {
        if (image.length == 0 || image.length > MAX_BYTES || !MenuPresence.open(sender)) return;

        pending.put(sender.getUUID(), image);
        settle(sender);
    }

    public static void clear(ServerPlayer player) {
        pending.remove(player.getUUID());
        shown.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (UUID id : pending.keySet().toArray(new UUID[0])) {
            settle(server.getPlayerList().getPlayer(id));
        }
        if (server.getTickCount() % SWEEP_TICKS == 0) sweep(server);
    }

    // WHY: сторож откладывает кадр, а не выбрасывает: выброшенным мог оказаться последний, и
    // WHY: тиммейты видели бы окно в состоянии, которого у игрока уже нет
    private static void settle(ServerPlayer sender) {
        if (sender == null || !MenuPresence.open(sender)) {
            if (sender != null) clear(sender);
            return;
        }
        if (!ActionGate.allow(sender, GATE_KEY, GATE_TICKS)) return;

        byte[] image = pending.remove(sender.getUUID());
        if (image == null) return;

        Frame frame = new Frame(image);
        shown.put(sender.getUUID(), frame);
        deliver(sender, frame);
    }

    private static void sweep(MinecraftServer server) {
        for (UUID id : shown.keySet().toArray(new UUID[0])) {
            ServerPlayer sender = server.getPlayerList().getPlayer(id);
            if (sender == null || !MenuPresence.open(sender)) {
                shown.remove(id);
                continue;
            }
            deliver(sender, shown.get(id));
        }
    }

    // WHY: получивший кадр и ушедший из радиуса вычёркивается, чтобы на возвращении получить свежий
    private static void deliver(ServerPlayer sender, Frame frame) {
        frame.delivered.removeIf(id -> {
            ServerPlayer viewer = sender.server.getPlayerList().getPlayer(id);
            return viewer == null || !eligible(sender, viewer);
        });
        for (ServerPlayer viewer : sender.serverLevel().players()) {
            if (!eligible(sender, viewer) || !frame.delivered.add(viewer.getUUID())) continue;

            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> viewer),
                    new S2CMenuFramePacket(sender.getUUID(), frame.image));
        }
    }

    private static boolean eligible(ServerPlayer sender, ServerPlayer viewer) {
        return viewer != sender && viewer.level() == sender.level() && sender.isAlliedTo(viewer)
                && viewer.distanceToSqr(sender) <= REACH * REACH;
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) clear(player);
    }

    private static final class Frame {
        private final byte[] image;
        private final Set<UUID> delivered = new HashSet<>();

        private Frame(byte[] image) {
            this.image = image;
        }
    }
}
