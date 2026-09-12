package com.persiki84.battlecraft.client.menu;

import com.persiki84.battlecraft.client.menu.custom.PaletteButton;
import com.persiki84.battlecraft.client.menu.custom.CustomizeScreen;
import com.persiki84.shared.client.ui.Ambient;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAmbience;
import com.persiki84.shared.client.ui.UiAssemble;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiBackdrop;
import com.persiki84.shared.client.ui.UiBoot;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlow;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiStage;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiWave;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.menu.browse.ServerBrowseScreen;
import com.persiki84.battlecraft.client.menu.browse.WorldBrowseScreen;
import net.minecraft.network.chat.Component;

public class MinimalTitleScreen extends TitleScreen implements Ambient {
    private static final int CORNER_MARGIN = 12;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_GAP = 7;
    private static final float TITLE_TOP = 0.235f;
    private static final int TITLE_BLOCK = 58;
    private static final int TITLE_MIN_TOP = 8;
    private static final int SUBTITLE_BLOCK = 54;
    private static final float STACK_CENTER = 0.615f;
    private static final float TITLE_TRACKING = 6.0f;
    private static final float SUBTITLE_TRACKING = 4.0f;
    private static final float SUBTITLE_SCALE = 0.95f;
    private static final float SUBTITLE_TOP = 36.0f;
    private static final float RULE_LENGTH = 46.0f;
    private static final float RULE_GAP = 13.0f;
    private static final float RULE_HEIGHT = 1.0f;
    private static final float RULE_ALPHA = 0.32f;
    private static final float VERSION_SCALE = 0.85f;
    private static final int VERSION_HEIGHT = 8;

    private static final float INTRO_CHARGE = 0.55f;
    private static final float TITLE_HALO = 0.8f;
    private static final float GLOW_SPEED = 1.7f;
    private static final float GLOW_READY = 0.9f;
    private static final float TITLE_GLOW_REST = 0.16f;
    private static final float TITLE_GLOW_PEAK = 0.72f;
    private static final float TITLE_LETTER_LIFT = 1.8f;
    private static final float TITLE_LETTER_LIGHTEN = 0.7f;
    private static final float SHINE_SECONDS = 2.9f;
    private static final float SHINE_SPREAD = 3.4f;
    private static final float SHINE_PAUSE = 6.5f;
    private static final float STAMP_SECONDS = 1.9f;
    private static final float STAMP_SPREAD = 2.4f;
    private static final float STAMP_PAUSE = 7.0f;
    private static final float STAMP_LIGHTEN = 0.9f;
    private static final float STAMP_HALO = 0.55f;
    private static final float STAMP_GLOW = 0.8f;
    private static final float STAMP_LIFT = 1.1f;
    private static final float STAMP_RULE_TOP = 10.0f;
    private static final float STAMP_RULE_HEIGHT = 1.0f;
    private static final float STAMP_RULE_ALPHA = 0.85f;
    private static final float RULE_BREATH_PERIOD = 5200.0f;
    private static final float RULE_BREATH_LOW = 0.82f;
    private static final float RULE_BREATH_HIGH = 1.0f;

    private final UiWave shine = new UiWave(SHINE_SECONDS, SHINE_SPREAD);
    private final UiWave stamp = new UiWave(STAMP_SECONDS, STAMP_SPREAD);
    private final Smooth glow = new Smooth(0.0f, GLOW_SPEED);

    private int stackTop;
    private float shineRest;
    private float stampRest;
    private long beat = -1L;
    private boolean introArmed = true;

    public MinimalTitleScreen() {
        super(false);
    }

    @Override
    public UiAmbience.Mood ambience() {
        return UiAmbience.Mood.CLEAR;
    }

    // WHY: выбор мира, сервера и настройки возвращаются в тот же экземпляр титульного экрана, и его
    // WHY: init зовёт ещё и ресайз окна: взводить проявление имеет право только открытие экрана
    @Override
    public void added() {
        super.added();
        introArmed = true;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - BUTTON_WIDTH / 2;
        int stack = BUTTON_HEIGHT * 4 + BUTTON_GAP * 3;
        int belowTitle = Math.round(this.height * TITLE_TOP) + TITLE_BLOCK;
        int lowest = Math.max(0, this.height - stack - BUTTON_GAP * 2);
        int y = Math.min(Math.max(Math.round(this.height * STACK_CENTER) - stack / 2, belowTitle), lowest);
        stackTop = y;

        this.addRenderableWidget(new UiButton(x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("menu.singleplayer"), button -> this.minecraft.setScreen(worlds())));
        y += BUTTON_HEIGHT + BUTTON_GAP;

        this.addRenderableWidget(new UiButton(x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("menu.multiplayer"), button -> this.minecraft.setScreen(servers())));
        y += BUTTON_HEIGHT + BUTTON_GAP;

        this.addRenderableWidget(new UiButton(x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("menu.options"), button -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options))));
        y += BUTTON_HEIGHT + BUTTON_GAP;

        this.addRenderableWidget(new UiButton(x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("menu.quit"), button -> this.minecraft.stop()));

        this.addRenderableWidget(new PaletteButton(CORNER_MARGIN,
                this.height - CORNER_MARGIN - PaletteButton.SIZE, button -> CustomizeScreen.open()));
    }

    private net.minecraft.client.gui.screens.Screen worlds() {
        return HudConfig.browseScreens() ? new WorldBrowseScreen(this) : new SelectWorldScreen(this);
    }

    private net.minecraft.client.gui.screens.Screen servers() {
        return HudConfig.browseScreens() ? new ServerBrowseScreen(this) : new JoinMultiplayerScreen(this);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MenuBackground.shared().render(graphics, this.width, this.height);
        UiBackdrop.capture();
        if (UiBoot.busy()) return;

        if (introArmed) {
            introArmed = false;
            MenuIntro.begin(this);
        }
        MenuIntro.advance();

        graphics.flush();
        boolean staged = MenuIntro.running() && UiStage.begin();
        try {
            content(graphics, mouseX, mouseY, partialTick);
        } finally {
            if (staged) {
                graphics.flush();
                UiStage.end();
            }
        }
        if (!staged) return;

        UiAssemble.draw(graphics, this.width, this.height, UiStage.texture(),
                MenuIntro.phase(), MenuIntro.seconds(), MenuIntro.mode(), 0.0f, this.height, INTRO_CHARGE);
    }

    private void content(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float centerX = this.width / 2.0f;
        float titleY = Math.max(TITLE_MIN_TOP, Math.min(this.height * TITLE_TOP, stackTop - TITLE_BLOCK));

        waves();
        UiGlow.halo(graphics, TITLE_HALO * glow.get(), () -> wordmark(graphics, centerX, titleY, true));
        wordmark(graphics, centerX, titleY, false);

        if (titleY + SUBTITLE_BLOCK <= stackTop) lockup(graphics, centerX, titleY + SUBTITLE_TOP);

        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        version(graphics);
    }

    // WHY: свет и блик идут волнами по буквам, а между прогонами надпись отдыхает: без пауз
    // WHY: главное меню начинает мигать, а не дышать. Само свечение поднимается от нуля после
    // WHY: проявления экрана, и волна не выходит раньше, чем оно встало: иначе свет включается рывком
    private void waves() {
        long frame = UiFrame.frame();
        if (frame == beat) return;

        beat = frame;
        float delta = UiFrame.delta();
        glow.to(MenuIntro.running() ? 0.0f : 1.0f, delta);
        shine.advance();
        stamp.advance();

        float ready = glow.get() < GLOW_READY ? 0.0f : delta;
        shineRest = rest(shine, shineRest, ready, SHINE_PAUSE);
        stampRest = rest(stamp, stampRest, ready, STAMP_PAUSE);
    }

    private static float rest(UiWave wave, float waited, float delta, float pause) {
        if (wave.running()) return 0.0f;
        if (waited + delta < pause) return waited + delta;

        wave.begin();
        return 0.0f;
    }

    private void wordmark(GuiGraphics graphics, float centerX, float titleY, boolean light) {
        int base = light ? UiAccent.color() : UiAccent.text();
        UiRender.textTitleToned(graphics, this.font, Component.translatable("battlecraft.menu.title"),
                centerX, titleY, 1.0f, TITLE_TRACKING, base, new UiRender.GlyphTone() {
                    @Override
                    public int tint(int index, int count, int color) {
                        float weight = shine.weight(index, count);
                        if (light) {
                            return UiTheme.alpha(color,
                                    TITLE_GLOW_REST + (TITLE_GLOW_PEAK - TITLE_GLOW_REST) * weight);
                        }
                        return UiTheme.lighten(color, TITLE_LETTER_LIGHTEN * weight);
                    }

                    @Override
                    public float rise(int index, int count) {
                        return shine.weight(index, count) * TITLE_LETTER_LIFT;
                    }
                });
    }

    private void version(GuiGraphics graphics) {
        Component value = Component.translatable("battlecraft.menu.version");
        float[] marks = UiRender.trackedStops(graphics, this.font, value, VERSION_SCALE, 0.0f);
        float span = marks[marks.length - 1];
        float centerX = this.width - CORNER_MARGIN - span / 2.0f;
        float y = this.height - CORNER_MARGIN - VERSION_HEIGHT;

        if (stamp.running()) {
            UiGlow.halo(graphics, STAMP_HALO * glow.get(), () -> stampLight(graphics, value, centerX, y));
            stampRule(graphics, marks, centerX - span / 2.0f, y);
        }
        UiRender.textTrackedToned(graphics, this.font, value, centerX, y, VERSION_SCALE, 0.0f,
                UiAccent.textFaint(), stampTone(false));
    }

    private void stampLight(GuiGraphics graphics, Component value, float centerX, float y) {
        UiRender.textTrackedToned(graphics, this.font, value, centerX, y, VERSION_SCALE, 0.0f,
                UiAccent.color(), stampTone(true));
    }

    // WHY: метка версии живёт в углу и в покое ничего не стоит: свет и черта включаются только
    // WHY: на время прохода волны, и полноэкранный проход свечения не идёт каждый кадр
    private void stampRule(GuiGraphics graphics, float[] marks, float left, float y) {
        int count = marks.length - 1;
        float head = stamp.head(count);
        float from = UiRender.along(marks, head - STAMP_SPREAD);
        float to = UiRender.along(marks, head + STAMP_SPREAD);
        if (to - from <= 0.5f) return;

        UiRender.panel(graphics, left + from, y + STAMP_RULE_TOP, to - from, STAMP_RULE_HEIGHT,
                STAMP_RULE_HEIGHT / 2.0f, UiTheme.alpha(UiAccent.color(), STAMP_RULE_ALPHA));
    }

    private UiRender.GlyphTone stampTone(boolean light) {
        return new UiRender.GlyphTone() {
            @Override
            public int tint(int index, int count, int color) {
                float weight = stamp.weight(index, count);
                if (light) return UiTheme.alpha(color, STAMP_GLOW * weight);
                return UiTheme.lighten(color, STAMP_LIGHTEN * weight);
            }

            @Override
            public float rise(int index, int count) {
                return stamp.weight(index, count) * STAMP_LIFT;
            }
        };
    }

    private void lockup(GuiGraphics graphics, float centerX, float y) {
        Component subtitle = Component.translatable("battlecraft.menu.subtitle");
        float half = tracked(graphics, subtitle) / 2.0f;
        int tint = UiTheme.alpha(UiAccent.color(), RULE_ALPHA);

        float breath = UiAnim.pulse(RULE_BREATH_PERIOD, RULE_BREATH_LOW, RULE_BREATH_HIGH);
        float reach = RULE_LENGTH * breath;
        UiRender.panel(graphics, centerX - half - RULE_GAP - reach, y + SUBTITLE_SCALE * 4.0f,
                reach, RULE_HEIGHT, RULE_HEIGHT / 2.0f, tint);
        UiRender.panel(graphics, centerX + half + RULE_GAP, y + SUBTITLE_SCALE * 4.0f,
                reach, RULE_HEIGHT, RULE_HEIGHT / 2.0f, tint);
        UiRender.textTracked(graphics, this.font, subtitle, centerX, y,
                SUBTITLE_SCALE, SUBTITLE_TRACKING, UiAccent.textDim());
    }

    private float tracked(GuiGraphics graphics, Component value) {
        int gaps = Math.max(0, value.getString().length() - 1);
        return UiRender.measure(graphics, this.font, value, SUBTITLE_SCALE) + SUBTITLE_TRACKING * gaps;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (MenuIntro.running()) {
            MenuIntro.skip();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (MenuIntro.running()) {
            MenuIntro.skip();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void removed() {
        MenuIntro.skip();
        super.removed();
    }

    @Override
    public void tick() {
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
