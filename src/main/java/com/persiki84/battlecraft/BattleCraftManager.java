package com.persiki84.battlecraft;

import com.persiki84.killreward.KillRewardMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
        public final CompoundTag inventoryData;
        public final String ipAddress;

        public PlayerSession(UUID uuid, String teamName, CompoundTag inventoryData, String ipAddress) {
            this.uuid = uuid;
            this.teamName = teamName;
            this.inventoryData = inventoryData;
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
    private int lobbyTimer = 0;
    private int lobbyMaxTimer = 0;
    private boolean softDisabled = false;
    private long matchStartTime = 0;

    private BattleCraftManager() {
        this.config = BattleCraftConfig.load();
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

    public void setSoftDisabled(boolean disabled) {
        this.softDisabled = disabled;
        if (disabled) {
            resetState();
        }
        if (net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer() != null) {
            syncToAll(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer());
        }
    }

    public void selectTeam(ServerPlayer player, String teamName) {
        if (softDisabled) return;
        if (phase != GamePhase.LOBBY) {
            player.sendSystemMessage(Component.translatable("battlecraft.error.team_select_locked").withStyle(ChatFormatting.RED));
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) return;

        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam targetTeam = scoreboard.getPlayerTeam(teamName);
        if (targetTeam == null) {
            player.sendSystemMessage(Component.translatable("battlecraft.error.invalid_team").withStyle(ChatFormatting.RED));
            return;
        }

        String ip = getCleanIp(player);
        for (PlayerSession session : sessions.values()) {
            if (session.ipAddress.equals(ip) && !session.uuid.equals(player.getUUID())) {
                player.sendSystemMessage(Component.translatable("battlecraft.error.ip_blocked").withStyle(ChatFormatting.RED));
                return;
            }
        }

        scoreboard.addPlayerToTeam(player.getScoreboardName(), targetTeam);
        player.sendSystemMessage(Component.translatable("battlecraft.success.team_selected", teamName).withStyle(ChatFormatting.GREEN));

        checkLobbyStart(server);
        syncToAll(server);
    }

    public void toggleReady(ServerPlayer player) {
        if (softDisabled || phase != GamePhase.LOBBY) return;
        UUID uuid = player.getUUID();
        if (readyPlayers.contains(uuid)) {
            readyPlayers.remove(uuid);
        } else {
            if (player.getTeam() != null) {
                readyPlayers.add(uuid);
            } else {
                player.sendSystemMessage(Component.translatable("battlecraft.error.select_team_first").withStyle(ChatFormatting.RED));
                return;
            }
        }
        checkLobbyStart(player.getServer());
        syncToAll(player.getServer());
    }

    public void startSurrenderVote(ServerPlayer player) {
        if (softDisabled || phase != GamePhase.ACTIVE) return;

        net.minecraft.world.scores.Team team = player.getTeam();
        if (team == null) return;

        String teamName = team.getName();
        long now = System.currentTimeMillis();

        if (now - matchStartTime < config.surrenderMinTime * 1000L) {
            long remaining = (config.surrenderMinTime * 1000L - (now - matchStartTime)) / 1000L;
            player.sendSystemMessage(Component.translatable("battlecraft.error.surrender_too_early", remaining).withStyle(ChatFormatting.RED));
            return;
        }

        if (voteCooldowns.getOrDefault(teamName, 0L) > now) {
            long remaining = (voteCooldowns.get(teamName) - now) / 1000L;
            player.sendSystemMessage(Component.translatable("battlecraft.error.vote_cooldown", remaining).withStyle(ChatFormatting.RED));
            return;
        }

        if (activeVotes.containsKey(teamName)) {
            player.sendSystemMessage(Component.translatable("battlecraft.error.vote_active").withStyle(ChatFormatting.RED));
            return;
        }

        ActiveVote vote = new ActiveVote(teamName, config.surrenderVoteTimeout * 1000L);
        vote.yesVotes.add(player.getUUID());
        activeVotes.put(teamName, vote);

        broadcastToTeam(player.getServer(), teamName, Component.translatable("battlecraft.vote.started", player.getName().getString()).withStyle(ChatFormatting.GOLD));
        syncToAll(player.getServer());
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
        if (softDisabled) return false;

        if (com.persiki84.capturepoints.capture.CapturePointManager.getAllPoints().isEmpty() ||
            com.persiki84.capturepoints.capture.CapturePointManager.getAllFinalPoints().isEmpty()) {
            source.sendFailure(Component.translatable("battlecraft.error.no_points_setup"));
            return false;
        }

        int teamCount = server.getScoreboard().getPlayerTeams().size();
        if (teamCount < 2) {
            source.sendFailure(Component.translatable("battlecraft.error.not_enough_teams"));
            return false;
        }

        phase = GamePhase.ACTIVE;
        matchStartTime = System.currentTimeMillis();
        KillRewardMod.modEnabled = true;

        executeConsoleCommands(server, config.startCommands);

        server.getPlayerList().broadcastSystemMessage(Component.translatable("battlecraft.match.started").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
        syncToAll(server);
        return true;
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
         syncToAll(server);
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
        lobbyTimer = 0;
        lobbyMaxTimer = 0;
        KillRewardMod.modEnabled = false;
    }

    private void checkLobbyStart(MinecraftServer server) {
        int readyCount = 0;
        int totalPlayers = server.getPlayerList().getPlayerCount();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getTeam() != null && readyPlayers.contains(player.getUUID())) {
                readyCount++;
            }
        }

        if (readyCount >= 1 && readyCount == totalPlayers) {
            if (lobbyTimer > config.lobbyFastStartTime * 20 || lobbyTimer <= 0) {
                lobbyTimer = config.lobbyFastStartTime * 20;
                lobbyMaxTimer = lobbyTimer;
                syncToAll(server);
            }
        } else {
            if (readyCount >= 1) {
                if (lobbyTimer <= 0) {
                    lobbyTimer = config.lobbyTimeLimit * 20;
                    lobbyMaxTimer = lobbyTimer;
                    syncToAll(server);
                }
            } else {
                if (lobbyTimer > 0) {
                    lobbyTimer = 0;
                    lobbyMaxTimer = 0;
                    syncToAll(server);
                }
            }
        }
    }

    private void autoBalanceTeams(MinecraftServer server) {
        List<ServerPlayer> unassigned = new ArrayList<>();
        Map<PlayerTeam, Integer> teamCounts = new HashMap<>();
        
        Scoreboard scoreboard = server.getScoreboard();
        for (String teamName : config.teams) {
            PlayerTeam pt = scoreboard.getPlayerTeam(teamName);
            if (pt != null) {
                teamCounts.put(pt, 0);
            }
        }
        
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getTeam() != null && readyPlayers.contains(p.getUUID())) {
                PlayerTeam pt = scoreboard.getPlayerTeam(p.getTeam().getName());
                if (pt != null && teamCounts.containsKey(pt)) {
                    teamCounts.put(pt, teamCounts.get(pt) + 1);
                }
            } else {
                unassigned.add(p);
            }
        }
        
        Collections.shuffle(unassigned);
        for (ServerPlayer p : unassigned) {
            PlayerTeam smallest = null;
            int min = Integer.MAX_VALUE;
            for (Map.Entry<PlayerTeam, Integer> e : teamCounts.entrySet()) {
                if (e.getValue() < min) {
                    min = e.getValue();
                    smallest = e.getKey();
                }
            }
            if (smallest != null) {
                scoreboard.addPlayerToTeam(p.getScoreboardName(), smallest);
                teamCounts.put(smallest, min + 1);
            }
        }
    }

    private void checkVoteResults(MinecraftServer server, String teamName) {
        ActiveVote vote = activeVotes.get(teamName);
        if (vote == null) return;

        List<ServerPlayer> teamPlayers = getOnlineTeamPlayers(server, teamName);
        int onlineCount = teamPlayers.size();

        if (vote.noVotes.size() > 0) {
            activeVotes.remove(teamName);
            voteCooldowns.put(teamName, System.currentTimeMillis() + config.voteCooldown * 1000L);
            server.getPlayerList().broadcastSystemMessage(Component.translatable("battlecraft.vote.failed", teamName).withStyle(ChatFormatting.RED), false);
            return;
        }

        if (vote.yesVotes.size() >= onlineCount) {
            activeVotes.remove(teamName);
            server.getPlayerList().broadcastSystemMessage(Component.translatable("battlecraft.vote.surrendered", teamName).withStyle(ChatFormatting.RED), false);
            executeConsoleCommands(server, config.surrenderCommands);
            String otherTeam = getOtherTeam(server, teamName);
            stopMatch(server, otherTeam);
        }
    }

    private String getOtherTeam(MinecraftServer server, String teamName) {
        if (server == null) return null;
        for (var team : server.getScoreboard().getPlayerTeams()) {
            if (!team.getName().equals(teamName)) return team.getName();
        }
        return null;
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
        String ip = player.connection.connection.getRemoteAddress().toString();
        if (ip.contains("/")) {
            ip = ip.substring(ip.indexOf("/") + 1);
        }
        if (ip.contains(":")) {
            ip = ip.substring(0, ip.indexOf(":"));
        }
        return ip;
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
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            PlayerSession session = sessions.get(uuid);

            if (session != null) {
                MinecraftServer server = player.getServer();
                if (server != null) {
                    Scoreboard scoreboard = server.getScoreboard();
                    PlayerTeam team = scoreboard.getPlayerTeam(session.teamName);
                    if (team != null) {
                        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
                    }
                }
                if (session.inventoryData != null) {
                    player.getInventory().load(session.inventoryData.getList("Inventory", 10));
                }
                sessions.remove(uuid);
            }
            syncToPlayer(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (softDisabled) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            if (phase == GamePhase.ACTIVE) {
                net.minecraft.world.scores.Team team = player.getTeam();
                if (team != null) {
                    ListTag invList = player.getInventory().save(new ListTag());
                    CompoundTag tag = new CompoundTag();
                    tag.put("Inventory", invList);

                    String ip = getCleanIp(player);
                    sessions.put(player.getUUID(), new PlayerSession(player.getUUID(), team.getName(), tag, ip));

                    MinecraftServer server = player.getServer();
                    if (server != null) {
                        checkVoteResults(server, team.getName());
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

        if (phase == GamePhase.LOBBY && lobbyTimer > 0) {
            lobbyTimer--;
            if (lobbyTimer % 20 == 0) {
                int secs = lobbyTimer / 20;
                if (secs <= 5 || secs % 10 == 0) {
                    server.getPlayerList().broadcastSystemMessage(Component.translatable("battlecraft.lobby.countdown", secs).withStyle(ChatFormatting.YELLOW), false);
                }
                syncToAll(server);
            }
            if (lobbyTimer <= 0) {
                autoBalanceTeams(server);
                if (!forceStart(server)) {
                    lobbyTimer = 0;
                }
            }
        }

        long now = System.currentTimeMillis();
        boolean voteChanged = false;
        Iterator<Map.Entry<String, ActiveVote>> it = activeVotes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ActiveVote> entry = it.next();
            ActiveVote vote = entry.getValue();
            if (now >= vote.endTime) {
                voteCooldowns.put(entry.getKey(), now + config.voteCooldown * 1000L);
                it.remove();
                server.getPlayerList().broadcastSystemMessage(Component.translatable("battlecraft.vote.timeout", entry.getKey()).withStyle(ChatFormatting.RED), false);
                voteChanged = true;
            }
        }
        if (voteChanged) {
            syncToAll(server);
        }
    }

    public void syncToPlayer(ServerPlayer player) {
        net.minecraft.world.scores.Team team = player.getTeam();
        String teamName = team != null ? team.getName() : "";
        ActiveVote vote = activeVotes.get(teamName);
        boolean hasVote = vote != null;
        String vTeam = hasVote ? vote.teamName : "";
        int yes = hasVote ? vote.yesVotes.size() : 0;
        int req = hasVote ? getOnlineTeamPlayers(player.getServer(), teamName).size() : 0;
        long end = hasVote ? vote.endTime : 0;

        List<String> missing = new ArrayList<>();
        boolean isReady = readyPlayers.contains(player.getUUID());
        if (phase == GamePhase.LOBBY && player.getServer() != null) {
            for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                if (p.getTeam() == null || !readyPlayers.contains(p.getUUID())) {
                    missing.add(p.getScoreboardName());
                }
            }
        }
        String missingStr = String.join(", ", missing);

        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncGamePhasePacket(phase, hasVote, vTeam, yes, req, end, softDisabled, lobbyTimer, lobbyMaxTimer, missingStr, isReady));
    }

    public void syncToAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncToPlayer(player);
        }
    }
}
