package com.persiki84.battlecraft.radio;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.compat.walkie.Walkie;
import com.persiki84.battlecraft.network.PacketHandler;
import com.persiki84.battlecraft.network.S2CRadioTalkPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Team;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// WHY: клиент не имеет права сам решать, кого слышно по рации: статическим каналом голосового
// WHY: чата ходят ещё и группы. Поэтому получателей заново считает сервер и подтверждает каждую
// WHY: передачу отдельно - вместе с силой сигнала, по которой клиент кладёт помехи
@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID)
public final class RadioRelay {
    private static final long SWEEP_MS = 200L;
    private static final float CLEAN_SHARE = 0.35f;
    private static final int FAINT_SIGNAL = 3;
    private static final UUID[] NOBODY = new UUID[0];

    private static final Map<UUID, Long> swept = new ConcurrentHashMap<>();

    private RadioRelay() {}

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        swept.remove(id);
        RadioAir.forget(id);
    }

    // WHY: голосовой пакет приходит пятьдесят раз в секунду и не в серверном потоке: обход
    // WHY: игроков и чтение инвентарей уходят в тик, а заявки режутся до пяти в секунду
    public static void onSpeak(UUID speaker) {
        if (speaker == null || !Walkie.available()) return;

        long now = System.currentTimeMillis();
        Long last = swept.get(speaker);
        if (last != null && now - last < SWEEP_MS) return;

        swept.put(speaker, now);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> sweep(server, speaker));
    }

    // WHY: зовут из обработчика клавиши эфира, он уже в серверном потоке
    public static void arm(ServerPlayer speaker) {
        if (!Walkie.available()) return;

        swept.put(speaker.getUUID(), System.currentTimeMillis());
        sweep(speaker);
    }

    private static void sweep(MinecraftServer server, UUID speakerId) {
        ServerPlayer speaker = server.getPlayerList().getPlayer(speakerId);
        if (speaker == null) {
            RadioAir.forget(speakerId);
            return;
        }
        sweep(speaker);
    }

    private static void sweep(ServerPlayer speaker) {
        UUID speakerId = speaker.getUUID();
        ItemStack hand = Walkie.transmitting(speaker);
        boolean vest = hand.isEmpty();
        ItemStack sending = vest ? vest(speaker) : hand;
        if (sending.isEmpty()) {
            RadioAir.route(speakerId, NOBODY);
            return;
        }

        RadioAir.route(speakerId, reach(speaker, Walkie.canal(sending), vest));
    }

    // WHY: с рацией в руке звук рассылает сам чужой мод, и дублировать его нельзя - слушатель
    // WHY: получил бы две копии речи. Наш путь работает ровно там, где чужой молчит: рация не в
    // WHY: руках, зажата клавиша эфира, и это та же рация, которой игрок слушает частоту
    private static ItemStack vest(ServerPlayer speaker) {
        if (!RadioAir.onAir(speaker.getUUID())) return ItemStack.EMPTY;

        ItemStack carried = Walkie.listening(speaker);
        return carried.isEmpty() || Walkie.muted(carried) ? ItemStack.EMPTY : carried;
    }

    private static UUID[] reach(ServerPlayer speaker, int canal, boolean vest) {
        UUID speakerId = speaker.getUUID();
        List<UUID> listeners = new ArrayList<>();
        for (ServerPlayer listener : speaker.server.getPlayerList().getPlayers()) {
            if (listener.getUUID().equals(speakerId)) continue;
            if (!hears(speaker, listener, canal)) continue;

            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> listener),
                    new S2CRadioTalkPacket(speakerId, friendly(speaker, listener), signal(speaker, listener)));
            if (vest) listeners.add(listener.getUUID());
        }
        return listeners.isEmpty() ? NOBODY : listeners.toArray(new UUID[0]);
    }

    private static boolean hears(ServerPlayer speaker, ServerPlayer listener, int canal) {
        if (!Walkie.crossDimensions() && listener.level().dimensionType() != speaker.level().dimensionType()) {
            return false;
        }

        ItemStack receiving = Walkie.listening(listener);
        if (receiving.isEmpty() || Walkie.canal(receiving) != canal) return false;

        return Walkie.reaches(speaker.level(), listener.level(), speaker.position(), listener.position(),
                Walkie.range(receiving));
    }

    // WHY: сила сигнала квантуется ступенями, а не едет точным расстоянием: иначе игрок с
    // WHY: правленым клиентом читает по помехам, насколько близко подошёл говорящий соперник
    private static int signal(ServerPlayer speaker, ServerPlayer listener) {
        if (speaker.level() != listener.level()) return FAINT_SIGNAL;

        double range = Walkie.effectiveRange(speaker.level(), listener.level(),
                Walkie.range(Walkie.listening(listener)));
        if (range <= 0.0) return S2CRadioTalkPacket.SIGNAL_STEPS;

        double share = speaker.position().distanceTo(listener.position()) / range;
        double quality = (1.0 - share) / (1.0 - CLEAN_SHARE);
        return (int) Math.round(Math.max(0.0, Math.min(1.0, quality)) * S2CRadioTalkPacket.SIGNAL_STEPS);
    }

    // WHY: команд нет вовсе - значит соперников нет тоже, и прятать имя не от кого: на сервере
    // WHY: без матча рация обязана работать как обычная рация
    private static boolean friendly(ServerPlayer speaker, ServerPlayer listener) {
        Team mine = speaker.getTeam();
        Team theirs = listener.getTeam();
        if (mine == null || theirs == null) return mine == null && theirs == null;
        return mine.getName().equals(theirs.getName());
    }
}
