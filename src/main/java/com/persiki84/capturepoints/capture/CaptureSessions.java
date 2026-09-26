package com.persiki84.capturepoints.capture;

import com.persiki84.battlecraft.rules.GameRule;
import com.persiki84.battlecraft.rules.GameRules;
import com.persiki84.capturepoints.network.CaptureUpdatePacket;
import com.persiki84.knockdown.cap.KnockdownCapability;
import com.persiki84.knockdown.cap.KnockdownProvider;
import com.persiki84.capturepoints.network.PacketHandler;
import com.persiki84.immortality.event.ImmortalityHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CaptureSessions {
    private static final double EDGE_TOLERANCE = 0.5;
    private static final int SYNC_TICKS = 4;
    private static final int GLOW_REFRESH_TICKS = 10;
    private static final int GLOW_DURATION_TICKS = 30;
    private static final int RETURN_HINT_TICKS = 20;
    private static final int DEFEND_WARN_TICKS = 20;
    private static final int MAX_SYNCED_MEMBERS = 32;
    private static final double KILL_APPROACH = 24.0;

    private static final Map<String, CaptureSession> sessions = new HashMap<>();
    private static final List<UUID> syncedMembers = new ArrayList<>();
    private static final List<String> scratchNames = new ArrayList<>();

    private static String[] scratchTeams = new String[8];
    private static int[] scratchCounts = new int[8];
    private static int scratchSize;
    private static int presentRivals;
    private static long ticks;

    private CaptureSessions() {}

    public static boolean isCapturing(UUID playerId) {
        for (CaptureSession session : sessions.values()) {
            if (session.holds(playerId)) return true;
        }
        return false;
    }

    // WHY: клиент рисует захват только в идущем матче, а сервер тикал его всегда: в лобби команда
    // WHY: молча забирала точки вместе с доходом и баффами, а финальная кончала матч до старта
    public static void tick(MinecraftServer server) {
        if (!MatchState.running()) {
            cancelAll();
            return;
        }

        ticks++;
        advanceSessions(server);
        armPoints(server);
        if (ticks % DEFEND_WARN_TICKS == 0) warnDefenders(server);
        if (ticks % SYNC_TICKS == 0) broadcastAll();
    }

    // WHY: захват финальной точки заканчивает матч, а тот сбрасывает все сессии изнутри этого же
    // WHY: обхода, поэтому идём по снимку имён и снимаем запись по ключу, а не итератором карты
    private static void advanceSessions(MinecraftServer server) {
        scratchNames.clear();
        scratchNames.addAll(sessions.keySet());

        for (int index = 0; index < scratchNames.size(); index++) {
            String pointName = scratchNames.get(index);
            CaptureSession session = sessions.get(pointName);
            if (session == null || advance(session, server)) continue;

            if (sessions.remove(pointName, session)) finish(session, server);
        }
    }

    private static boolean advance(CaptureSession session, MinecraftServer server) {
        CapturePoint point = session.getPoint();
        ServerLevel level = CapturePointManager.levelOf(point);
        if (level == null) return false;
        if (point instanceof FinalCapturePoint && !CapturePointManager.isFinalPointAvailable()) return false;
        if (point.getMode() != session.getMode()) return false;

        scan(session, level, server);
        return session.isRunning() ? run(session, server) : arm(session, server);
    }

    private static void finish(CaptureSession session, MinecraftServer server) {
        stripGlow(session, server);
        if (session.isRunning() && !capturedByInitiator(session)) {
            TeamCooldowns.charge(session.getInitiatorTeam(), session.getPoint());
        }
        broadcast(session, false);
    }

    private static boolean capturedByInitiator(CaptureSession session) {
        return session.isComplete() && session.getAttackerTeam().equals(session.getInitiatorTeam());
    }

    private static void scan(CaptureSession session, ServerLevel level, MinecraftServer server) {
        session.beginScan();
        presentRivals = 0;

        ServerPlayer heir = null;
        for (ServerPlayer player : level.players()) {
            String team = standingTeam(session, player);
            if (team == null) continue;

            if (!team.equals(session.getAttackerTeam())) presentRivals++;
            if (heir == null && heirWanted(session, team)) heir = player;
            if (admits(session, player, team)) enlist(session, player, team);
        }

        if (heir != null && needsCaptor(session)) adopt(session, heir, server);
        age(session, server);
    }

    private static void countMembers(CaptureSession session) {
        resetScratch();
        for (CaptureSession.Member member : session.getMembers().values()) {
            if (member.marked()) countTeam(member.team());
        }
    }

    private static String standingTeam(CaptureSession session, ServerPlayer player) {
        if (player.isSpectator() || !player.isAlive() || downed(player)) return null;

        String team = CapturePointManager.teamOf(player);
        if (team == null) return null;
        if (!session.getPoint().getArea().contains(player.getX(), player.getY(), player.getZ(), EDGE_TOLERANCE)) {
            return null;
        }
        return team;
    }

    private static boolean heirWanted(CaptureSession session, String team) {
        return session.getMode().solo() && team.equals(session.getAttackerTeam());
    }

    private static boolean needsCaptor(CaptureSession session) {
        if (!session.getMode().solo()) return false;
        if (!session.getMode().startsByPresence() && !session.getMode().relays()) return false;
        return session.getSoloCaptor() == null || !session.holds(session.getSoloCaptor());
    }

    private static void adopt(CaptureSession session, ServerPlayer heir, MinecraftServer server) {
        if (busyElsewhere(session, heir.getUUID())) return;

        session.setSoloCaptor(heir.getUUID());
        enlist(session, heir, CapturePointManager.teamOf(heir));
        session.setWipeAnnounced(false);
        glow(heir);
        announce(server, Component.translatable("capturepoints.capture.handed_over",
                Component.literal(heir.getName().getString()).withStyle(ChatFormatting.AQUA),
                Component.literal(session.getPoint().getName()).withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.YELLOW));
    }

    private static boolean admits(CaptureSession session, ServerPlayer player, String team) {
        if (busyElsewhere(session, player.getUUID())) return false;
        if (session.getMode().solo()) return player.getUUID().equals(session.getSoloCaptor());
        if (session.getMode().teamWide()) return team.equals(session.getAttackerTeam());
        return true;
    }

    // WHY: бессмертие после возрождения дано для отхода с базы, а не чтобы неуязвимым стоять на
    // WHY: точке: участник захвата его теряет, как при атаке
    private static void enlist(CaptureSession session, ServerPlayer player, String team) {
        session.mark(player.getUUID(), team);
        ImmortalityHandler.revoke(player, "immortality.lost.capture");
    }

    private static boolean busyElsewhere(CaptureSession session, UUID playerId) {
        for (CaptureSession other : sessions.values()) {
            if (other == session) continue;
            if (playerId.equals(other.getSoloCaptor()) && other.holds(playerId)) return true;
        }
        return false;
    }

    private static void age(CaptureSession session, MinecraftServer server) {
        Iterator<Map.Entry<UUID, CaptureSession.Member>> members = session.getMembers().entrySet().iterator();
        while (members.hasNext()) {
            Map.Entry<UUID, CaptureSession.Member> entry = members.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

            if (entry.getValue().marked()) {
                keepMember(session, player, entry.getValue());
                continue;
            }
            if (!lingers(session, entry.getValue(), player)) {
                members.remove();
                stripGlow(player);
            }
        }
    }

    private static void keepMember(CaptureSession session, ServerPlayer player, CaptureSession.Member member) {
        if (player == null) return;
        if (ticks % GLOW_REFRESH_TICKS == 0) glow(player);
        if (session.isRunning()) session.getLedger().credit(player.getUUID(), member.team(), 1.0f);
    }

    private static boolean lingers(CaptureSession session, CaptureSession.Member member, ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator() || downed(player)) return false;

        member.ageOutside();
        if (member.expired()) {
            player.sendSystemMessage(Component.translatable(session.getMode().solo()
                    ? "capturepoints.capture.cancelled_left_zone"
                    : "capturepoints.capture.cancelled_for_you").withStyle(ChatFormatting.RED));
            return false;
        }
        if ((member.ticksOutside() - 1) % RETURN_HINT_TICKS == 0) {
            player.sendSystemMessage(Component.translatable("capturepoints.capture.return_to_zone",
                    member.secondsLeftOutside()).withStyle(ChatFormatting.YELLOW));
        }
        return true;
    }

    private static boolean arm(CaptureSession session, MinecraftServer server) {
        if (!hasStarter(session)) return false;

        session.addArming(1.0f);
        if (!session.isArmed()) return true;

        session.setRunning(true);
        announceStart(session, server);
        return true;
    }

    private static boolean hasStarter(CaptureSession session) {
        String owner = session.getPoint().getOwnerTeam();
        for (CaptureSession.Member member : session.getMembers().values()) {
            if (!member.marked()) continue;
            if (member.team().equals(owner)) continue;
            if (TeamCooldowns.blocked(member.team(), session.getPoint().getName())) continue;
            return true;
        }
        return false;
    }

    // WHY: вышедшему из зоны даётся время вернуться, и всё это время в зоне никого нет: прогресс
    // WHY: обязан стоять, иначе точка дозахватывается сама, пока игрок бежит обратно
    private static boolean run(CaptureSession session, MinecraftServer server) {
        countMembers(session);
        int attackers = teamCount(session.getAttackerTeam());
        session.setCounts(attackers, totalPresent() - attackers);

        session.setHeld(frozen(session));
        if (session.isHeld()) {
            session.setDecaying(false);
            return true;
        }

        float rate = rate(session, attackers);
        session.setDecaying(rate < 0.0f);
        announceWipe(session, server, attackers);
        if (rate == 0.0f) return true;

        float next = session.getProgress() + rate;
        if (next >= session.getPoint().getCaptureTime()) return capture(session, server);
        if (next <= 0.0f) {
            session.setProgress(0.0f);
            return relight(session);
        }
        session.setProgress(next);
        return true;
    }

    // WHY: соперник на точке не участник сессии и в totalPresent не входит: без его учёта прогресс
    // WHY: стоял, пока захватчик бегал вне зоны, и соперник не мог его откатить
    private static boolean frozen(CaptureSession session) {
        return totalPresent() == 0 && presentRivals == 0 && session.anyOutside();
    }

    private static float rate(CaptureSession session, int attackers) {
        float capture = session.getPoint().getCaptureSpeed() / 100.0f;
        if (!session.getMode().contested()) {
            return attackers > 0 ? capture : -rollback(session);
        }
        return contestedRate(session, capture);
    }

    private static float contestedRate(CaptureSession session, float capture) {
        int total = totalPresent();
        if (total == 0) return -rollback(session);

        int leading = leadingCount();
        String dominant = dominantTeam();
        if (dominant == null) return 0.0f;

        float factor = 1.0f - (total - leading) / (float) leading;
        if (factor <= 0.0f) return 0.0f;
        return dominant.equals(session.getAttackerTeam()) ? capture * factor : -rollback(session) * factor;
    }

    private static float rollback(CaptureSession session) {
        CapturePoint point = session.getPoint();
        float rate = point.getRollbackSpeed() / 100.0f;

        if (presentRivals > 0) {
            rate = Math.max(rate, point.getPressuredRollbackSpeed() / 100.0f);
        }
        if (point.getOwnerTeam() != null) {
            rate = Math.max(rate, point.getOwnedRollbackSpeed() / 100.0f);
        }
        return rate;
    }

    private static boolean relight(CaptureSession session) {
        String dominant = dominantTeam();
        if (dominant == null || dominant.equals(session.getPoint().getOwnerTeam())) return false;
        if (!session.getMode().contested()) return false;
        if (session.getPoint() instanceof FinalCapturePoint && !CapturePointManager.mayTakeFinal(dominant)) {
            return false;
        }

        session.setAttackerTeam(dominant);
        return true;
    }

    private static boolean capture(CaptureSession session, MinecraftServer server) {
        String team = session.getAttackerTeam();
        session.setProgress(session.getPoint().getCaptureTime());
        CapturePointManager.completeCapture(session, team, server);
        return false;
    }

    private static void announceWipe(CaptureSession session, MinecraftServer server, int attackers) {
        if (attackers > 0) {
            session.setWipeAnnounced(false);
            return;
        }
        if (session.isWipeAnnounced() || session.getProgress() <= 0.0f) return;

        session.setWipeAnnounced(true);
        boolean isFinal = session.getPoint() instanceof FinalCapturePoint;
        MutableComponent message = Component.translatable(
                isFinal ? "capturepoints.capture.final_cancelled_wipe" : "capturepoints.capture.cancelled_wipe",
                Component.literal(session.getPoint().getName()).withStyle(ChatFormatting.YELLOW));
        announce(server, isFinal
                ? message.withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                : message.withStyle(ChatFormatting.RED));
    }

    private static void armPoints(MinecraftServer server) {
        for (CapturePoint point : CapturePointManager.getAllPoints()) {
            openSession(point, server);
        }
        if (!CapturePointManager.isFinalPointAvailable()) return;

        for (FinalCapturePoint point : CapturePointManager.getAllFinalPoints()) {
            openSession(point, server);
        }
    }

    private static void openSession(CapturePoint point, MinecraftServer server) {
        if (!point.getMode().startsByPresence() || sessions.containsKey(point.getName())) return;
        if (point.isOnCooldown()) return;

        ServerLevel level = CapturePointManager.levelOf(point);
        if (level == null) return;

        ServerPlayer starter = findStarter(point, level);
        if (starter == null) return;

        CaptureSession session = new CaptureSession(point, CapturePointManager.teamOf(starter), false);
        if (point.getMode().solo()) session.setSoloCaptor(starter.getUUID());
        sessions.put(point.getName(), session);
    }

    // WHY: команду, открывшую финальную, ищет обход всех точек, а стартующего ищет обход всех
    // WHY: игроков: спрашивать первое внутри второго значит умножать тик на число игроков
    private static ServerPlayer findStarter(CapturePoint point, ServerLevel level) {
        String opener = point instanceof FinalCapturePoint ? CapturePointManager.finalOpener() : null;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !player.isAlive() || downed(player)) continue;

            String team = CapturePointManager.teamOf(player);
            if (team == null || team.equals(point.getOwnerTeam())) continue;
            if (opener != null && !opener.equals(team)) continue;
            if (TeamCooldowns.blocked(team, point.getName())) continue;
            if (isCapturing(player.getUUID())) continue;
            if (point.getArea().contains(player.getX(), player.getY(), player.getZ(), EDGE_TOLERANCE)) return player;
        }
        return null;
    }

    public static void startByKey(ServerPlayer player, CapturePoint point) {
        if (player.isSpectator() || !player.isAlive()) return;
        if (!MatchState.running()) {
            player.sendSystemMessage(Component.translatable("capturepoints.capture.match_inactive")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (downed(player)) {
            player.sendSystemMessage(Component.translatable("capturepoints.capture.knocked_out")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        CaptureSession session = sessions.get(point.getName());
        if (session != null) {
            join(session, player);
            return;
        }
        if (!point.getMode().startsByKey()) {
            player.sendSystemMessage(Component.translatable("capturepoints.capture.starts_itself")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        String team = CapturePointManager.teamOf(player);
        if (!mayStart(player, point, team)) return;

        open(player, point, team);
    }

    private static void open(ServerPlayer player, CapturePoint point, String team) {
        CaptureSession session = new CaptureSession(point, team, true);
        if (point.getMode().solo()) session.setSoloCaptor(player.getUUID());
        enlist(session, player, team);
        sessions.put(point.getName(), session);

        glow(player);
        announceStart(session, player.getServer());
        broadcast(session, true);
    }

    private static void join(CaptureSession session, ServerPlayer player) {
        String team = CapturePointManager.teamOf(player);
        if (team == null) return;

        if (session.getMode().solo() && team.equals(session.getAttackerTeam())) {
            resume(session, player, team);
            return;
        }
        if (session.holds(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("capturepoints.capture.already_capturing")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        player.sendSystemMessage(Component.translatable(session.getMode().contested()
                        ? "capturepoints.capture.join_contest"
                        : "capturepoints.capture.other_team_holds",
                Component.literal(session.getPoint().getName()).withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.YELLOW));
    }

    private static void resume(CaptureSession session, ServerPlayer player, String team) {
        if (session.holds(session.getSoloCaptor()) && !player.getUUID().equals(session.getSoloCaptor())) {
            player.sendSystemMessage(Component.translatable("capturepoints.capture.already_capturing")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        session.setSoloCaptor(player.getUUID());
        enlist(session, player, team);
        session.setWipeAnnounced(false);
        glow(player);
    }

    private static boolean mayStart(ServerPlayer player, CapturePoint point, String team) {
        if (point instanceof FinalCapturePoint && !CapturePointManager.isFinalPointAvailable()) {
            return refuse(player, Component.translatable("capturepoints.capture.final_unavailable")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        if (point instanceof FinalCapturePoint && !CapturePointManager.mayTakeFinal(team)) {
            return refuse(player, Component.translatable("capturepoints.capture.final_not_yours")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        if (point.isOnCooldown()) {
            return refuse(player, Component.translatable("capturepoints.capture.on_cooldown",
                    Component.literal(point.getName()).withStyle(ChatFormatting.YELLOW),
                    point.getRemainingCooldown()).withStyle(ChatFormatting.RED));
        }
        if (team == null) {
            return refuse(player, Component.translatable("capturepoints.capture.must_be_in_team")
                    .withStyle(ChatFormatting.RED));
        }
        if (team.equals(point.getOwnerTeam())) {
            return refuse(player, Component.translatable("capturepoints.capture.already_owned")
                    .withStyle(ChatFormatting.YELLOW));
        }
        return mayStartByCooldown(player, point, team);
    }

    private static boolean mayStartByCooldown(ServerPlayer player, CapturePoint point, String team) {
        long left = TeamCooldowns.remainingSeconds(team, point.getName());
        if (left > 0L) {
            return refuse(player, Component.translatable("capturepoints.capture.team_cooldown",
                    Component.literal(point.getName()).withStyle(ChatFormatting.YELLOW), left)
                    .withStyle(ChatFormatting.RED));
        }
        if (isCapturing(player.getUUID())) {
            return refuse(player, Component.translatable("capturepoints.capture.already_capturing")
                    .withStyle(ChatFormatting.RED));
        }
        return true;
    }

    private static boolean refuse(ServerPlayer player, Component reason) {
        player.sendSystemMessage(reason);
        return false;
    }

    public static void onDeath(ServerPlayer player, MinecraftServer server) {
        for (CaptureSession session : sessions.values()) {
            if (!session.holds(player.getUUID())) continue;

            session.getMembers().remove(player.getUUID());
            stripGlow(player);
            if (session.getMode().solo() && player.getUUID().equals(session.getSoloCaptor())) {
                relay(session, player, server);
            }
        }
    }

    private static void relay(CaptureSession session, ServerPlayer fallen, MinecraftServer server) {
        session.setSoloCaptor(null);
        if (!session.getMode().relays()) return;

        ServerLevel level = CapturePointManager.levelOf(session.getPoint());
        if (level == null) return;

        for (ServerPlayer player : level.players()) {
            if (player == fallen) continue;
            if (!session.getAttackerTeam().equals(standingTeam(session, player))) continue;
            if (busyElsewhere(session, player.getUUID())) continue;

            adopt(session, player, server);
            return;
        }
    }

    // WHY: своей считалась только жертва, уже состоящая в сессии, поэтому убийство союзника рядом
    // WHY: с точкой накручивало долю награды тимкиллами
    public static void creditKill(ServerPlayer killer, ServerPlayer victim) {
        String killerTeam = CapturePointManager.teamOf(killer);
        if (killerTeam == null || killerTeam.equals(CapturePointManager.teamOf(victim))) return;

        for (CaptureSession session : sessions.values()) {
            if (!session.isRunning()) continue;
            if (killerTeam.equals(session.teamOfMember(victim.getUUID()))) continue;
            if (!nearPoint(session.getPoint(), victim) && !session.holds(victim.getUUID())) continue;

            session.getLedger().creditKill(killer.getUUID(), killerTeam);
        }
    }

    private static boolean nearPoint(CapturePoint point, ServerPlayer victim) {
        if (victim.serverLevel() != CapturePointManager.levelOf(point)) return false;
        return point.getArea().containsHorizontally(victim.getX(), victim.getZ(), KILL_APPROACH);
    }

    private static void warnDefenders(MinecraftServer server) {
        for (CaptureSession session : sessions.values()) {
            if (!session.isRunning() || session.isDecaying() || session.isHeld()) continue;

            CapturePoint point = session.getPoint();
            String owner = point.getOwnerTeam();
            if (owner == null || owner.equals(session.getAttackerTeam())) continue;

            tellOwners(session, server, owner);
        }
    }

    private static void tellOwners(CaptureSession session, MinecraftServer server, String owner) {
        CapturePoint point = session.getPoint();
        int remaining = Math.max(1, (int) ((point.getCaptureTime() - session.getProgress()) / 20.0f));
        Component message = Component.translatable(
                point instanceof FinalCapturePoint
                        ? "capturepoints.capture.defend_warning_final"
                        : "capturepoints.capture.defend_warning",
                Component.literal(session.getAttackerTeam())
                        .withStyle(CapturePointManager.getTeamChatFormatting(session.getAttackerTeam())),
                Component.literal(point.getName()).withStyle(ChatFormatting.YELLOW),
                remaining).withStyle(ChatFormatting.RED);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (owner.equals(CapturePointManager.teamOf(player))) player.sendSystemMessage(message);
        }
    }

    public static void cancelForPoint(String pointName) {
        CaptureSession session = sessions.remove(pointName);
        if (session == null) return;

        stripGlow(session, CapturePointManager.server());
        broadcast(session, false);
    }

    public static void cancelAll() {
        if (sessions.isEmpty()) return;

        for (String pointName : new ArrayList<>(sessions.keySet())) {
            cancelForPoint(pointName);
        }
    }

    private static void announceStart(CaptureSession session, MinecraftServer server) {
        boolean isFinal = session.getPoint() instanceof FinalCapturePoint;
        MutableComponent message = Component.translatable(
                isFinal ? "capturepoints.capture.final_started" : "capturepoints.capture.started",
                Component.literal(session.getAttackerTeam())
                        .withStyle(CapturePointManager.getTeamChatFormatting(session.getAttackerTeam())),
                Component.literal(session.getPoint().getName()).withStyle(ChatFormatting.YELLOW));

        announce(server, isFinal
                ? message.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                : message.withStyle(ChatFormatting.GOLD));
    }

    private static void announce(MinecraftServer server, Component message) {
        if (server == null) return;
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    private static boolean downed(ServerPlayer player) {
        KnockdownCapability knocked = player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).orElse(null);
        return knocked != null && knocked.isKnocked();
    }

    // WHY: свечение захватывающих видно всей карте, поэтому оно снимается правилом матча, а не
    // WHY: настройкой клиента: клиентский запрет чужой эффект всё равно не спрячет
    private static void glow(ServerPlayer player) {
        if (!GameRules.allows(GameRule.CAPTURE_GLOW)) return;

        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_DURATION_TICKS, 0, false, false));
    }

    private static void stripGlow(ServerPlayer player) {
        if (player != null) player.removeEffect(MobEffects.GLOWING);
    }

    private static void stripGlow(CaptureSession session, MinecraftServer server) {
        if (server == null) return;

        for (UUID playerId : session.getMembers().keySet()) {
            stripGlow(server.getPlayerList().getPlayer(playerId));
        }
    }

    private static void broadcastAll() {
        for (CaptureSession session : sessions.values()) {
            broadcast(session, true);
        }
    }

    private static void broadcast(CaptureSession session, boolean active) {
        syncedMembers.clear();
        if (active) {
            for (UUID playerId : session.getMembers().keySet()) {
                if (syncedMembers.size() >= MAX_SYNCED_MEMBERS) break;
                syncedMembers.add(playerId);
            }
        }

        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new CaptureUpdatePacket(
                session.getPoint().getName(), active, session.isRunning(),
                session.isRunning() ? session.progressPercent() : session.armingPercent(),
                session.isDecaying(), session.isHeld(), session.armingSecondsLeft(),
                session.getAttackerTeam(), session.getAttackerCount(), session.getRivalCount(),
                new ArrayList<>(syncedMembers)));
    }

    private static void resetScratch() {
        scratchSize = 0;
    }

    private static void countTeam(String team) {
        for (int index = 0; index < scratchSize; index++) {
            if (scratchTeams[index].equals(team)) {
                scratchCounts[index]++;
                return;
            }
        }
        if (scratchSize == scratchTeams.length) grow();

        scratchTeams[scratchSize] = team;
        scratchCounts[scratchSize] = 1;
        scratchSize++;
    }

    private static void grow() {
        String[] teams = new String[scratchTeams.length * 2];
        int[] counts = new int[scratchCounts.length * 2];
        System.arraycopy(scratchTeams, 0, teams, 0, scratchSize);
        System.arraycopy(scratchCounts, 0, counts, 0, scratchSize);
        scratchTeams = teams;
        scratchCounts = counts;
    }

    private static int teamCount(String team) {
        for (int index = 0; index < scratchSize; index++) {
            if (scratchTeams[index].equals(team)) return scratchCounts[index];
        }
        return 0;
    }

    private static int totalPresent() {
        int total = 0;
        for (int index = 0; index < scratchSize; index++) {
            total += scratchCounts[index];
        }
        return total;
    }

    private static int leadingCount() {
        int leading = 0;
        for (int index = 0; index < scratchSize; index++) {
            if (scratchCounts[index] > leading) leading = scratchCounts[index];
        }
        return leading;
    }

    private static String dominantTeam() {
        int leading = leadingCount();
        if (leading == 0) return null;

        String dominant = null;
        for (int index = 0; index < scratchSize; index++) {
            if (scratchCounts[index] != leading) continue;
            if (dominant != null) return null;

            dominant = scratchTeams[index];
        }
        return dominant;
    }
}
