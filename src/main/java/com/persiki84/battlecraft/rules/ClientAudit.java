package com.persiki84.battlecraft.rules;

import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClientAudit {
    private static final int GRACE_TICKS = 600;
    private static final int SWEEP_TICKS = 20;
    private static final int ADMIN_LEVEL = 2;
    private static final String NAME_SEPARATOR = ", ";

    private static final Map<UUID, Integer> awaited = new HashMap<>();

    private ClientAudit() {}

    // WHY: сторож на клиенте молчит у себя дома (Minecraft.hasSingleplayerServer), поэтому ждать
    // WHY: отчёт от хозяина одиночного мира значит кикать его через полминуты после каждого входа
    public static void expect(ServerPlayer player) {
        if (!watched()) return;
        if (player.server.isSingleplayerOwner(player.getGameProfile())) return;

        awaited.put(player.getUUID(), GRACE_TICKS);
    }

    public static void expectAll(MinecraftServer server) {
        if (server == null) return;

        awaited.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            expect(player);
        }
    }

    public static void forget(UUID player) {
        awaited.remove(player);
    }

    // WHY: карта почти всегда пуста, и просроченных ищем раз в секунду: ежетиковый обход с
    // WHY: распаковкой счётчиков дал бы мусор в тик-луте ради ожидания длиной в полминуты
    public static void tick(MinecraftServer server) {
        if (awaited.isEmpty() || server.getTickCount() % SWEEP_TICKS != 0) return;

        Iterator<Map.Entry<UUID, Integer>> entries = awaited.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Integer> entry = entries.next();
            int left = entry.getValue() - SWEEP_TICKS;
            if (left > 0) {
                entry.setValue(left);
                continue;
            }

            entries.remove();
            silent(server, entry.getKey());
        }
    }

    public static void accept(ServerPlayer player, List<String> dropped, List<String> kept, boolean glintLoose) {
        awaited.remove(player.getUUID());
        if (glintLoose && GameRules.allows(GameRule.NO_ENCHANT_GLINT)) {
            report(player.server, player, "battlecraft.glint.refused", List.of());
            kick(player, Component.translatable("battlecraft.glint.kicked"));
            return;
        }
        if (!GameRules.allows(GameRule.BLOCK_RESOURCE_PACKS)) return;

        if (!kept.isEmpty()) {
            report(player.server, player, "battlecraft.packs.refused", kept);
            kick(player, Component.translatable("battlecraft.packs.kicked", names(kept)));
            return;
        }
        if (dropped.isEmpty()) return;

        player.sendSystemMessage(Component.translatable("battlecraft.packs.dropped", names(dropped))
                .withStyle(ChatFormatting.YELLOW));
        report(player.server, player, "battlecraft.packs.report", dropped);
    }

    private static boolean watched() {
        return GameRules.allows(GameRule.BLOCK_RESOURCE_PACKS) || GameRules.allows(GameRule.NO_ENCHANT_GLINT);
    }

    private static void silent(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null) return;

        report(server, player, "battlecraft.packs.silent", List.of());
        kick(player, Component.translatable("battlecraft.packs.kicked.silent"));
    }

    private static void kick(ServerPlayer player, Component reason) {
        player.connection.disconnect(reason);
    }

    private static void report(MinecraftServer server, ServerPlayer player, String key, List<String> packs) {
        Component message = Component.translatable(key, player.getGameProfile().getName(), names(packs))
                .withStyle(ChatFormatting.GOLD);
        BattleCraftMod.LOGGER.warn("Client audit {}: {} {}", key, player.getGameProfile().getName(), packs);
        for (ServerPlayer admin : server.getPlayerList().getPlayers()) {
            if (admin.hasPermissions(ADMIN_LEVEL)) admin.sendSystemMessage(message);
        }
    }

    private static Component names(List<String> packs) {
        if (packs.isEmpty()) return Component.translatable("battlecraft.packs.none");

        return Component.literal(String.join(NAME_SEPARATOR, packs));
    }
}
