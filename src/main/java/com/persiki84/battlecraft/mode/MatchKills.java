package com.persiki84.battlecraft.mode;

import com.mojang.authlib.GameProfile;
import com.persiki84.battlecraft.BattleCraftManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.PlayerTeam;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

// WHY: очки за убийство и за технику дают только убийства врага в идущем матче: своя команда,
// WHY: игрок без команды и техника, на которой никто не ездил, счёт не двигают
public final class MatchKills {
    private MatchKills() {}

    static void playerKilled(ServerPlayer victim, @Nullable Entity killer) {
        MatchMode mode = running();
        if (mode == null || mode.killScore() <= 0 || !(killer instanceof ServerPlayer attacker)) return;

        String victimTeam = teamOf(victim.getServer(), victim.getScoreboardName());
        String attackerTeam = teamOf(attacker.getServer(), attacker.getScoreboardName());
        if (enemies(attackerTeam, victimTeam)) MatchScore.add(attackerTeam, mode.killScore());
    }

    // WHY: вызывается миксином в начале VehicleEntity.destroy у SuperbWarfare: своего события
    // WHY: уничтожения техники у мода нет, а уход сущности не отличает уничтожение от уборки ключом.
    // WHY: Вооружённая техника и безоружный транспорт стоят по-разному: пикап не равен танку
    public static void vehicleDestroyed(Entity vehicle, boolean armed, @Nullable Entity attacker,
                                        @Nullable String driverId) {
        MatchMode mode = running();
        int worth = mode == null ? 0 : armed ? mode.vehicleScore() : mode.transportScore();
        if (worth <= 0 || vehicle.level().isClientSide()) return;
        if (!(attacker instanceof ServerPlayer player) || !MatchScore.firstWreck(vehicle.getUUID())) return;

        MinecraftServer server = player.getServer();
        String attackerTeam = teamOf(server, player.getScoreboardName());
        String driverTeam = driverTeam(server, driverId);
        if (enemies(attackerTeam, driverTeam)) MatchScore.add(attackerTeam, worth);
    }

    @Nullable
    private static MatchMode running() {
        BattleCraftManager manager = BattleCraftManager.getInstance();
        if (manager.getPhase() != BattleCraftManager.GamePhase.ACTIVE || manager.isSoftDisabled()) return null;
        return MatchScore.mode();
    }

    private static boolean enemies(@Nullable String attacker, @Nullable String victim) {
        return attacker != null && victim != null && !attacker.equals(victim);
    }

    @Nullable
    static String teamOf(@Nullable MinecraftServer server, String scoreboardName) {
        if (server == null) return null;
        PlayerTeam team = server.getScoreboard().getPlayersTeam(scoreboardName);
        return team == null ? null : team.getName();
    }

    // WHY: водитель мог выйти с сервера к моменту взрыва: его ник берётся из кэша профилей
    @Nullable
    private static String driverTeam(@Nullable MinecraftServer server, @Nullable String driverId) {
        if (server == null || driverId == null || driverId.isEmpty()) return null;
        UUID id;
        try {
            id = UUID.fromString(driverId);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return teamOf(server, online.getScoreboardName());
        if (server.getProfileCache() == null) return null;
        Optional<GameProfile> known = server.getProfileCache().get(id);
        return known.map(profile -> teamOf(server, profile.getName())).orElse(null);
    }
}
