package com.persiki84.battlecraft.client.hud;

import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.Toggle;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiTicker;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiVital;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public final class AmmoHud {
    private static final float WIDTH = 84.0f;
    private static final float HEIGHT = 30.0f;
    private static final float PADDING = 8.0f;

    private static final float LOADED_SCALE = 1.9f;
    private static final float SPARE_SCALE = 0.95f;
    private static final float MODE_SCALE = 0.7f;
    private static final float TRACK_HEIGHT = 2.5f;
    private static final float COUNTER_DROP = 2.5f;
    private static final float LOW_SHARE = 0.25f;
    private static final String INFINITY = "∞";

    private static final Toggle visibility = new Toggle(10.0f, 120L);
    private static final int SAMPLE_LOADED = 24;
    private static final int SAMPLE_MAGAZINE = 30;
    private static final int SAMPLE_SPARE = 120;
    private static final String SAMPLE_MODE = "AUTO";

    private static final Smooth fill = new Smooth(14.0f);
    private static final Smooth previewFill = new Smooth(14.0f);
    private static final UiTicker.Format SPARE =
            spare -> spare == GunBridge.SPARE_INFINITE ? "/ " + INFINITY : "/ " + spare;
    private static final Counters counters = new Counters();
    private static final Counters previewCounters = new Counters();

    private static int heldLoaded;
    private static int heldMagazine;
    private static int heldSpare = GunBridge.SPARE_HIDDEN;
    private static String heldMode = "";
    private static boolean gunHeld;

    private AmmoHud() {}

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator()) return;
        if (!GunBridge.available() || !HudLayout.visible(HudSlot.AMMO)) return;

        float scale = UiScale.push(graphics);
        try {
            render(graphics, mc, mc.player, screenWidth / scale, screenHeight / scale);
        } finally {
            UiScale.pop(graphics);
        }
    };

    private static void render(GuiGraphics graphics, Minecraft mc, LocalPlayer player,
                               float screenWidth, float screenHeight) {
        float delta = UiFrame.delta();
        float alpha = visibility.update(gunHeld, delta);
        if (alpha <= 0.01f) {
            forget();
            return;
        }

        float appear = UiAnim.easeOut(alpha);
        HudBox box = HudLayout.place(HudSlot.AMMO, WIDTH, HEIGHT, screenWidth, screenHeight);
        float x = box.x() + HudLayout.slideX(HudSlot.AMMO, (1.0f - appear) * 18.0f);
        float y = box.y();

        HudLayout.push(graphics, box);
        try {
            drawCard(graphics, mc, x, y, alpha * box.alpha(), delta, fill, counters);
        } finally {
            HudLayout.pop(graphics, box);
        }
    }

    public static void preview(GuiGraphics graphics, HudBox box, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        int loaded = heldLoaded;
        int magazine = heldMagazine;
        int spare = heldSpare;
        String mode = heldMode;
        if (magazine <= 0) {
            heldLoaded = SAMPLE_LOADED;
            heldMagazine = SAMPLE_MAGAZINE;
            heldSpare = SAMPLE_SPARE;
            heldMode = SAMPLE_MODE;
        }
        try {
            HudLayout.sample(HudSlot.AMMO, WIDTH, HEIGHT);
            drawCard(graphics, mc, box.x(), box.y(), alpha, UiFrame.delta(), previewFill, previewCounters);
        } finally {
            heldLoaded = loaded;
            heldMagazine = magazine;
            heldSpare = spare;
            heldMode = mode;
        }
    }

    public static void poll(LocalPlayer player) {
        if (!GunBridge.available()) {
            gunHeld = false;
            return;
        }

        GunBridge.Gun gun = GunBridge.read(player.getMainHandItem(), player.getInventory());
        gunHeld = gun != null;
        if (gun != null) remember(gun);
    }

    private static void remember(GunBridge.Gun gun) {
        heldLoaded = gun.loaded();
        heldMagazine = gun.magazine();
        heldSpare = gun.spare();
        heldMode = gun.fireMode();
    }

    static void forget() {
        if (!visibility.cleared()) return;

        heldLoaded = 0;
        heldMagazine = 0;
        heldSpare = GunBridge.SPARE_HIDDEN;
        heldMode = "";
        fill.snap(0.0f);
    }

    private static void drawCard(GuiGraphics graphics, Minecraft mc, float x, float y, float alpha,
                                 float delta, Smooth track, Counters shown) {
        float ratio = heldMagazine > 0 ? UiAnim.clamp01(heldLoaded / (float) heldMagazine) : 0.0f;
        boolean low = heldLoaded <= 0 || ratio <= LOW_SHARE;
        float pulse = low ? UiAnim.pulse(900.0f, 0.65f, 1.0f) : 1.0f;
        int base = HudLayout.tint(HudSlot.AMMO, UiAccent.color());
        int accent = low ? UiTheme.mix(base, UiPalette.alert(), 0.7f) : base;

        UiVital.card(graphics, x, y, WIDTH, HEIGHT, UiMetrics.radius(HEIGHT), alpha);
        drawCounters(graphics, mc, x, y, alpha, pulse, accent, shown);

        float trackY = y + HEIGHT - PADDING + 1.0f;
        UiGlass.sunken(graphics, x + PADDING, trackY, WIDTH - PADDING * 2.0f, TRACK_HEIGHT, TRACK_HEIGHT / 2.0f, alpha);
        UiGlass.progress(graphics, x + PADDING, trackY, WIDTH - PADDING * 2.0f, TRACK_HEIGHT,
                track.to(ratio, delta), accent, alpha * pulse);
    }

    private static void drawCounters(GuiGraphics graphics, Minecraft mc, float x, float y,
                                     float alpha, float pulse, int accent, Counters shown) {
        float row = HEIGHT - PADDING - TRACK_HEIGHT;
        shown.loaded.set(heldLoaded);
        shown.loaded.draw(graphics, mc.font, x + PADDING, UiRender.centerY(y + COUNTER_DROP, row, LOADED_SCALE),
                LOADED_SCALE, accent, alpha * pulse);

        if (heldSpare != GunBridge.SPARE_HIDDEN) {
            float loadedWidth = shown.loaded.steadyWidth(graphics, mc.font, LOADED_SCALE);
            shown.spare.set(heldSpare, SPARE);
            shown.spare.draw(graphics, mc.font, x + PADDING + loadedWidth + 4.0f,
                    UiRender.centerY(y + COUNTER_DROP, row, SPARE_SCALE), SPARE_SCALE, HudInk.textDim(), alpha);
        }

        if (!heldMode.isEmpty()) {
            shown.mode.set(heldMode);
            shown.mode.draw(graphics, mc.font, x + WIDTH - PADDING, y + PADDING * 0.6f, MODE_SCALE,
                    HudInk.textFaint(), alpha);
        }
    }

    // WHY: патроны в магазине меняются на каждом выстреле, запас и режим огня - на перезарядке и
    // WHY: переключении: все три меняют только сменившиеся буквы. У образца в редакторе свой набор,
    // WHY: иначе выход из редактора морфил бы образцовые числа в настоящие
    private static final class Counters {
        private final UiTicker loaded = new UiTicker();
        private final UiTicker spare = new UiTicker();
        private final UiTicker mode = new UiTicker().align(UiTicker.RIGHT);
    }
}
