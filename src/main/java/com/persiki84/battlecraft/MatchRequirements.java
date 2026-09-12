package com.persiki84.battlecraft;

import com.persiki84.capturepoints.capture.CapturePointManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MatchRequirements {

    public enum Stage {
        SETUP,
        LOBBY
    }

    public enum Rule {
        MOD_ENABLED(Stage.SETUP),
        CAPTURE_POINTS(Stage.SETUP),
        FINAL_POINT(Stage.SETUP),
        TEAMS(Stage.SETUP),
        JOIN_GRACE(Stage.LOBBY),
        MIN_PLAYERS(Stage.LOBBY),
        READY_SHARE(Stage.LOBBY);

        private final Stage stage;

        Rule(Stage stage) {
            this.stage = stage;
        }

        public Stage stage() {
            return stage;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public String label() {
            return "battlecraft.requirement." + id();
        }

        public String failure() {
            return label() + ".failed";
        }
    }

    public record Status(Rule rule, boolean met, int have, int need) {

        public Component describe() {
            return Component.translatable(met ? rule.label() : rule.failure(), have, need);
        }
    }

    private MatchRequirements() {}

    public static List<Status> check(MinecraftServer server, BattleCraftManager manager) {
        List<Status> statuses = new ArrayList<>();
        BattleCraftConfig config = manager.getConfig();

        statuses.add(new Status(Rule.MOD_ENABLED, !manager.isSoftDisabled(), 0, 0));
        statuses.add(pointStatus(Rule.CAPTURE_POINTS, CapturePointManager.getAllPoints().size()));
        statuses.add(pointStatus(Rule.FINAL_POINT, CapturePointManager.getAllFinalPoints().size()));

        addTeamStatus(statuses, server, config);

        addLobbyStatuses(statuses, server, manager, config);
        return statuses;
    }

    private static void addTeamStatus(List<Status> statuses, MinecraftServer server, BattleCraftConfig config) {
        if (!config.requireTeams) return;

        int needed = Math.max(1, config.teamsNeeded);
        int manned = mannedTeams(server).size();
        statuses.add(new Status(Rule.TEAMS, manned >= needed, manned, needed));
    }

    private static void addLobbyStatuses(List<Status> statuses, MinecraftServer server,
                                         BattleCraftManager manager, BattleCraftConfig config) {
        int grace = manager.graceSecondsLeft();
        statuses.add(new Status(Rule.JOIN_GRACE, grace <= 0, grace, 0));

        int ready = manager.readyCount(server);
        int minimum = Math.max(1, config.minPlayersToStart);
        statuses.add(new Status(Rule.MIN_PLAYERS, ready >= minimum, ready, minimum));

        int online = server.getPlayerList().getPlayerCount();
        int share = Math.max(1, (int) Math.ceil(online * (config.readyPercentToStart / 100.0)));
        statuses.add(new Status(Rule.READY_SHARE, ready >= share, ready, share));
    }

    private static Status pointStatus(Rule rule, int count) {
        return new Status(rule, count > 0, count, 1);
    }

    private static Set<String> mannedTeams(MinecraftServer server) {
        Set<String> teams = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerTeam team = (PlayerTeam) player.getTeam();
            if (team != null) teams.add(team.getName());
        }
        return teams;
    }

    public static boolean satisfied(List<Status> statuses, Stage stage) {
        for (Status status : statuses) {
            if (status.rule().stage() == stage && !status.met()) return false;
        }
        return true;
    }

    public static Status firstUnmet(List<Status> statuses, Stage stage) {
        for (Status status : statuses) {
            if (status.rule().stage() == stage && !status.met()) return status;
        }
        return null;
    }
}
