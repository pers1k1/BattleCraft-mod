package com.persiki84.battlecraft.bases;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.network.PacketHandler;
import com.persiki84.capturepoints.capture.BaseRules;
import com.persiki84.zones.ZoneLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// WHY: сеть баз меняется от захвата точек, смены команды, правки зон и правил: вместо крючка на
// WHY: каждое из этих мест сервер раз в две секунды сверяет подпись списка и шлёт его, только если он стал другим
@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID)
public final class BaseSync {
    private static final int PERIOD_TICKS = 40;
    private static final Map<UUID, String> sent = new HashMap<>();
    private static int clock;

    private BaseSync() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++clock < PERIOD_TICKS) return;
        clock = 0;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            push(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        sent.remove(player.getUUID());
        push(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        sent.remove(event.getEntity().getUUID());
    }

    private static void push(ServerPlayer player) {
        List<BaseSpot> spots = BaseNetwork.of(ZoneLookup.teamNameOf(player));
        StringBuilder signature = new StringBuilder();
        signature.append(BaseRules.respawn()).append(BaseRules.travel()).append(BaseRules.shop());
        List<S2CBasesPacket.Entry> entries = new ArrayList<>(spots.size());
        for (BaseSpot spot : spots) {
            signature.append('|').append(spot.id());
            entries.add(new S2CBasesPacket.Entry(spot.id(), spot.name(), spot.home()));
        }
        String shown = signature.toString();
        if (shown.equals(sent.get(player.getUUID()))) return;
        sent.put(player.getUUID(), shown);
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CBasesPacket(entries, BaseRules.respawn(), BaseRules.travel(), BaseRules.shop()));
    }
}
