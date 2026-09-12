package com.persiki84.battlecraft.client.setup;

import com.mojang.blaze3d.systems.RenderSystem;
import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.battlecraft.client.menu.MenuBackground;
import com.persiki84.battlecraft.client.voice.VoiceOptions;
import com.persiki84.shared.client.menu.GlassScreen;
import com.persiki84.shared.client.ui.Ambient;
import com.persiki84.shared.client.ui.UiMotion;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAmbience;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiBackdrop;
import com.persiki84.shared.client.ui.UiBoot;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiGlow;
import com.persiki84.shared.client.ui.UiQuality;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class SetupScreen extends GlassScreen implements Ambient, UiMotion.Fixed {
    private static final int PANEL_LIMIT = 640;
    private static final int PANEL_INSET = 40;
    private static final int PANEL_PAD = 16;
    private static final int TITLE_BLOCK = 48;
    private static final int NOTE_TOP = 16;
    private static final int DOTS_BLOCK = 22;
    private static final int DOT_GAP = 9;
    private static final float DOT_RADIUS = 2.4f;
    private static final float DOT_ACTIVE_RADIUS = 3.1f;
    private static final float DOT_GLOW = 1.25f;
    private static final float HALO_SPREAD = 2.2f;
    private static final float DOT_SPEED = 13.0f;
    private static final float DOT_SWELL = 1.1f;
    private static final float CREST_REACH = 1.7f;
    private static final float CREST_FLOOR = 0.02f;
    private static final float MARKER_FADE = 2.4f;
    private static final float FINISH_SECONDS = 0.9f;
    private static final float NO_CREST = -1000.0f;
    private static final float PANEL_RADIUS = 10.0f;
    private static final float TITLE_SCALE = 1.0f;
    private static final float TITLE_TRACKING = 1.6f;
    private static final float NOTE_SCALE = 0.72f;
    private static final float ENTER_SECONDS = 1.15f;

    private static final int BACK_WIDTH = 64;
    private static final int BACK_HEIGHT = 18;
    private static final float FORWARD = 1.0f;
    private static final float BACKWARD = -1.0f;

    private static final float TURN_SECONDS = 0.7f;
    private static final float TURN_SHIFT = 26.0f;
    private static final float TURN_HALF = 0.5f;
    private static final float SETTLED = -1.0f;

    private final List<SetupStep> queue = new ArrayList<>();
    private final Greeting greeting = new Greeting();
    private final Smooth marker = new Smooth(0.0f, DOT_SPEED);

    private SetupPage page = greeting;
    private SetupPage arriving;
    private int cursor = -1;
    private float turn = SETTLED;
    private float finish = SETTLED;
    private boolean farewell;
    private float way = FORWARD;
    private long stamp = -1L;
    private boolean swapped;

    public SetupScreen() {
        super(Component.translatable("battlecraft.setup.title"));
        queue.addAll(SetupState.pending());
    }

    @Override
    public UiAmbience.Mood ambience() {
        return UiAmbience.Mood.FOCUS;
    }

    @Override
    protected void init() {
        rebuildPage();
    }

    private void rebuildPage() {
        clearWidgets();
        page.build(this, contentLeft(), contentTop(), contentWidth());
        if (page.framed() && earlier() >= 0) placeBack();
    }

    // WHY: возврат живёт на строке точек слева от них: ответ можно переиграть, не начиная мастер заново
    private void placeBack() {
        int y = Math.round(panelTop() + panelHeight() - PANEL_PAD - DOTS_BLOCK / 2.0f) - BACK_HEIGHT / 2;
        place(new UiButton(panelLeft() + PANEL_PAD, y, BACK_WIDTH, BACK_HEIGHT,
                Component.translatable("battlecraft.setup.back"), button -> retreat()));
    }

    private int earlier() {
        for (int at = cursor - 1; at >= 0; at--) {
            if (!skipped(queue.get(at))) return at;
        }
        return -1;
    }

    private void retreat() {
        int at = earlier();
        if (at < 0 || busy()) return;

        cursor = at;
        arriving = SetupPages.of(queue.get(at), this);
        turn = 0.0f;
        swapped = false;
        way = BACKWARD;
    }

    public <T extends net.minecraft.client.gui.components.AbstractWidget> T place(T widget) {
        return addRenderableWidget(widget);
    }

    public void answer(SetupStep step) {
        SetupState.answered(step);
        SetupState.save();
        Customization.save();
        UiSound.confirm();
        advance();
    }

    private void advance() {
        SetupPage next = nextPage();
        if (next == null) {
            finish = 0.0f;
            return;
        }
        arriving = next;
        turn = 0.0f;
        swapped = false;
        way = FORWARD;
    }

    private SetupPage nextPage() {
        while (++cursor < queue.size()) {
            SetupStep step = queue.get(cursor);
            if (skipped(step)) {
                SetupState.skipped(step);
                SetupState.save();
                continue;
            }
            return SetupPages.of(step, this);
        }
        return null;
    }

    // WHY: подтон стекла спрашивается только у того, кто выбрал жидкое стекло: в остальных режимах его нет,
    // WHY: а голос только у того, у кого стоит Simple Voice Chat: без мода спрашивать нечего
    private static boolean skipped(SetupStep step) {
        return switch (step) {
            case TINT -> Customization.mode() != UiQuality.Mode.LIQUID;
            case VOICE -> !VoiceOptions.available();
            default -> false;
        };
    }

    // WHY: renderContent зовётся дважды за кадр, пока экран догорает, поэтому время двигается
    // WHY: один раз на кадр, а не один раз на вызов
    private void advanceTime() {
        if (stamp == UiFrame.frame()) return;

        stamp = UiFrame.frame();
        advanceTurn();
        advanceFinish();
    }

    private void advanceFinish() {
        if (finish < 0.0f || farewell) return;

        finish = Math.min(1.0f, finish + UiFrame.delta() / FINISH_SECONDS);
        if (finish < 1.0f) return;

        farewell = true;
        dismiss();
    }

    private void advanceTurn() {
        if (turn < 0.0f) return;

        turn = Math.min(1.0f, turn + UiFrame.delta() / TURN_SECONDS);
        if (!swapped && turn >= TURN_HALF) {
            swapped = true;
            page.release();
            page = arriving;
            arriving = null;
            rebuildPage();
        }
        if (turn >= 1.0f) turn = SETTLED;
    }

    // WHY: easeOut на входе и на выходе съедает почти всю прозрачность в первые кадры, и страница
    // WHY: читается как подставленная целиком; сглаженная с обоих концов кривая даёт настоящий переход
    private float fadeOut() {
        if (turn < 0.0f) return 0.0f;
        return turn < TURN_HALF ? UiAnim.smoothstep(0.0f, 1.0f, turn / TURN_HALF) : 0.0f;
    }

    private float fadeIn() {
        if (turn < TURN_HALF) return 0.0f;
        return 1.0f - UiAnim.smoothstep(0.0f, 1.0f, (turn - TURN_HALF) / TURN_HALF);
    }

    private float pageShift() {
        return (fadeIn() * TURN_SHIFT - fadeOut() * TURN_SHIFT) * way;
    }

    private float pageAlpha() {
        return 1.0f - Math.max(fadeOut(), fadeIn());
    }

    @Override
    protected boolean inputCaptured() {
        return busy();
    }

    private boolean busy() {
        return turn >= 0.0f || finish >= 0.0f;
    }

    @Override
    protected float contentScale() {
        if (!page.framed()) return 1.0f;
        return Math.min(1.0f, Math.max(1, this.height - PANEL_PAD * 2) / (float) panelHeight());
    }

    private int panelWidth() {
        return Math.min(PANEL_LIMIT, this.width - PANEL_INSET);
    }

    private int panelHeight() {
        return TITLE_BLOCK + page.contentHeight() + DOTS_BLOCK + PANEL_PAD * 2;
    }

    private int panelLeft() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelTop() {
        return (this.height - panelHeight()) / 2;
    }

    private int contentLeft() {
        return page.framed() ? panelLeft() + PANEL_PAD : 0;
    }

    private int contentWidth() {
        return page.framed() ? panelWidth() - PANEL_PAD * 2 : this.width;
    }

    private int contentTop() {
        return page.framed() ? panelTop() + TITLE_BLOCK : 0;
    }

    @Override
    protected float enterSeconds() {
        return ENTER_SECONDS;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (UiBoot.busy()) {
            MenuBackground.shared().render(graphics, this.width, this.height);
            UiBackdrop.capture();
            return;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBackdrop(GuiGraphics graphics) {
        MenuBackground.shared().render(graphics, this.width, this.height);
        graphics.flush();
        UiBackdrop.restage();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        advanceTime();

        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, pageShift(), 0.0f);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, pageAlpha());
        try {
            paintPage(graphics, mouseX, mouseY, partialTick);
        } finally {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            graphics.pose().popPose();
        }
    }

    private void paintPage(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (page.framed()) paintFrame(graphics);
        page.paint(graphics, this.font, contentLeft(), contentTop(), contentWidth(), fadeOut());
        renderWidgets(graphics, mouseX, mouseY, partialTick);
    }

    private void paintFrame(GuiGraphics graphics) {
        float centerX = this.width / 2.0f;
        UiGlass.window(graphics, panelLeft(), panelTop(), panelWidth(), panelHeight(), PANEL_RADIUS, 1.0f);
        UiRender.textTracked(graphics, this.font, page.title(), centerX, panelTop() + PANEL_PAD,
                TITLE_SCALE, TITLE_TRACKING, UiAccent.text());
        UiRender.textCentered(graphics, this.font, page.note(), centerX,
                panelTop() + PANEL_PAD + NOTE_TOP, NOTE_SCALE, UiAccent.textFaint(), false);
        paintDots(graphics, centerX);
    }

    private void paintDots(GuiGraphics graphics, float centerX) {
        int total = queue.size();
        if (total <= 1) return;

        float left = centerX - (total - 1) * DOT_GAP / 2.0f;
        float y = panelTop() + panelHeight() - PANEL_PAD - DOTS_BLOCK / 2.0f;
        float crest = crest(total);
        for (int index = 0; index < total; index++) {
            paintDot(graphics, left + index * DOT_GAP, y, index, crest);
        }
        paintMarker(graphics, left, y, total);
    }

    private void paintDot(GuiGraphics graphics, float x, float y, int index, float crest) {
        float wave = wave(index, crest);
        float radius = DOT_RADIUS + wave * (DOT_ACTIVE_RADIUS - DOT_RADIUS + DOT_SWELL);
        int resting = UiTheme.alpha(UiAccent.faint(), index < cursor ? 0.7f : 0.35f);
        int color = UiTheme.mix(resting, UiAccent.color(), lit(index, crest));
        if (wave > CREST_FLOOR) {
            UiGlow.halo(graphics, DOT_GLOW * wave,
                    () -> UiRender.dot(graphics, x, y, radius * HALO_SPREAD, color));
        }
        UiRender.dot(graphics, x, y, radius, color);
    }

    // WHY: бегунок доезжает до последнего кружка и гаснет, а не уходит за ряд: пройденных шагов
    // WHY: на один больше, чем кружков, и по номеру шага он оказывался на несуществующем месте
    // WHY: источник света рисуется шире самого кружка: пирамида ореола живёт в восьмую долю кадра,
    // WHY: и пятно в три пикселя размывается там до невидимого
    private void paintMarker(GuiGraphics graphics, float left, float y, int total) {
        float head = left + marker.to(Math.min(cursor, total - 1), UiFrame.delta()) * DOT_GAP;
        float alpha = finish < 0.0f ? 1.0f : 1.0f - UiAnim.clamp01(finish * MARKER_FADE);
        if (alpha <= CREST_FLOOR) return;

        int color = UiTheme.alpha(UiAccent.color(), alpha);
        UiGlow.halo(graphics, DOT_GLOW * alpha, () ->
                UiRender.dot(graphics, head, y, DOT_ACTIVE_RADIUS * HALO_SPREAD, color));
        UiRender.dot(graphics, head, y, DOT_ACTIVE_RADIUS, color);
    }

    // WHY: гребень финала строится от числа кружков, поэтому шаг, добавленный в enum, участвует
    // WHY: в нём сам, без единого номера в коде
    private float crest(int total) {
        if (finish < 0.0f) return NO_CREST;

        float span = total - 1 + CREST_REACH * 2.0f;
        return UiAnim.smoothstep(0.0f, 1.0f, finish) * span - CREST_REACH;
    }

    private static float wave(int index, float crest) {
        float distance = Math.abs(index - crest);
        if (distance >= CREST_REACH) return 0.0f;

        return UiAnim.smoothstep(0.0f, 1.0f, 1.0f - distance / CREST_REACH);
    }

    private static float lit(int index, float crest) {
        return UiAnim.smoothstep(index - CREST_REACH * 0.5f, index + CREST_REACH * 0.5f, crest);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (page.keyPressed(key, scan)) return true;
        if (greet()) return true;
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE) {
            retreat();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (busy()) return true;
        if (page.mouseClicked(button)) return true;
        if (greet()) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean greet() {
        if (page != greeting || busy()) return false;
        if (!greeting.settled()) {
            greeting.hurry();
            return true;
        }
        UiSound.confirm();
        advance();
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
