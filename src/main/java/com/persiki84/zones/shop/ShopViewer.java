package com.persiki84.zones.shop;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Team;

import java.util.UUID;

public record ShopViewer(String team, UUID player) {
    public static final ShopViewer NOBODY = new ShopViewer(null, null);

    public static ShopViewer of(ServerPlayer player) {
        Team team = player.getTeam();
        return new ShopViewer(team == null ? null : team.getName(), player.getUUID());
    }

    // WHY: у команды и у игрока разные пространства имён, а ключом склада служит одна строка:
    // WHY: без приставки команда с именем-идентификатором забрала бы чужой личный запас
    public String key(StockScope scope) {
        return switch (scope) {
            case TEAM -> team == null ? "" : "t:" + team;
            case PLAYER -> player == null ? "" : "p:" + player;
            case SHARED -> "";
        };
    }
}
