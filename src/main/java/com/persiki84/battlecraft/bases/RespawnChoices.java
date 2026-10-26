package com.persiki84.battlecraft.bases;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.capturepoints.capture.BaseRules;
import com.persiki84.zones.ZoneLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// WHY: выбор базы на экране смерти приходит до нажатия «Возродиться» и тратится ровно на одно
// WHY: возрождение. Живой игрок выбрать не может: иначе выбор ждал бы следующей смерти, и ванильная
// WHY: кнопка, которая ведёт домой, вела бы на точку. Сервер перепроверяет выбор в момент возрождения:
// WHY: точку могли потерять, пока игрок смотрел на экран смерти, и тогда он встаёт на домашней базе
@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID)
public final class RespawnChoices {
    private static final Map<UUID, String> chosen = new HashMap<>();

    private RespawnChoices() {}

    @Nullable
    public static Component choose(ServerPlayer player, String id) {
        if (player.isAlive()) return Component.translatable("battlecraft.bases.alive");
        BaseSpot spot = BaseNetwork.find(ZoneLookup.teamNameOf(player), id);
        if (spot == null || spot.home()) return Component.translatable("battlecraft.bases.unknown");
        chosen.put(player.getUUID(), id);
        return null;
    }

    @Nullable
    public static BaseSpot take(ServerPlayer player) {
        String id = chosen.remove(player.getUUID());
        if (id == null || !BaseRules.respawn()) return null;
        BaseSpot spot = BaseNetwork.find(ZoneLookup.teamNameOf(player), id);
        return spot == null || spot.home() ? null : spot;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        chosen.remove(event.getEntity().getUUID());
    }
}
