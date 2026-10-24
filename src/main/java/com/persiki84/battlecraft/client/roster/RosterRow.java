package com.persiki84.battlecraft.client.roster;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiTicker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.UUID;

final class RosterRow {
    private static final float GLIDE_SPEED = 9.0f;
    private static final float FADE_SPEED = 12.0f;
    private static final float TINT_SPEED = 6.0f;
    // WHY: пинг приходит от сервера раз в несколько секунд, но у только что вошедшего скачет:
    // WHY: число не морфится чаще, чем глаз успевает его прочесть
    private static final float LATENCY_PACE = 0.5f;
    static final float GONE = 0.01f;

    final UUID uuid;
    final Smooth x;
    final Smooth y;
    final Smooth presence;
    final Smooth tint = new Smooth(1.0f, TINT_SPEED);
    final UiTicker latencyTicker = new UiTicker().align(UiTicker.RIGHT).throttle(LATENCY_PACE);
    final UiTicker scoreTicker = new UiTicker().align(UiTicker.RIGHT);

    private Component shownDisplay;
    private String nameText;
    private PlayerTeam shownTeam;
    private Component shownPrefix;
    private Component shownSuffix;
    private ChatFormatting shownColor;
    private Component name = Component.empty();
    private String sortKey = "";
    private float nameWidth = -1.0f;
    private float measuredPixels = -1.0f;
    private RosterGroup group;
    private int fromTint;
    private int toTint;
    private float targetX;
    private float targetY;
    private boolean spectator;
    private int latency;
    private int score;
    private boolean scored;
    private boolean hearts;
    private boolean listed;

    public RosterRow(UUID uuid, boolean fresh) {
        this.uuid = uuid;
        this.x = new Smooth(0.0f, GLIDE_SPEED);
        this.y = new Smooth(0.0f, GLIDE_SPEED);
        this.presence = new Smooth(fresh ? 0.0f : 1.0f, FADE_SPEED);
    }

    public void refresh(PlayerInfo info, Scoreboard board, Objective objective) {
        rename(info);
        spectator = info.getGameMode() == GameType.SPECTATOR;
        latency = info.getLatency();
        scored = objective != null && !spectator;
        hearts = scored && objective.getRenderType() == ObjectiveCriteria.RenderType.HEARTS;
        score = scored ? scoreOf(board, objective, info.getProfile().getName()) : 0;
        listed = true;
    }

    // WHY: getOrCreatePlayerScore заводит пустую запись в клиентском табло; спрашиваем только
    // WHY: существующую, у игрока без счёта показывается ноль, как в ванильном списке
    private static int scoreOf(Scoreboard board, Objective objective, String player) {
        if (!board.hasPlayerScore(player, objective)) return 0;
        return board.getOrCreatePlayerScore(player, objective).getScore();
    }

    // WHY: имя пересобирается только при смене: компонент на кадр и замер ширины в отрисовке
    // WHY: это аллокация и проход по атласу на каждой строке
    private void rename(PlayerInfo info) {
        Component display = info.getTabListDisplayName();
        if (display != null) {
            if (display == shownDisplay) return;
            shownDisplay = display;
            nameText = null;
            replaceName(display);
            return;
        }
        String plain = info.getProfile().getName();
        PlayerTeam team = info.getTeam();
        if (shownDisplay == null && plain.equals(nameText) && dressedAs(team)) return;
        shownDisplay = null;
        nameText = plain;
        dress(team);
        replaceName(PlayerTeam.formatNameForTeam(team, Component.literal(plain)));
        sortKey = plain;
    }

    // WHY: ванильный список одевает ник префиксом и суффиксом команды (ранги плагинов живут именно
    // WHY: там); клиент на каждое обновление команды ставит новые объекты из пакета, поэтому смена
    // WHY: видна по ссылке, без сборки строки в тике
    private boolean dressedAs(PlayerTeam team) {
        if (team != shownTeam) return false;
        if (team == null) return true;
        return team.getPlayerPrefix() == shownPrefix && team.getPlayerSuffix() == shownSuffix
                && team.getColor() == shownColor;
    }

    private void dress(PlayerTeam team) {
        shownTeam = team;
        shownPrefix = team == null ? null : team.getPlayerPrefix();
        shownSuffix = team == null ? null : team.getPlayerSuffix();
        shownColor = team == null ? null : team.getColor();
    }

    private void replaceName(Component value) {
        name = value;
        sortKey = value.getString();
        nameWidth = -1.0f;
    }

    public void unlist() {
        listed = false;
    }

    // WHY: строка в полёте меняет цвет своей группы за время пути, а не кадром на вылете
    public void join(RosterGroup next, int nextTint) {
        if (next == group && nextTint == toTint) return;
        fromTint = group == null ? nextTint : tintNow();
        toTint = nextTint;
        tint.snap(group == null ? 1.0f : 0.0f);
        group = next;
    }

    public void aim(float nextX, float nextY) {
        if (presence.get() <= GONE) {
            x.snap(nextX);
            y.snap(nextY);
        }
        targetX = nextX;
        targetY = nextY;
    }

    public void settle() {
        x.snap(targetX);
        y.snap(targetY);
        tint.snap(1.0f);
        presence.snap(listed ? 1.0f : 0.0f);
    }

    public boolean advance(float delta) {
        x.to(targetX, delta);
        y.to(targetY, delta);
        tint.to(1.0f, delta);
        float shown = presence.to(listed ? 1.0f : 0.0f, delta);
        latencyTicker.set(Math.max(0, latency));
        if (scored) scoreTicker.set(score);
        return listed || shown > GONE;
    }

    public int tintNow() {
        return UiTheme.mix(fromTint, toTint, tint.get());
    }

    // WHY: колонка меряется тем же пером и бейком, каким имя рисуется (textTrackedBox): дробная
    // WHY: ширина StringSplitter расходилась с пером по целым пикселям, и самое длинное имя уезжало
    // WHY: в бегущую строку в колонке, заведённой под него
    public float nameWidth(GuiGraphics graphics, Font font) {
        float pixels = UiRender.restPixels(graphics);
        if (nameWidth < 0.0f || pixels != measuredPixels) {
            nameWidth = UiRender.measureTracked(graphics, font, name, RosterLayout.NAME_SCALE, 0.0f);
            measuredPixels = pixels;
        }
        return nameWidth;
    }

    public Component name() {
        return name;
    }

    public String sortName() {
        return sortKey;
    }

    public RosterGroup group() {
        return group;
    }

    public float targetX() {
        return targetX;
    }

    public float targetY() {
        return targetY;
    }

    public boolean listed() {
        return listed;
    }

    public boolean spectator() {
        return spectator;
    }

    public int latency() {
        return latency;
    }

    public boolean scored() {
        return scored;
    }

    public boolean hearts() {
        return hearts;
    }
}
