package com.persiki84.battlecraft.announce;

import com.persiki84.battlecraft.network.PacketHandler;
import com.persiki84.battlecraft.network.S2CAnnouncePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Announcements {
    public static final int MAX_LENGTH = 180;
    public static final int MIN_SECONDS = 2;
    public static final int MAX_SECONDS = 60;
    public static final int DEFAULT_SECONDS = 8;

    private Announcements() {}

    public static int everyone(MinecraftServer server, String text, AnnounceStyle style, int seconds) {
        return deliver(server.getPlayerList().getPlayers(), text, style, seconds);
    }

    public static int clampSeconds(int seconds) {
        return Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, seconds));
    }

    // WHY: получатели сводятся в один набор до отправки: игрок, попавший и в команду, и в список
    // WHY: имён, иначе ловит два баннера подряд на одно объявление
    public static Set<ServerPlayer> resolve(MinecraftServer server, boolean everyone,
                                            Collection<String> teams, Collection<String> names) {
        if (everyone) return new LinkedHashSet<>(server.getPlayerList().getPlayers());

        Set<ServerPlayer> found = new LinkedHashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getTeam() != null && teams.contains(player.getTeam().getName())) found.add(player);
        }
        for (String name : names) {
            ServerPlayer player = server.getPlayerList().getPlayerByName(name);
            if (player != null) found.add(player);
        }
        return found;
    }

    public static List<ServerPlayer> ofTeam(MinecraftServer server, String team) {
        List<ServerPlayer> found = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getTeam() != null && player.getTeam().getName().equals(team)) found.add(player);
        }
        return found;
    }

    public static int deliver(Collection<ServerPlayer> recipients, String text, AnnounceStyle style,
                              int seconds) {
        String trimmed = clip(text);
        if (trimmed.isEmpty()) return 0;

        int held = clampSeconds(seconds);
        Set<ServerPlayer> unique = new LinkedHashSet<>(recipients);
        for (ServerPlayer player : unique) {
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CAnnouncePacket(trimmed, style, held));
        }
        return unique.size();
    }

    public static String clip(String text) {
        if (text == null) return "";

        String trimmed = text.trim();
        return trimmed.length() <= MAX_LENGTH ? trimmed : trimmed.substring(0, MAX_LENGTH);
    }

    public static Component receipt(int count) {
        return Component.translatable("battlecraft.announce.sent", count).withStyle(ChatFormatting.GREEN);
    }
}
