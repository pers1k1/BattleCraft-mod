package com.persiki84.battlecraft;

import com.persiki84.killreward.KillRewardMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import com.persiki84.battlecraft.network.PacketHandler;
import com.persiki84.battlecraft.network.S2CLobbyRosterPacket;
import com.persiki84.battlecraft.network.S2CSurrenderVotePacket;
import com.persiki84.battlecraft.network.S2CSyncGamePhasePacket;

import java.util.*;

public class BattleCraftManager {
    private static BattleCraftManager instance;

    public enum GamePhase {
        LOBBY,
        ACTIVE,
        ENDED
    }

    public static class PlayerSession {
        public final UUID uuid;
        public final String teamName;
        public final String ipAddress;

        public PlayerSession(UUID uuid, String teamName, String ipAddress) {
            this.uuid = uuid;
            this.teamName = teamName;
            this.ipAddress = ipAddress;
        }
    }

    public static class ActiveVote {
        public final String teamName;
        public final Set<UUID> yesVotes = new HashSet<>();
        public final Set<UUID> noVotes = new HashSet<>();
        public final long endTime;

        public ActiveVote(String teamName, long durationMs) {
            this.teamName = teamName;
            this.endTime = System.currentTimeMillis() + durationMs;
        }
    }

    private GamePhase phase = GamePhase.LOBBY;
    private final BattleCraftConfig config;
    private final Map<UUID, PlayerSession> sessions = new HashMap<>();
    private final Set<UUID> readyPlayers = new HashSet<>();
    private final Map<String, ActiveVote> activeVotes = new HashMap<>();
    private final Map<String, Long> voteCooldowns = new HashMap<>();
    private final Map<UUID, Long> undecidedSince = new HashMap<>();
    private final Map<UUID, Long> teamSwitchAt = new HashMap<>();
    private final Map<UUID, Long> readyToggleAt = new HashMap<>();
    private static final long REJOIN_CHOICE_MS = 10_000L;

    private final java.util.Set<java.util.UUID> graced = new java.util.HashSet<>();
    private long joinGraceUntil = 0;
    private int lobbyTimer = 0;
    private int lobbyMaxTimer = 0;
    private boolean softDisabled = false;
    private long matchStartTime = 0;

    private BattleCraftManager() {
        this.config = BattleCraftConfig.load();
        this.softDisabled = !config.modEnabled;
        MinecraftForge.EVENT_BUS.register(this);
        resetState();
    }

    public static BattleCraftManager getInstance() {
        if (instance == null) {
            instance = new BattleCraftManager();
        }
        return instance;
    }

    public BattleCraftConfig getConfig() {
        return config;
    }

    public GamePhase getPhase() {
        return phase;
    }

    public boolean isSoftDisabled() {
        return softDisabled;
    }

    // WHY: при выключенном ядре матча нет вовсе, и модули, живущие только в матче, обязаны
    // WHY: работать всегда, а не никогда: иначе без режима матча точки и тайники мертвы
    public boolean matchRunning() {
        return softDisabled || phase == GamePhase.ACTIVE;
    }

    public void setSoftDisabled(boolean disabled) {
        this.softDisabled = disabled;
        config.modEnabled = !disabled;
        config.save();
        if (disabled) {
            resetState();
        }
        if (net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() != null) {
            syncToAll(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
        }
    }

    public void selectTeam(ServerPlayer player, String teamName) {
        if (softDisabled) return;

        MinecraftServer server = player.getServer();
        if (server == null || !acceptsChoices(player)) return;

        PlayerTeam targetTeam = server.getScoreboard().getPlayerTeam(teamName);
        if (targetTeam == null) {
            deny(player, Component.translatable("battlecraft.error.invalid_team"));
            return;
        }
        if (!admits(player, teamName, server)) return;

        server.getScoreboard().addPlayerToTeam(player.getScoreboardName(), targetTeam);
        teamSwitchAt.put(player.getUUID(), System.currentTimeMillis());
        undecidedSince.remove(player.getUUID());
        player.sendSystemMessage(Component.translatable("battlecraft.success.team_selected", teamName).withStyle(ChatFormatting.GREEN));

        checkLobbyStart(server);
        syncToAll(server);
    }

    private boolean acceptsChoices(ServerPlayer player) {
        if (phase == GamePhase.LOBBY) return true;
        if (phase == GamePhase.ACTIVE && player.getTeam() == null) return true;

        deny(player, Component.translatable("battlecraft.error.team_select_locked"));
        return false;
    }

    private boolean admits(ServerPlayer player, String teamName, MinecraftServer server) {
        net.minecraft.world.scores.Team current = player.getTeam();
        if (current != null && current.getName().equals(teamName)) {
            deny(player, Component.translatable("battlecraft.error.already_in_team", teamName));
            return false;
        }

        long cooldown = switchCooldownRemaining(player.getUUID());
        if (current != null && cooldown > 0) {
            deny(player, Component.translatable("battlecraft.error.switch_cooldown", seconds(cooldown)));
            return false;
        }
        if (!LobbyRoster.hasRoom(server, teamPool(server), teamName)) {
            deny(player, Component.translatable("battlecraft.error.team_full", teamName));
            return false;
        }
        return ownsAddress(player);
    }

    private boolean ownsAddress(ServerPlayer player) {
        if (!config.ipLock) return true;

        String ip = getCleanIp(player);
        for (PlayerSession session : sessions.values()) {
            if (session.ipAddress.equals(ip) && !session.uuid.equals(player.getUUID())) {
                deny(player, Component.translatable("battlecraft.error.ip_blocked"));
                return false;
            }
        }
        return true;
    }

    private void deny(ServerPlayer player, Component reason) {
        player.sendSystemMessage(reason.copy().withStyle(ChatFormatting.RED));
    }

    private long switchCooldownRemaining(UUID uuid) {
        Long last = teamSwitchAt.get(uuid);
        if (last == null) return 0;
        return Math.max(0, config.teamSwitchCooldownSeconds * 1000L - (System.currentTimeMillis() - last));
    }

    private long choiceRemaining(ServerPlayer player) {
        if (player.getTeam() != null) return 0;
        Long since = undecidedSince.get(player.getUUID());
        if (since == null) return 0;
        return Math.max(0, choiceDeadline() - (System.currentTimeMillis() - since));
    }

    private List<String> teamPool(MinecraftServer server) {
        return LobbyRoster.pool(server);
    }

    private long choiceDeadline() {
        int limit = phase == GamePhase.ACTIVE ? config.matchJoinChoiceSeconds : config.autoAssignSeconds;
        return limit * 1000L;
    }

    private static int seconds(long millis) {
        return (int) Math.ceil(millis / 1000.0);
    }

    private static int ticks(long millis) {
        return (int) (millis / 50L);
    }

    public void toggleReady(ServerPlayer player) {
        if (softDisabled || phase != GamePhase.LOBBY) return;

        UUID uuid = player.getUUID();
        boolean ready = readyPlayers.contains(uuid);
        if (!ready && player.getTeam() == null) {
            deny(player, Component.translatable("battlecraft.error.select_team_first"));
            return;
        }

        long cooldown = readyCooldownRemaining(uuid);
        if (cooldown > 0) {
            deny(player, Component.translatable("battlecraft.error.ready_cooldown", seconds(cooldown)));
            return;
        }

        if (ready) {
            readyPlayers.remove(uuid);
        } else {
            readyPlayers.add(uuid);
        }
        readyToggleAt.put(uuid, System.currentTimeMillis());

        checkLobbyStart(player.getServer());
        syncToAll(player.getServer());
    }

    private long readyCooldownRemaining(UUID uuid) {
        Long last = readyToggleAt.get(uuid);
        if (last == null) return 0;
        return Math.max(0, config.readyToggleCooldownSeconds * 1000L - (System.currentTimeMillis() - last));
    }

    public void startSurrenderVote(ServerPlayer player) {
        if (softDisabled || phase != GamePhase.ACTIVE) return;

        net.minecraft.world.scores.Team team = player.getTeam();
        if (team == null) return;

        String teamName = team.getName();
        if (!voteAllowed(player, teamName)) return;

        ActiveVote vote = new ActiveVote(teamName, config.surrenderVoteTimeout * 1000L);
        vote.yesVotes.add(player.getUUID());
        activeVotes.put(teamName, vote);

        broadcastToTeam(player.getServer(), teamName, Component.translatable("battlecraft.vote.started", player.getName().getString()).withStyle(ChatFormatting.GOLD));
        syncToAll(player.getServer());
    }

    private boolean voteAllowed(ServerPlayer player, String teamName) {
        long now = System.currentTimeMillis();

        if (now - matchStartTime < config.surrenderMinTime * 1000L) {
            deny(player, Component.translatable("battlecraft.error.surrender_too_early",
                    seconds(config.surrenderMinTime * 1000L - (now - matchStartTime))));
            return false;
        }
        if (voteCooldowns.getOrDefault(teamName, 0L) > now) {
            deny(player, Component.translatable("battlecraft.error.vote_cooldown",
                    seconds(voteCooldowns.get(teamName) - now)));
            return false;
        }
        if (activeVotes.containsKey(teamName)) {
            deny(player, Component.translatable("battlecraft.error.vote_active"));
            return false;
        }
        return true;
    }

    public void castVote(ServerPlayer player, boolean yes) {
        if (softDisabled || phase != GamePhase.ACTIVE) return;

        net.minecraft.world.scores.Team team = player.getTeam();
        if (team == null) return;

        String teamName = team.getName();
        ActiveVote vote = activeVotes.get(teamName);
        if (vote == null) return;

        vote.yesVotes.remove(player.getUUID());
        vote.noVotes.remove(player.getUUID());

        if (yes) {
            vote.yesVotes.add(player.getUUID());
        } else {
            vote.noVotes.add(player.getUUID());
        }

        checkVoteResults(player.getServer(), teamName);
        syncToAll(player.getServer());
    }

    public boolean forceStart(MinecraftServer server) {
        return forceStart(server, server.createCommandSourceStack());
    }

    public boolean forceStart(MinecraftServer server, net.minecraft.commands.CommandSourceStack source) {
        if (phase == GamePhase.ACTIVE) {
            source.sendFailure(Component.translatable("battlecraft.error.match_running"));
            return false;
        }

        MatchRequirements.Status blocking =
                MatchRequirements.firstUnmet(requirements(server), MatchRequirements.Stage.SETUP);
        if (blocking != null) {
            source.sendFailure(blocking.describe());
            return false;
        }

        phase = GamePhase.ACTIVE;
        matchStartTime = System.currentTimeMillis();
        KillRewardMod.setMatchActive(true);

        executeConsoleCommands(server, config.startCommands);

        persistMatch(server);
        syncToAll(server);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new MatchEvent.Started(server));
        return true;
    }

    @SubscribeEvent
    public void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent event) {
        restoreMatch(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        persistMatch(event.getServer());
    }

    // WHY: менеджер живёт всё время процесса, поэтому мир без match.dat обязан обнулить состояние:
    // WHY: иначе в одиночной игре фаза и готовые прошлого мира доезжали в следующий
    private void restoreMatch(MinecraftServer server) {
        MatchStore.Snapshot saved = MatchStore.load(server);
        if (saved == null) {
            resetState();
            return;
        }

        phase = saved.phase();
        matchStartTime = phase == GamePhase.ACTIVE ? System.currentTimeMillis() - saved.elapsedMillis() : 0;
        readyPlayers.clear();
        readyPlayers.addAll(saved.ready());
        KillRewardMod.setMatchActive(phase == GamePhase.ACTIVE);
    }

    private void persistMatch(MinecraftServer server) {
        long elapsed = phase == GamePhase.ACTIVE && matchStartTime > 0
                ? System.currentTimeMillis() - matchStartTime
                : 0L;
        MatchStore.save(server, phase, elapsed, readyPlayers);
    }

    public void stopMatch(MinecraftServer server, String winnerTeam) {
        if (softDisabled) return;
        phase = GamePhase.ENDED;

        executeConsoleCommands(server, config.stopCommands);
        com.persiki84.capturepoints.capture.CapturePointManager.resetAllPoints();

        com.persiki84.minimap.MapManager.clearAll();
        com.persiki84.minimap.network.PlayerPositionSyncPacket emptyPosPacket = new com.persiki84.minimap.network.PlayerPositionSyncPacket(new ArrayList<>());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            com.persiki84.minimap.MapManager.syncMarkers(p);
            com.persiki84.minimap.network.PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), emptyPosPacket);
            
            PlayerTeam team = server.getScoreboard().getPlayersTeam(p.getScoreboardName());
            if (team != null) {
                server.getScoreboard().removePlayerFromTeam(p.getScoreboardName(), team);
            }
        }

        Component msg = winnerTeam != null 
            ? Component.translatable("battlecraft.match.ended.winner", winnerTeam).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            : Component.translatable("battlecraft.match.ended").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);

         server.getPlayerList().broadcastSystemMessage(msg, false);

         resetState();
         persistMatch(server);
         syncToAll(server);
         net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new MatchEvent.Ended(server));
    }

    private void executeConsoleCommands(MinecraftServer server, List<String> commands) {
        if (server == null || commands == null) return;
        var source = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        for (String cmd : commands) {
            server.getCommands().performPrefixedCommand(source, cmd);
        }
    }

    private void resetState() {
        phase = GamePhase.LOBBY;
        sessions.clear();
        readyPlayers.clear();
        activeVotes.clear();
        voteCooldowns.clear();
        undecidedSince.clear();
        teamSwitchAt.clear();
        readyToggleAt.clear();
        graced.clear();
        joinGraceUntil = 0;
        lobbyTimer = 0;
        lobbyMaxTimer = 0;
        KillRewardMod.setMatchActive(false);
    }

    private void checkLobbyStart(MinecraftServer server) {
        int readyCount = countReady(server);
        int totalPlayers = server.getPlayerList().getPlayerCount();

        if (holdsCountdown(server) || readyCount < 1) {
            stopCountdown();
            return;
        }

        if (readyCount == totalPlayers) {
            startCountdown(config.lobbyFastStartTime * 20, true);
            return;
        }
        startCountdown(config.lobbyTimeLimit * 20, false);
    }

    public int readyCount(MinecraftServer server) {
        return countReady(server);
    }

    public void clearJoinGrace() {
        joinGraceUntil = 0L;
    }

    public int graceSecondsLeft() {
        return (int) Math.max(0L, (joinGraceUntil - System.currentTimeMillis()) / 1000L);
    }

    private int countReady(MinecraftServer server) {
        int readyCount = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getTeam() != null && readyPlayers.contains(player.getUUID())) readyCount++;
        }
        return readyCount;
    }

    private void stopCountdown() {
        lobbyTimer = 0;
        lobbyMaxTimer = 0;
    }

    private void startCountdown(int ticks, boolean shortens) {
        if (lobbyTimer > 0 && !(shortens && lobbyTimer > ticks)) return;

        lobbyTimer = ticks;
        lobbyMaxTimer = ticks;
    }

    private boolean holdsCountdown(MinecraftServer server) {
        List<MatchRequirements.Status> statuses = requirements(server);
        return !MatchRequirements.satisfied(statuses, MatchRequirements.Stage.SETUP)
                || !MatchRequirements.satisfied(statuses, MatchRequirements.Stage.LOBBY);
    }

    public List<MatchRequirements.Status> requirements(MinecraftServer server) {
        return MatchRequirements.check(server, this);
    }

    // WHY: грейс продлевался каждым входом, поэтому один перезаходящий игрок обнулял отсчёт
    // WHY: бесконечно; метка выбора команды, наоборот, не пережидала перезаход и швыряла в команду
    // WHY: мгновенно - теперь она лишь не даёт вернувшемуся меньше REJOIN_CHOICE_MS на решение
    public void noteJoin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || phase == GamePhase.ENDED) return;

        long now = System.currentTimeMillis();
        long earliest = now - Math.max(0L, choiceDeadline() - REJOIN_CHOICE_MS);
        undecidedSince.merge(player.getUUID(), now, (stored, fresh) -> Math.max(stored, earliest));
        if (phase != GamePhase.LOBBY || !graced.add(player.getUUID())) return;

        joinGraceUntil = now + config.joinGraceSeconds * 1000L;
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("battlecraft.lobby.waiting_for", player.getName().getString())
                        .withStyle(ChatFormatting.YELLOW), false);
    }

    private void assignOverduePlayers(MinecraftServer server) {
        if (teamPool(server).isEmpty()) return;
        long now = System.currentTimeMillis();

        for (ServerPlayer player : LobbyRoster.withoutTeam(server, teamPool(server))) {
            Long since = undecidedSince.putIfAbsent(player.getUUID(), now);
            if (since == null || now - since < choiceDeadline()) continue;

            assignToNeediest(server, player);
        }
    }

    private void assignToNeediest(MinecraftServer server, ServerPlayer player) {
        String team = LobbyRoster.neediestTeam(server, teamPool(server));
        if (team == null) return;

        server.getScoreboard().addPlayerToTeam(player.getScoreboardName(),
                server.getScoreboard().getPlayerTeam(team));
        undecidedSince.remove(player.getUUID());
        teamSwitchAt.put(player.getUUID(), System.currentTimeMillis());
        player.sendSystemMessage(Component.translatable("battlecraft.lobby.auto_assigned", team)
                .withStyle(ChatFormatting.YELLOW));
        syncToAll(server);
    }

    private void autoBalanceTeams(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        Map<PlayerTeam, Integer> teamCounts = countTeams(scoreboard, server);

        List<ServerPlayer> unassigned = LobbyRoster.withoutTeam(server, teamPool(server));
        Collections.shuffle(unassigned);

        for (ServerPlayer player : unassigned) {
            PlayerTeam smallest = smallestTeam(teamCounts);
            if (smallest == null) return;

            scoreboard.addPlayerToTeam(player.getScoreboardName(), smallest);
            teamCounts.put(smallest, teamCounts.get(smallest) + 1);
            undecidedSince.remove(player.getUUID());
        }
    }

    private Map<PlayerTeam, Integer> countTeams(Scoreboard scoreboard, MinecraftServer server) {
        Map<PlayerTeam, Integer> counts = new HashMap<>();
        for (String teamName : teamPool(server)) {
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team != null) counts.put(team, 0);
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            net.minecraft.world.scores.Team team = player.getTeam();
            if (team == null) continue;

            PlayerTeam playerTeam = scoreboard.getPlayerTeam(team.getName());
            if (playerTeam != null && counts.containsKey(playerTeam)) {
                counts.put(playerTeam, counts.get(playerTeam) + 1);
            }
        }
        return counts;
    }

    private static PlayerTeam smallestTeam(Map<PlayerTeam, Integer> counts) {
        PlayerTeam smallest = null;
        int min = Integer.MAX_VALUE;
        for (Map.Entry<PlayerTeam, Integer> entry : counts.entrySet()) {
            if (entry.getValue() < min) {
                min = entry.getValue();
                smallest = entry.getKey();
            }
        }
        return smallest;
    }

    private void checkVoteResults(MinecraftServer server, String teamName) {
        checkVoteResults(server, teamName, null);
    }

    // WHY: Forge шлёт выход до удаления из PlayerList, поэтому уходящий ещё числился онлайн и
    // WHY: голосование за сдачу зависало до таймаута, хотя все оставшиеся уже проголосовали
    private void checkVoteResults(MinecraftServer server, String teamName, java.util.UUID leaving) {
        ActiveVote vote = activeVotes.get(teamName);
        if (vote == null) return;

        Set<UUID> present = presentMembers(server, teamName, leaving);
        vote.yesVotes.retainAll(present);
        vote.noVotes.retainAll(present);
        if (present.isEmpty()) {
            activeVotes.remove(teamName);
            return;
        }

        if (vote.noVotes.size() > 0) {
            activeVotes.remove(teamName);
            voteCooldowns.put(teamName, System.currentTimeMillis() + config.voteCooldown * 1000L);
            server.getPlayerList().broadcastSystemMessage(Component.translatable("battlecraft.vote.failed", teamName).withStyle(ChatFormatting.RED), false);
            return;
        }

        if (vote.yesVotes.size() >= present.size()) {
            activeVotes.remove(teamName);
            server.getPlayerList().broadcastSystemMessage(Component.translatable("battlecraft.vote.surrendered", teamName).withStyle(ChatFormatting.RED), false);
            executeConsoleCommands(server, config.surrenderCommands);
            String otherTeam = getOtherTeam(server, teamName);
            stopMatch(server, otherTeam);
        }
    }

    private Set<UUID> presentMembers(MinecraftServer server, String teamName, UUID leaving) {
        Set<UUID> present = new HashSet<>();
        for (ServerPlayer member : getOnlineTeamPlayers(server, teamName)) {
            if (!member.getUUID().equals(leaving)) present.add(member.getUUID());
        }
        return present;
    }

    private String getOtherTeam(MinecraftServer server, String teamName) {
        if (server == null) return null;
        String winner = null;
        int most = 0;
        for (Map.Entry<String, List<ServerPlayer>> entry : LobbyRoster.byTeam(server, LobbyRoster.pool(server)).entrySet()) {
            int online = entry.getValue().size();
            if (entry.getKey().equals(teamName) || online <= most) continue;

            winner = entry.getKey();
            most = online;
        }
        return winner;
    }

    private List<ServerPlayer> getOnlineTeamPlayers(MinecraftServer server, String teamName) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            net.minecraft.world.scores.Team team = player.getTeam();
            if (team != null && team.getName().equals(teamName)) {
                list.add(player);
            }
        }
        return list;
    }

    private void broadcastToTeam(MinecraftServer server, String teamName, Component message) {
        if (server == null) return;
        for (ServerPlayer player : getOnlineTeamPlayers(server, teamName)) {
            player.sendSystemMessage(message);
        }
    }

    private String getCleanIp(ServerPlayer player) {
        java.net.SocketAddress address = player.connection.connection.getRemoteAddress();
        if (address instanceof java.net.InetSocketAddress inet && inet.getAddress() != null) {
            return inet.getAddress().getHostAddress();
        }
        return address.toString();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPvP(LivingAttackEvent event) {
        if (softDisabled) return;
        if (phase == GamePhase.LOBBY) {
            if (event.getEntity() instanceof Player && event.getSource().getEntity() instanceof Player) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (softDisabled) return;
        if (phase == GamePhase.LOBBY && !event.getPlayer().isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (softDisabled) return;
        if (phase == GamePhase.LOBBY && event.getEntity() instanceof Player player && !player.isCreative()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (softDisabled) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;

        noteJoin(player);
        PlayerSession session = sessions.remove(player.getUUID());
        if (session != null) {
            restoreSession(server, player, session);
        } else if (phase == GamePhase.LOBBY) {
            leaveTeam(server, player);
        }
        syncToAll(server);
    }

    private void restoreSession(MinecraftServer server, ServerPlayer player, PlayerSession session) {
        PlayerTeam team = server.getScoreboard().getPlayerTeam(session.teamName);
        if (team != null) {
            server.getScoreboard().addPlayerToTeam(player.getScoreboardName(), team);
        }
        undecidedSince.remove(player.getUUID());
    }

    private void leaveTeam(MinecraftServer server, ServerPlayer player) {
        PlayerTeam team = server.getScoreboard().getPlayersTeam(player.getScoreboardName());
        if (team == null) return;

        server.getScoreboard().removePlayerFromTeam(player.getScoreboardName(), team);
        readyPlayers.remove(player.getUUID());
        readyToggleAt.remove(player.getUUID());
    }

    @SubscribeEvent
    public void onCommand(net.minecraftforge.event.CommandEvent event) {
        if (softDisabled) return;

        net.minecraft.commands.CommandSourceStack source = event.getParseResults().getContext().getSource();
        if (source.hasPermission(4)) return;
        if (!changesTeamMembership(event.getParseResults().getReader().getString())) return;

        event.setCanceled(true);
        source.sendFailure(Component.translatable("battlecraft.error.team_command_blocked"));
    }

    private static boolean changesTeamMembership(String command) {
        String text = command.trim();
        if (text.startsWith("/")) text = text.substring(1);
        if (text.startsWith("minecraft:")) text = text.substring("minecraft:".length());

        String[] parts = text.split("\\s+");
        if (parts.length < 2 || !parts[0].equals("team")) return false;
        return parts[1].equals("join") || parts[1].equals("leave") || parts[1].equals("empty");
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (softDisabled) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            if (phase == GamePhase.ACTIVE) {
                net.minecraft.world.scores.Team team = player.getTeam();
                if (team != null) {
                    String ip = getCleanIp(player);
                    sessions.put(player.getUUID(), new PlayerSession(player.getUUID(), team.getName(), ip));

                    MinecraftServer server = player.getServer();
                    if (server != null) {
                        checkVoteResults(server, team.getName(), player.getUUID());
                    }
                }
            }
            MinecraftServer server = player.getServer();
            if (server != null) {
                syncToAll(server);
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (softDisabled || event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = event.getServer();
        if (server == null) return;

        if (server.getTickCount() % 20 == 0 && phase != GamePhase.ENDED) {
            assignOverduePlayers(server);
            checkLobbyStart(server);
            syncToAll(server);
        }

        tickLobbyCountdown(server);
        expireVotes(server);
    }

    private void tickLobbyCountdown(MinecraftServer server) {
        if (phase != GamePhase.LOBBY || lobbyTimer <= 0) return;

        lobbyTimer--;
        int secs = lobbyTimer / 20;
        if (lobbyTimer % 20 == 0 && (secs <= 5 || secs % 10 == 0)) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("battlecraft.lobby.countdown", secs).withStyle(ChatFormatting.YELLOW), false);
        }

        if (lobbyTimer <= 0) {
            autoBalanceTeams(server);
            if (!forceStart(server)) lobbyTimer = 0;
        }
    }

    private void expireVotes(MinecraftServer server) {
        long now = System.currentTimeMillis();
        boolean changed = false;

        Iterator<Map.Entry<String, ActiveVote>> it = activeVotes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ActiveVote> entry = it.next();
            if (now < entry.getValue().endTime) continue;

            voteCooldowns.put(entry.getKey(), now + config.voteCooldown * 1000L);
            it.remove();
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("battlecraft.vote.timeout", entry.getKey()).withStyle(ChatFormatting.RED), false);
            changed = true;
        }
        if (changed) syncToAll(server);
    }

private void syncRoster(MinecraftServer server) {
        if (server == null) return;

        List<S2CLobbyRosterPacket.Member> members = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            net.minecraft.world.scores.Team team = player.getTeam();
            String teamName = team == null ? null : team.getName();
            members.add(new S2CLobbyRosterPacket.Member(player.getUUID(), player.getName().getString(),
                    teamName, readyPlayers.contains(player.getUUID())));
        }

        List<String> teams = teamPool(server);
        S2CLobbyRosterPacket packet = new S2CLobbyRosterPacket(teams, members,
                LobbyRoster.slotsPerTeam(server, teams));
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), packet);
    }

    public void syncToPlayer(ServerPlayer player) {
        long now = System.currentTimeMillis();
        int matchTicks = phase == GamePhase.ACTIVE && matchStartTime > 0 ? ticks(now - matchStartTime) : 0;

        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncGamePhasePacket(phase, softDisabled, readyPlayers.contains(player.getUUID()),
                        lobbyTimer, lobbyMaxTimer, ticks(Math.max(0, joinGraceUntil - now)),
                        ticks(choiceRemaining(player)), ticks(switchCooldownRemaining(player.getUUID())),
                        ticks(readyCooldownRemaining(player.getUUID())), matchTicks, missingNames(player)));

        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), voteStateFor(player));
    }

    private String missingNames(ServerPlayer player) {
        if (phase != GamePhase.LOBBY || player.getServer() == null) return "";

        List<String> missing = new ArrayList<>();
        for (ServerPlayer other : player.getServer().getPlayerList().getPlayers()) {
            if (other.getTeam() == null || !readyPlayers.contains(other.getUUID())) {
                missing.add(other.getScoreboardName());
            }
        }
        return String.join(", ", missing);
    }

    private S2CSurrenderVotePacket voteStateFor(ServerPlayer player) {
        net.minecraft.world.scores.Team team = player.getTeam();
        ActiveVote vote = team == null ? null : activeVotes.get(team.getName());
        if (vote == null) return new S2CSurrenderVotePacket(false, "", 0, 0, 0);

        return new S2CSurrenderVotePacket(true, vote.teamName, vote.yesVotes.size(),
                getOnlineTeamPlayers(player.getServer(), team.getName()).size(),
                ticks(Math.max(0, vote.endTime - System.currentTimeMillis())));
    }

    public void syncToAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncToPlayer(player);
        }
        syncRoster(server);
    }
}
