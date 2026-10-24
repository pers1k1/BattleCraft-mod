package com.persiki84.battlecraft.client.roster;

import com.mojang.blaze3d.platform.Window;
import com.persiki84.shared.client.menu.MenuLayer;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiAssemble;
import com.persiki84.shared.client.ui.UiCorner;
import com.persiki84.shared.client.ui.UiEmber;
import com.persiki84.shared.client.ui.UiFarewell;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.shared.client.ui.UiHud;
import com.persiki84.shared.client.ui.UiMotion;
import com.persiki84.shared.client.ui.UiMotionSet;
import com.persiki84.shared.client.ui.UiPlane;
import com.persiki84.shared.client.ui.UiReveal;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiStage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

// WHY: список игроков живёт как окно /bc, а не как плашка HUD: проявляется выбранным набором
// WHY: движения (свет, резкость, иней) через ту же офскрин-стадию, а на отпускании клавиши
// WHY: остаётся висеть в мире и уходит тем же набором (горение, раскол, иней), перерисовываясь
// WHY: живьём. Рисуется после всего HUD и после уходов, поэтому лежит поверх них
public final class RosterOverlay implements UiEmber {
    private static final float ENTER_SECONDS = 0.6f;
    private static final float ENTER_CHARGE = 0.55f;
    private static final float FADE_IN_SPEED = 17.0f;
    private static final float FADE_OUT_SPEED = 34.0f;
    private static final float GONE = 0.012f;
    private static final RosterOverlay EMBER = new RosterOverlay();
    private static final Runnable RELEASE = RosterOverlay::release;

    private enum State { HIDDEN, OPEN, FADING }

    private static final UiReveal reveal = new UiReveal();
    private static final Smooth entrance = new Smooth(0.0f, FADE_IN_SPEED);
    private static final RosterPanel panel = new RosterPanel();
    private static State state = State.HIDDEN;
    private static UiMotionSet motion = UiMotionSet.IGNITE;
    private static long lastFrame = Long.MIN_VALUE;
    private static boolean opening;
    private static float scale = 1.0f;

    private RosterOverlay() {}

    public static boolean live() {
        return state != State.HIDDEN || UiFarewell.burning();
    }

    // WHY: выход из мира посреди горения: догорающее окно ещё перерисовывается из книги, и её
    // WHY: очистка ждёт конца своего ухода, иначе в мире на миг висело бы пустое окно
    public static void forget() {
        state = State.HIDDEN;
        opening = false;
        if (!UiFarewell.defer(EMBER, RELEASE)) release();
    }

    private static void release() {
        RosterBook.forget();
        ServerPulseClient.forget();
    }

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!follows(UiFrame.frame())) state = State.HIDDEN;
        if (wanted(minecraft)) {
            if (state != State.OPEN) open(minecraft);
            show(graphics, screenWidth, screenHeight);
            return;
        }
        if (state == State.OPEN) {
            depart(graphics, screenWidth, screenHeight);
        } else if (state == State.FADING) {
            fade(graphics, screenWidth, screenHeight);
        }
    }

    // WHY: при F1 и вне мира проход HUD не идёт вовсе, и отпускание клавиши никто не увидит; кадр,
    // WHY: пришедший после разрыва, начинает с чистого листа, а не уводит в мир устаревшее окно
    private static boolean follows(long frame) {
        boolean continuous = lastFrame == frame - 1L;
        lastFrame = frame;
        return continuous;
    }

    private static boolean wanted(Minecraft minecraft) {
        return minecraft.player != null && minecraft.level != null && UiHud.rosterOpen();
    }

    private static void open(Minecraft minecraft) {
        state = State.OPEN;
        motion = UiMotion.of(null);
        reveal.restart();
        entrance.snap(0.0f);
        RosterBook.refresh(minecraft);
        opening = true;
    }

    private static void show(GuiGraphics graphics, int screenWidth, int screenHeight) {
        reveal.pace(motion.enterSeconds(ENTER_SECONDS));
        reveal.advance();
        float shown = UiAnim.easeOut(entrance.to(1.0f, FADE_IN_SPEED, UiFrame.delta()));
        boolean staged = beginStage(graphics, reveal.active());
        reveal.report("RosterOverlay", staged);
        float alpha = UiReveal.enabled() ? 1.0f : shown;
        float presence = staged ? UiReveal.glassPresence(reveal.phase())
                : UiReveal.enabled() ? 1.0f : UiReveal.glassPresence(shown);
        paintScoped(graphics, screenWidth, screenHeight, alpha, presence, staged);
        if (!staged) return;

        UiAssemble.draw(graphics, screenWidth, screenHeight, UiStage.texture(), reveal.phase(), reveal.seconds(),
                motion.enterMode(), panel.top() * scale, panel.height() * scale,
                originX(screenWidth), originY(screenHeight), ENTER_CHARGE);
    }

    // WHY: уход снимается тем же кадром, что и последний кадр окна: стадия заполняется окном, уход
    // WHY: берёт её с долей, которую успел набрать вход, а мировая плоскость снимается по камере
    // WHY: этого кадра, поэтому первый кадр ухода совпадает с окном пиксель в пиксель
    private static void depart(GuiGraphics graphics, int screenWidth, int screenHeight) {
        state = State.HIDDEN;
        if (!UiReveal.enabled() || !beginStage(graphics, true)) {
            state = State.FADING;
            fade(graphics, screenWidth, screenHeight);
            return;
        }
        paintScoped(graphics, screenWidth, screenHeight, 1.0f, UiReveal.glassPresence(reveal.phase()), true);
        UiPlane plane = anchored() ? UiPlane.capture(screenWidth, screenHeight) : null;
        UiFarewell.begin(EMBER, plane, panel.top() * scale / screenHeight, panel.height() * scale / screenHeight,
                originX(screenWidth), originY(screenHeight), motion, reveal.phase(), reveal.seconds());
        UiFarewell.first(graphics, screenWidth, screenHeight);
    }

    private static void fade(GuiGraphics graphics, int screenWidth, int screenHeight) {
        float shown = UiAnim.easeOut(entrance.to(0.0f, FADE_OUT_SPEED, UiFrame.delta()));
        if (shown <= GONE) {
            state = State.HIDDEN;
            return;
        }
        UiGlass.layer(graphics);
        paintScoped(graphics, screenWidth, screenHeight, shown, UiReveal.glassPresence(shown), false);
    }

    private static boolean anchored() {
        return UiPlane.allowed() && Minecraft.getInstance().level != null;
    }

    // WHY: подложку переснимаем до стадии: окно списка лежит поверх HUD и догорающих окон, и его
    // WHY: стекло обязано их преломлять, как экран мода в мире видит HUD
    private static boolean beginStage(GuiGraphics graphics, boolean wanted) {
        UiGlass.layer(graphics);
        if (!wanted) return false;
        graphics.flush();
        return UiStage.begin();
    }

    // WHY: между begin и end стадии рисует чужой код (головы игроков, шрифты): исключение без
    // WHY: finally оставило бы кадр писать в офскрин, и игрок видел бы чёрный экран. Глубина
    // WHY: чистится до окна: чат (z 50 и 100) и значки предметов HUD (z 150) пишут её выше стекла
    // WHY: и протыкали бы окно вне стадии
    private static void paintScoped(GuiGraphics graphics, int screenWidth, int screenHeight, float alpha,
                                    float presence, boolean staged) {
        MenuLayer.flatten(graphics);
        float outerPresence = UiGlassStyle.scalePresence(presence);
        try {
            paint(graphics, screenWidth, screenHeight, alpha);
        } finally {
            UiGlassStyle.restorePresence(outerPresence);
            if (staged) {
                graphics.flush();
                UiStage.end();
            }
        }
    }

    // WHY: без мира раскладка не перемеряется: после выхода ванильный список сбрасывает шапку и
    // WHY: подвал, и догорающее окно схлопнулось бы посреди ухода
    private static void paint(GuiGraphics graphics, int screenWidth, int screenHeight, float alpha) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean hud = UiCorner.hud(false);
        float unit = UiScale.push(graphics);
        try {
            if (minecraft.level != null) panel.prepare(graphics, minecraft.font, screenWidth / unit, screenHeight / unit);
            if (opening) {
                panel.settle();
                opening = false;
            }
            graphics.pose().scale(panel.scale(), panel.scale(), 1.0f);
            scale = unit * panel.scale();
            panel.advance();
            panel.draw(graphics, minecraft.font, alpha);
            graphics.flush();
        } finally {
            UiScale.pop(graphics);
            UiCorner.hud(hud);
        }
    }

    private static float originX(int screenWidth) {
        return (panel.left() + panel.width() / 2.0f) * scale / screenWidth;
    }

    private static float originY(int screenHeight) {
        return 1.0f - (panel.top() + panel.height() / 2.0f) * scale / screenHeight;
    }

    // WHY: догорающее окно перерисовывается живьём, как закрытый экран: стекло берёт живой кадр,
    // WHY: а числа и состав продолжают идти, пока окно не погасло
    @Override
    public void paintEmber(GuiGraphics graphics, float partialTick) {
        Window window = Minecraft.getInstance().getWindow();
        UiGlass.layer(graphics);
        paint(graphics, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 1.0f);
    }
}
