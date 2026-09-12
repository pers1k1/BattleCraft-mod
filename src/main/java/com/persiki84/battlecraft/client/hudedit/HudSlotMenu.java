package com.persiki84.battlecraft.client.hudedit;

import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudPlacement;
import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.hud.HudInk;
import com.persiki84.minimap.client.ClientMapData;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class HudSlotMenu {
    private static final int SHOW = 0;
    private static final int ALPHA = 1;
    private static final int DIM = 2;
    private static final int CORNER = 3;
    private static final int TINT = 4;
    private static final int UNDOCK = 5;
    private static final int CENTER = 6;
    private static final int RESET = 7;
    private static final int RESET_ALL = 8;
    private static final int RESET_LAYOUT = 9;
    private static final int PART = 100;

    private static final float WIDTH = 134.0f;
    private static final float PAD = 7.0f;
    private static final float ROW = 14.0f;
    private static final float GAP = 2.0f;
    private static final float TITLE = 15.0f;
    private static final float RADIUS = 8.0f;
    private static final float LABEL_SCALE = 0.76f;
    private static final float TITLE_SCALE = 0.86f;
    private static final float PILL_WIDTH = 18.0f;
    private static final float PILL_HEIGHT = 9.0f;
    private static final float KNOB_INSET = 1.5f;
    private static final float TRACK_WIDTH = 42.0f;
    private static final float TRACK_HEIGHT = 4.0f;
    private static final float SWATCH = 9.0f;
    private static final float SWATCH_GAP = 2.0f;
    private static final float GROW_SPEED = 15.0f;
    private static final float FLIP_SPEED = 17.0f;
    private static final float GLOW_SPEED = 14.0f;
    private static final float SWATCH_SPEED = 19.0f;
    private static final float RIPPLE_SECONDS = 0.34f;
    private static final float ROW_STAGGER = 0.035f;
    private static final float ROW_STAGGER_CAP = 0.42f;
    private static final float ROW_SLIDE = 4.0f;
    private static final float LABEL_LIFT = 1.5f;
    private static final float CHOICE_GAP = 1.5f;
    private static final float CHOICE_STROKE = 1.0f;
    private static final float RIPPLE_SWELL = 2.6f;
    private static final float RIPPLE_FADE = 0.45f;
    private static final float HOVER_ALPHA = 0.10f;
    private static final float PANEL_ALPHA = 0.94f;
    private static final float PANEL_LIFT = 0.18f;
    private static final float PANEL_SINK = 0.88f;
    private static final float CONTROL_ALPHA = 0.82f;
    private static final float SHRINK = 0.86f;
    private static final int MAP_SIZE_DEFAULT = 100;

    private static final int[] SWATCHES = {
            0xFFE7E9F4, 0xFFCE2A22, 0xFFE8A33D, 0xFFE6D34A,
            0xFF3FDB6A, 0xFF4FC9B0, 0xFF4F9BE8, 0xFFA96BE0
    };

    private static final IslandPart[] PARTS = {
            new IslandPart("avatar", HudConfig::islandAvatar, HudConfig::islandAvatar),
            new IslandPart("nick", HudConfig::islandNick, HudConfig::islandNick),
            new IslandPart("cover", HudConfig::islandCover, HudConfig::islandCover),
            new IslandPart("title", HudConfig::islandTitle, HudConfig::islandTitle),
            new IslandPart("artist", HudConfig::islandArtist, HudConfig::islandArtist),
            new IslandPart("bar", HudConfig::islandBar, HudConfig::islandBar),
            new IslandPart("time", HudConfig::islandTime, HudConfig::islandTime),
            new IslandPart("visualizer", HudConfig::islandVisualizer, HudConfig::islandVisualizer),
            new IslandPart("cover_tint", HudConfig::islandCoverTint, HudConfig::islandCoverTint),
            new IslandPart("fps", HudConfig::islandFps, HudConfig::islandFps),
            new IslandPart("ping", HudConfig::islandPing, HudConfig::islandPing)
    };

    private static final int KINDS = RESET_LAYOUT + 1 + PARTS.length;

    private static final int[] order = new int[8 + PARTS.length];
    private static final Smooth grow = new Smooth(0.0f, GROW_SPEED);
    private static final Smooth[] flip = new Smooth[KINDS];
    private static final Smooth[] glow = new Smooth[KINDS];
    private static final Smooth swatchAt = new Smooth(0.0f, SWATCH_SPEED);

    static {
        for (int index = 0; index < KINDS; index++) {
            flip[index] = new Smooth(0.0f, FLIP_SPEED);
            glow[index] = new Smooth(0.0f, GLOW_SPEED);
        }
    }

    private static float pickRipple;
    private static HudSlot slot;
    private static int rows;
    private static boolean armed;
    private static boolean closing;
    private static int sliding = -1;
    private static float left;
    private static float top;
    private static float originX;
    private static float originY;
    private static float pointerX;
    private static float pointerY;

    private HudSlotMenu() {}

    public static void open(HudSlot picked, float screenX, float screenY) {
        slot = picked;
        armed = true;
        closing = false;
        sliding = -1;
        grow.snap(0.0f);
        fillOrder();
        settle();
        originX = screenX;
        originY = screenY;
        left = Math.max(PAD, Math.min(screenX, guiWidth() - WIDTH - PAD));
        top = Math.max(PAD, Math.min(screenY, guiHeight() - height() * fit() - PAD));
        UiSound.press();
    }

    // WHY: меню открывается на новом элементе с его собственными положениями переключателей,
    // WHY: и без защёлки они переезжали бы от того, что стояло у прошлого
    private static void settle() {
        for (int index = 0; index < KINDS; index++) {
            glow[index].snap(0.0f);
        }
        for (int row = 0; row < rows; row++) {
            int kind = order[row];
            if (flips(kind)) flip[slotOf(kind)].snap(lit(kind) ? 1.0f : 0.0f);
        }
        swatchAt.snap(chosenCell());
        pickRipple = 0.0f;
    }

    private static int slotOf(int kind) {
        return kind >= PART ? RESET_LAYOUT + 1 + (kind - PART) : kind;
    }

    private static boolean flips(int kind) {
        return kind == SHOW || kind >= PART;
    }

    private static boolean lit(int kind) {
        return kind >= PART ? PARTS[kind - PART].state.getAsBoolean() : HudLayout.of(slot).visible();
    }

    private static float chosenCell() {
        if (slot == null) return SWATCHES.length;

        Integer tint = HudLayout.of(slot).tint();
        if (tint == null) return SWATCHES.length;
        for (int cell = 0; cell < SWATCHES.length; cell++) {
            if (tint == SWATCHES[cell]) return cell;
        }
        return SWATCHES.length;
    }

    private static void fillOrder() {
        rows = 0;
        if (slot == null) {
            order[rows++] = RESET_LAYOUT;
            order[rows++] = RESET_ALL;
            return;
        }
        order[rows++] = SHOW;
        order[rows++] = ALPHA;
        order[rows++] = DIM;
        order[rows++] = CORNER;
        order[rows++] = TINT;
        if (slot == HudSlot.ISLAND) {
            for (int part = 0; part < PARTS.length; part++) {
                order[rows++] = PART + part;
            }
        }
        if (HudLayout.of(slot).dock() != null) order[rows++] = UNDOCK;
        order[rows++] = CENTER;
        order[rows++] = RESET;
    }

    public static void close() {
        if (!armed) return;

        closing = true;
        sliding = -1;
    }

    public static boolean showing() {
        return armed && !closing;
    }

    public static void advance(float screenX, float screenY, boolean held, float delta) {
        pointerX = screenX;
        pointerY = screenY;
        if (!armed) return;

        grow.to(closing ? 0.0f : 1.0f, delta);
        if (closing && grow.get() < 0.01f) {
            armed = false;
            return;
        }
        breathe(delta);
        if (sliding < 0) return;
        if (!held) {
            sliding = -1;
            Customization.save();
            return;
        }
        slide(sliding);
    }

    private static void breathe(float delta) {
        int lifted = closing || !inside(pointerX, pointerY) ? -1 : rowAt(pointerY);
        pickRipple = Math.max(0.0f, pickRipple - delta / RIPPLE_SECONDS);
        swatchAt.to(chosenCell(), delta);
        for (int row = 0; row < rows; row++) {
            int kind = order[row];
            glow[slotOf(kind)].to(row == lifted ? 1.0f : 0.0f, delta);
            if (flips(kind)) flip[slotOf(kind)].to(lit(kind) ? 1.0f : 0.0f, delta);
        }
    }

    private static void slide(int kind) {
        float trackLeft = left + WIDTH - PAD - TRACK_WIDTH;
        float share = Math.max(0.0f, Math.min(1.0f, (mapX(pointerX) - trackLeft) / TRACK_WIDTH));
        HudPlacement placement = HudLayout.of(slot);
        if (kind == ALPHA) {
            placement.alpha(share);
        } else if (kind == DIM) {
            placement.dim(share * HudPlacement.DIM_MAX);
        } else {
            placement.corner(share * HudPlacement.CORNER_MAX);
        }
    }

    private static float share(int kind) {
        HudPlacement placement = HudLayout.of(slot);
        if (kind == ALPHA) return placement.alpha();
        if (kind == DIM) return placement.dim() / HudPlacement.DIM_MAX;
        return placement.corner() / HudPlacement.CORNER_MAX;
    }

    public static boolean press(double screenX, double screenY, int button) {
        if (!showing()) return false;
        if (!inside((float) screenX, (float) screenY)) {
            close();
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;

        int row = rowAt((float) screenY);
        if (row < 0) return true;

        act(order[row], mapX((float) screenX));
        return true;
    }

    private static void act(int kind, float screenX) {
        if (kind == RESET_ALL || kind == RESET_LAYOUT) {
            wipe(kind);
            return;
        }
        if (kind >= PART) {
            flipPart(kind - PART);
            return;
        }
        HudPlacement placement = HudLayout.of(slot);
        if (kind == SHOW) {
            placement.visible(!placement.visible());
            UiSound.chip(placement.visible());
        } else if (kind == ALPHA || kind == DIM || kind == CORNER) {
            sliding = kind;
            slide(kind);
        } else if (kind == TINT) {
            pickTint(placement, screenX);
        } else {
            command(kind, placement);
        }
        Customization.save();
    }

    private static void flipPart(int index) {
        IslandPart part = PARTS[index];
        boolean wanted = !part.state.getAsBoolean();
        part.apply.accept(wanted);
        UiSound.chip(wanted);
    }

    private static void wipe(int kind) {
        if (kind == RESET_ALL) {
            HudLayout.resetAll();
            ClientMapData.minimapSize = MAP_SIZE_DEFAULT;
        } else {
            HudLayout.resetPositions();
        }
        UiSound.slot();
        Customization.save();
        close();
    }

    private static void command(int kind, HudPlacement placement) {
        if (kind == UNDOCK) {
            stayPut(placement);
        } else if (kind == CENTER) {
            HudLayout.center(slot, HudEditSession.logicalWidth(), HudEditSession.logicalHeight());
        } else {
            placement.reset();
        }
        UiSound.slot();
        fillOrder();
    }

    private static void stayPut(HudPlacement placement) {
        HudBox box = HudLayout.box(slot);
        placement.dock(null);
        if (!box.drawn()) return;

        HudLayout.moveTo(slot, box.x(), box.y(),
                HudEditSession.logicalWidth(), HudEditSession.logicalHeight());
    }

    private static void pickTint(HudPlacement placement, float screenX) {
        int cell = (int) ((screenX - (left + PAD)) / (SWATCH + SWATCH_GAP));
        if (cell < 0 || cell > SWATCHES.length) return;

        placement.tint(cell == SWATCHES.length ? null : SWATCHES[cell]);
        pickRipple = 1.0f;
        UiSound.chip(true);
    }

    private static boolean inside(float screenX, float screenY) {
        float pointX = mapX(screenX);
        float pointY = mapY(screenY);
        return pointX >= left && pointX <= left + WIDTH
                && pointY >= top && pointY <= top + height();
    }

    private static int rowAt(float screenY) {
        float pointY = mapY(screenY);
        float first = top + PAD + TITLE;
        for (int row = 0; row < rows; row++) {
            float y = first + row * (ROW + GAP);
            if (pointY >= y && pointY <= y + ROW) return row;
        }
        return -1;
    }

    private static float height() {
        return PAD * 2.0f + TITLE + rows * (ROW + GAP) - GAP;
    }

    private static float fit() {
        float room = guiHeight() - PAD * 2.0f;
        float span = height();
        return span <= room ? 1.0f : room / span;
    }

    private static float mapX(float screenX) {
        return (screenX - left) / fit() + left;
    }

    private static float mapY(float screenY) {
        return (screenY - top) / fit() + top;
    }

    public static void render(GuiGraphics graphics) {
        if (!armed) return;

        float shown = UiAnim.easeOut(grow.get());
        float squeeze = fit();
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0.0f);
        graphics.pose().scale(squeeze, squeeze, 1.0f);
        graphics.pose().translate(-left, -top, 0.0f);
        graphics.pose().translate(originX, originY, 0.0f);
        graphics.pose().scale(scale(shown), scale(shown), 1.0f);
        graphics.pose().translate(-originX, -originY, 0.0f);
        try {
            paint(graphics, shown);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static float scale(float shown) {
        return SHRINK + (1.0f - SHRINK) * shown;
    }

    private static void paint(GuiGraphics graphics, float shown) {
        UiGlass.tinted(graphics, left, top, WIDTH, height(), RADIUS, shown * PANEL_ALPHA, PANEL_LIFT, body());
        UiRender.labelScaled(graphics, font(), title(),
                left + PAD, UiRender.centerY(top + PAD, TITLE, TITLE_SCALE), TITLE_SCALE,
                UiTheme.withAlpha(HudInk.text(), shown));

        float first = top + PAD + TITLE;
        for (int row = 0; row < rows; row++) {
            paintRow(graphics, order[row], first + row * (ROW + GAP), cascade(row, shown));
        }
    }

    // WHY: строки въезжают лесенкой, но их места не двигаются: попадание мыши считается по
    // WHY: своим координатам строки, сдвиг живёт только в отрисовке
    private static float cascade(int row, float shown) {
        float held = Math.min(ROW_STAGGER_CAP, row * ROW_STAGGER);
        return UiAnim.clamp01((shown - held) / (1.0f - held));
    }

    private static int body() {
        return UiTheme.mix(HudInk.text(), UiTheme.BLACK, PANEL_SINK);
    }

    private static Component title() {
        return slot == null
                ? Component.translatable("battlecraft.custom.group.elements")
                : Component.translatable(slot.translationKey());
    }

    private static void paintRow(GuiGraphics graphics, int kind, float y, float shown) {
        if (shown <= 0.004f) return;

        graphics.pose().pushPose();
        graphics.pose().translate(ROW_SLIDE * (1.0f - shown), 0.0f, 0.0f);
        try {
            paintBody(graphics, kind, y, shown);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void paintBody(GuiGraphics graphics, int kind, float y, float shown) {
        float warm = UiAnim.easeOut(glow[slotOf(kind)].get());
        if (warm > 0.004f) {
            UiRender.panel(graphics, left + PAD * 0.5f, y, WIDTH - PAD, ROW, ROW / 2.0f,
                    UiTheme.withAlpha(HudInk.text(), HOVER_ALPHA * warm * shown));
        }
        if (kind == TINT) {
            paintSwatches(graphics, y, shown);
            return;
        }
        UiRender.labelScaled(graphics, font(), label(kind), left + PAD + LABEL_LIFT * warm,
                UiRender.centerY(y, ROW, LABEL_SCALE), LABEL_SCALE,
                UiTheme.withAlpha(kind == RESET || kind >= PART
                        ? UiTheme.mix(HudInk.textDim(), HudInk.text(), warm) : HudInk.text(), shown));
        if (flips(kind)) paintPill(graphics, kind, y, shown);
        if (kind == ALPHA || kind == DIM || kind == CORNER) paintTrack(graphics, kind, y, shown);
    }

    private static Component label(int kind) {
        if (kind >= PART) {
            return Component.translatable("battlecraft.custom.island." + PARTS[kind - PART].name);
        }
        if (kind == RESET_ALL) return Component.translatable("battlecraft.custom.hud.reset_all");
        if (kind == RESET_LAYOUT) return Component.translatable("battlecraft.custom.hud.reset_layout");
        if (kind == SHOW) return Component.translatable("battlecraft.custom.hud.visible");
        if (kind == ALPHA) return Component.translatable("battlecraft.custom.hud.alpha");
        if (kind == DIM) return Component.translatable("battlecraft.custom.hud.dim");
        if (kind == CORNER) return Component.translatable("battlecraft.custom.hud.corner");
        if (kind == UNDOCK) return Component.translatable("battlecraft.custom.hud.undock");
        if (kind == CENTER) return Component.translatable("battlecraft.custom.hud.center");
        return Component.translatable("battlecraft.custom.hud.reset_one");
    }

    // WHY: у жёлоба и шайбы своя форма угла из кастомизации, а не общая панельная: без этой
    // WHY: защёлки переключатели редактора жили по «форме угла», а переключатели меню - по своей
    private static void paintPill(GuiGraphics graphics, int kind, float y, float shown) {
        boolean switching = UiGlassStyle.switching(true);
        try {
            paintSwitch(graphics, UiAnim.easeOut(flip[slotOf(kind)].get()), y, shown);
        } finally {
            UiGlassStyle.switching(switching);
        }
    }

    private static void paintSwitch(GuiGraphics graphics, float shift, float y, float shown) {
        float x = left + WIDTH - PAD - PILL_WIDTH;
        float pillTop = y + (ROW - PILL_HEIGHT) / 2.0f;
        UiGlass.sunken(graphics, x, pillTop, PILL_WIDTH, PILL_HEIGHT, PILL_HEIGHT / 2.0f, shown);
        UiGlass.progress(graphics, x, pillTop, PILL_WIDTH, PILL_HEIGHT, shift,
                UiAccent.color(), shown * CONTROL_ALPHA);

        float knob = PILL_HEIGHT - KNOB_INSET * 2.0f;
        float travel = PILL_WIDTH - knob - KNOB_INSET * 2.0f;
        UiGlass.inner(graphics, x + KNOB_INSET + travel * shift, pillTop + KNOB_INSET, knob, knob,
                knob / 2.0f, shown, 0.9f);
    }

    private static void paintTrack(GuiGraphics graphics, int kind, float y, float shown) {
        float x = left + WIDTH - PAD - TRACK_WIDTH;
        float trackTop = y + (ROW - TRACK_HEIGHT) / 2.0f;
        float share = share(kind);
        UiGlass.sunken(graphics, x, trackTop, TRACK_WIDTH, TRACK_HEIGHT, TRACK_HEIGHT / 2.0f, shown);
        UiGlass.progress(graphics, x, trackTop, TRACK_WIDTH, TRACK_HEIGHT, share, UiAccent.color(),
                shown * CONTROL_ALPHA);
        UiRender.dot(graphics, x + TRACK_WIDTH * share, trackTop + TRACK_HEIGHT / 2.0f, 2.6f,
                UiTheme.withAlpha(HudInk.text(), shown));
    }

    private static void paintSwatches(GuiGraphics graphics, float y, float shown) {
        float swatchTop = y + (ROW - SWATCH) / 2.0f;
        for (int cell = 0; cell <= SWATCHES.length; cell++) {
            paintSwatch(graphics, cellLeft(cell), swatchTop, cell, shown);
        }
        paintChoice(graphics, swatchTop, shown);
    }

    private static void paintSwatch(GuiGraphics graphics, float x, float y, int cell, float shown) {
        int color = cell == SWATCHES.length ? UiTheme.withAlpha(UiAccent.faint(), 0.5f) : SWATCHES[cell];
        UiRender.panel(graphics, x, y, SWATCH, SWATCH, SWATCH / 2.0f, UiTheme.withAlpha(color, shown));
    }

    // WHY: обводка не гаснет на прежнем цвете и не зажигается на новом, а переезжает между ними
    // WHY: одним кольцом: так видно, что выбор один, и куда он ушёл
    private static void paintChoice(GuiGraphics graphics, float y, float shown) {
        float ripple = RIPPLE_SWELL * pickRipple;
        float span = SWATCH + CHOICE_GAP * 2.0f + ripple * 2.0f;
        float x = cellLeft(swatchAt.get()) - CHOICE_GAP - ripple;
        UiRender.rim(graphics, x, y - CHOICE_GAP - ripple, span, span, span / 2.0f,
                CHOICE_STROKE, UiTheme.withAlpha(HudInk.text(), shown * (1.0f - pickRipple * RIPPLE_FADE)));
    }

    private static float cellLeft(float cell) {
        return left + PAD + cell * (SWATCH + SWATCH_GAP);
    }

    private static float guiWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    private static float guiHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    private static net.minecraft.client.gui.Font font() {
        return Minecraft.getInstance().font;
    }

    private static final class IslandPart {
        private final String name;
        private final java.util.function.BooleanSupplier state;
        private final java.util.function.Consumer<Boolean> apply;

        private IslandPart(String name, java.util.function.BooleanSupplier state,
                           java.util.function.Consumer<Boolean> apply) {
            this.name = name;
            this.state = state;
            this.apply = apply;
        }
    }
}
