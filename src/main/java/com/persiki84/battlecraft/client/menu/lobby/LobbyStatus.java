package com.persiki84.battlecraft.client.menu.lobby;

import com.persiki84.battlecraft.BattleCraftManager;
import com.persiki84.battlecraft.client.ClientGameData;
import com.persiki84.battlecraft.client.ClientLobbyData;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiSwap;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class LobbyStatus {
    public static final float HEIGHT = 18.0f;
    private static final float SCALE = 1.0f;
    private static final float SWAP_SPEED = 14.0f;
    private static final float BAR_SPEED = 8.0f;
    private static final float BAR_WIDTH = 132.0f;
    private static final float BAR_HEIGHT = 3.0f;
    private static final float BAR_GAP = 12.0f;
    private static final float GONE = 0.01f;

    private final LobbyText line = new LobbyText();
    private final Smooth swap = new Smooth(1.0f, SWAP_SPEED);
    private final Smooth bar = new Smooth(0.0f, BAR_SPEED);
    private Component outgoing;

    // WHY: смена смысла строки (выбор, отсчёт, ожидание) уходит переездом, а смена одного числа
    // WHY: перекатывается по цифрам на месте: переезд каждую секунду читался бы дрожью
    public void render(GuiGraphics graphics, Font font, float centerX, float y, float delta) {
        pick();
        float phase = swap.to(1.0f, delta);
        if (outgoing != null && phase < 0.999f) {
            paint(graphics, font, outgoing, centerX, y, -phase * UiSwap.LIFT, 1.0f - phase);
        }
        paintCurrent(graphics, font, centerX, y, (1.0f - phase) * UiSwap.LIFT, phase);
        renderBar(graphics, centerX, y + BAR_GAP, delta);
    }

    // WHY: переезд строки заявлен ходом от её места покоя: сдвиг координатой вёл буквы ступенями
    private static void paint(GuiGraphics graphics, Font font, Component value, float centerX, float y, float move,
                              float alpha) {
        if (alpha <= GONE) return;
        UiRestFrame.shift(graphics, 0.0f, move);
        try {
            LobbyFigures.line(graphics, font, value, centerX, y, SCALE, UiTheme.alpha(UiAccent.textDim(), alpha),
                    LobbyFigures.CENTER);
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    // WHY: число в той же фразе (секунды отсчёта) перекатывается по цифрам, как таймер острова
    private void paintCurrent(GuiGraphics graphics, Font font, float centerX, float y, float move, float alpha) {
        if (alpha <= GONE) return;
        UiRestFrame.shift(graphics, 0.0f, move);
        try {
            LobbyFigures.line(graphics, font, line, centerX, y, SCALE, UiTheme.alpha(UiAccent.textDim(), alpha),
                    LobbyFigures.CENTER);
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    private void renderBar(GuiGraphics graphics, float centerX, float y, float delta) {
        float timer = ClientGameData.getInterpolatedLobbyTimer();
        int max = ClientGameData.getLobbyMaxTimer();
        float shown = bar.to(timer > 0.0f && max > 0 ? 1.0f : 0.0f, delta);
        if (shown <= GONE) return;

        float left = centerX - BAR_WIDTH / 2.0f;
        UiGlass.sunken(graphics, left, y, BAR_WIDTH, BAR_HEIGHT, BAR_HEIGHT / 2.0f, 0.8f * shown);
        float share = max > 0 ? UiAnim.clamp01(timer / max) : 0.0f;
        UiGlass.progress(graphics, left, y, BAR_WIDTH, BAR_HEIGHT, share, UiAccent.color(), shown);
    }

    private void pick() {
        String before = line.key();
        Component was = line.current();
        String key = key();
        if (!line.set(key, value(key)) || before == null || key.equals(before)) return;
        outgoing = was;
        swap.snap(0.0f);
    }

    private static String key() {
        if (ClientGameData.isSoftDisabled()) return "battlecraft.lobby.off";
        if (ClientGameData.getInterpolatedLobbyTimer() > 0.0f) return "battlecraft.lobby.starts_in";
        if (ClientLobbyData.teams().isEmpty()) return "battlecraft.lobby.no_teams";
        if (ClientLobbyData.ownTeam() == null) return undecidedKey();
        if (ClientGameData.getGraceSeconds() > 0) return "battlecraft.lobby.grace";
        if (ClientGameData.getSwitchCooldownSeconds() > 0) return "battlecraft.lobby.switch_in";
        return "battlecraft.lobby.waiting_ready";
    }

    private static String undecidedKey() {
        if (ClientGameData.getAutoAssignSeconds() > 0) return "battlecraft.lobby.auto_in";
        boolean running = ClientGameData.getCurrentPhase() == BattleCraftManager.GamePhase.ACTIVE;
        return running ? "battlecraft.lobby.match_running" : "battlecraft.lobby.pick_any";
    }

    private static int value(String key) {
        return switch (key) {
            case "battlecraft.lobby.starts_in" -> (int) Math.ceil(ClientGameData.getInterpolatedLobbyTimer() / 20.0f);
            case "battlecraft.lobby.auto_in" -> ClientGameData.getAutoAssignSeconds();
            case "battlecraft.lobby.grace" -> ClientGameData.getGraceSeconds();
            case "battlecraft.lobby.switch_in" -> ClientGameData.getSwitchCooldownSeconds();
            default -> 0;
        };
    }
}
