package com.persiki84.battlecraft.rules;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.network.PacketHandler;
import com.persiki84.battlecraft.network.S2CConfigScanPacket;
import com.persiki84.battlecraft.network.S2CConfigWatchPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ConfigAudit {
    private static final int GRACE_TICKS = 600;
    private static final int SWEEP_TICKS = 20;
    private static final int ADMIN_LEVEL = 2;
    private static final int NAMED_LIMIT = 6;
    private static final String NAME_SEPARATOR = ", ";

    private static final Map<UUID, Awaited> awaited = new HashMap<>();
    private static final Map<UUID, Map<String, Long>> scanning = new HashMap<>();

    private ConfigAudit() {}

    private static final class Awaited {
        private final List<String> paths;
        private int left;

        private Awaited(List<String> paths) {
            this.paths = paths;
            this.left = GRACE_TICKS;
        }
    }

    public static void expect(ServerPlayer player) {
        if (!watched()) return;
        if (player.server.isSingleplayerOwner(player.getGameProfile())) return;

        List<String> paths = ConfigManifest.paths();
        awaited.put(player.getUUID(), new Awaited(paths));
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new S2CConfigWatchPacket(paths));
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
        scanning.remove(player);
    }

    public static void tick(MinecraftServer server) {
        if (awaited.isEmpty() || server.getTickCount() % SWEEP_TICKS != 0) return;

        Iterator<Map.Entry<UUID, Awaited>> entries = awaited.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Awaited> entry = entries.next();
            Awaited pending = entry.getValue();
            pending.left -= SWEEP_TICKS;
            if (pending.left > 0) continue;

            entries.remove();
            silent(server, entry.getKey());
        }
    }

    // WHY: сверяется тот список, который был отправлен именно этому игроку: эталон могли поменять
    // WHY: между запросом и ответом, и тогда позиции хешей уже значат другие файлы
    public static void accept(ServerPlayer player, long[] hashes) {
        Awaited pending = awaited.remove(player.getUUID());
        if (pending == null || !watched()) return;

        List<String> paths = pending.paths;
        if (hashes.length != paths.size()) {
            refuse(player, List.of(), "battlecraft.configs.short");
            return;
        }

        List<String> broken = new ArrayList<>();
        for (int index = 0; index < paths.size(); index++) {
            String path = paths.get(index);
            if (ConfigManifest.hash(path) != hashes[index]) broken.add(path);
        }
        if (!broken.isEmpty()) refuse(player, broken, "battlecraft.configs.refused");
    }

    public static void requestScan(ServerPlayer operator) {
        scanning.put(operator.getUUID(), new LinkedHashMap<>());
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> operator), new S2CConfigScanPacket());
    }

    // WHY: снимок принимается только у того, кого сервер сам об этом попросил: иначе любой клиент
    // WHY: присылает свой набор файлов и сам себе назначает эталон
    public static void acceptSnapshot(ServerPlayer player, List<String> paths, long[] hashes, boolean last) {
        Map<String, Long> collected = scanning.get(player.getUUID());
        if (collected == null || !player.hasPermissions(ADMIN_LEVEL)) return;

        int count = Math.min(paths.size(), hashes.length);
        for (int index = 0; index < count && collected.size() < ConfigManifest.MAX_FILES; index++) {
            collected.put(paths.get(index), hashes[index]);
        }
        if (!last) return;

        scanning.remove(player.getUUID());
        ConfigManifest.replace(player.server, collected);
        player.sendSystemMessage(Component.translatable("battlecraft.configs.snapshot.taken",
                ConfigManifest.size()).withStyle(ChatFormatting.GREEN));
        expectAll(player.server);
    }

    private static boolean watched() {
        return GameRules.allows(GameRule.CHECK_CONFIGS) && !ConfigManifest.empty();
    }

    private static void silent(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null) return;

        report(server, player, "battlecraft.configs.silent", List.of());
        player.connection.disconnect(Component.translatable("battlecraft.configs.kicked.silent"));
    }

    private static void refuse(ServerPlayer player, List<String> broken, String key) {
        report(player.server, player, key, broken);
        player.connection.disconnect(Component.translatable("battlecraft.configs.kicked", names(broken)));
    }

    private static void report(MinecraftServer server, ServerPlayer player, String key, List<String> files) {
        Component message = Component.translatable(key, player.getGameProfile().getName(), names(files))
                .withStyle(ChatFormatting.GOLD);
        BattleCraftMod.LOGGER.warn("Config audit {}: {} {}", key, player.getGameProfile().getName(), files);
        for (ServerPlayer admin : server.getPlayerList().getPlayers()) {
            if (admin.hasPermissions(ADMIN_LEVEL)) admin.sendSystemMessage(message);
        }
    }

    private static Component names(List<String> files) {
        if (files.isEmpty()) return Component.translatable("battlecraft.configs.none");

        List<String> shown = files.size() <= NAMED_LIMIT ? files : files.subList(0, NAMED_LIMIT);
        Component listed = Component.literal(String.join(NAME_SEPARATOR, shown));
        if (files.size() <= NAMED_LIMIT) return listed;

        return Component.translatable("battlecraft.configs.more", listed, files.size() - NAMED_LIMIT);
    }
}
