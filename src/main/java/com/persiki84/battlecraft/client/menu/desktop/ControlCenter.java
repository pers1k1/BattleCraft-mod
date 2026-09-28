package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.battlecraft.client.custom.InterfaceFlag;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiIcon;
import com.persiki84.shared.client.ui.UiMotionSet;
import com.persiki84.shared.client.ui.UiRestFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

// WHY: сетка повторяет центр управления macOS: слева модуль из трёх переключателей, справа плитка
// WHY: набора анимаций и ряд круглых кнопок, снизу мини-плеер во всю ширину. Яркости и громкости
// WHY: здесь нет: для них есть настройки игры, а центр держит то, что меняют на ходу
final class ControlCenter {
    static final float WIDTH = 188.0f;

    private static final float PAD = 7.0f;
    private static final float GAP = 6.0f;
    private static final float RADIUS = 16.0f;
    private static final float MODULE_RADIUS = 14.0f;
    private static final float MODULE_INSET = 4.0f;
    private static final float ROW_HEIGHT = 24.0f;
    private static final float MODULE_HEIGHT = ROW_HEIGHT * 3.0f + MODULE_INSET * 2.0f;
    private static final float COLUMN = (WIDTH - PAD * 2.0f - GAP) / 2.0f;
    private static final float ROUND = (COLUMN - GAP * 2.0f) / 3.0f;
    private static final float HEIGHT = PAD * 2.0f + MODULE_HEIGHT + GAP + CenterMusic.HEIGHT;

    private final DesktopPopover popover = new DesktopPopover();
    private final List<CenterToggle> rows;
    private final CenterToggle motion;
    private final List<CenterToggle> rounds;
    private final CenterMusic music = new CenterMusic();
    private final Component[] motionNames = motionNames();
    private float left;
    private float top;

    ControlCenter(Runnable gallery, Runnable search) {
        Options options = Minecraft.getInstance().options;
        rows = List.of(
                CenterToggle.row(UiIcon.Kind.FULLSCREEN, Component.translatable("battlecraft.desktop.center.fullscreen"),
                        () -> options.fullscreen().get(), () -> flipFullscreen(options)),
                CenterToggle.row(UiIcon.Kind.SPARKLE, Component.translatable("battlecraft.desktop.center.dots"),
                        HudConfig::wallpaperDots, () -> HudConfig.wallpaperDots(!HudConfig.wallpaperDots())),
                CenterToggle.row(UiIcon.Kind.CHAT, Component.translatable("battlecraft.desktop.center.discord"),
                        InterfaceFlag.DISCORD::get, () -> InterfaceFlag.DISCORD.set(!InterfaceFlag.DISCORD.get())));
        motion = CenterToggle.tile(UiIcon.Kind.MOTION, Component.translatable("battlecraft.desktop.center.motion"),
                () -> motionNames[Customization.motion().ordinal()], ControlCenter::nextMotion);
        rounds = List.of(
                CenterToggle.action(UiIcon.Kind.PHOTO, gallery),
                CenterToggle.round(UiIcon.Kind.DROP, HudConfig::wallpaperThemed,
                        () -> HudConfig.wallpaperThemed(!HudConfig.wallpaperThemed())),
                CenterToggle.action(UiIcon.Kind.SEARCH, search));
    }

    private static Component[] motionNames() {
        UiMotionSet[] sets = UiMotionSet.values();
        Component[] names = new Component[sets.length];
        for (int index = 0; index < sets.length; index++) names[index] = Component.translatable(sets[index].translationKey());
        return names;
    }

    // WHY: в ванили опцию в options.txt пишет закрытие экрана настроек, а центр управления его не
    // WHY: открывает: без записи выбор «Весь экран» терялся бы на перезапуске игры
    private static void flipFullscreen(Options options) {
        options.fullscreen().set(!options.fullscreen().get());
        options.save();
    }

    private static void nextMotion() {
        UiMotionSet[] sets = UiMotionSet.values();
        Customization.motion(sets[(Customization.motion().ordinal() + 1) % sets.length]);
        Customization.save();
    }

    boolean open() {
        return popover.open();
    }

    void toggle() {
        popover.toggle();
        if (!popover.open()) music.forget();
    }

    void close() {
        popover.hide();
        music.forget();
    }

    void dismiss() {
        popover.dismiss();
        music.forget();
    }

    void render(GuiGraphics graphics, Font font, float anchorRight, float anchorTop, float pivotX,
                int mouseX, int mouseY) {
        float delta = UiFrame.delta();
        popover.advance(delta);
        if (!popover.visible()) return;
        left = anchorRight - WIDTH;
        top = anchorTop;
        popover.begin(graphics, Math.max(left, Math.min(anchorRight, pivotX)), top);
        try {
            paint(graphics, font, mouseX, mouseY, popover.alpha(), delta);
        } finally {
            popover.end(graphics);
        }
    }

    private void paint(GuiGraphics graphics, Font font, int mouseX, int mouseY, float alpha, float delta) {
        UiGlass.above(graphics, left, top, WIDTH, HEIGHT);
        UiGlass.hush(graphics, left, top, WIDTH, HEIGHT, RADIUS, alpha);
        UiGlass.window(graphics, left, top, WIDTH, HEIGHT, RADIUS, alpha);
        UiGlass.layer(graphics);
        layout();
        paintModule(graphics, font, mouseX, mouseY, alpha, delta);
        paintTile(graphics, font, motion, 0, mouseX, mouseY, alpha, delta);
        for (CenterToggle round : rounds) paintTile(graphics, font, round, 2, mouseX, mouseY, alpha, delta);
        float shown = alpha * popover.row(3);
        UiRestFrame.shift(graphics, 0.0f, popover.drift(3));
        try {
            music.render(graphics, font, mouseX, mouseY, shown, delta);
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    private void paintModule(GuiGraphics graphics, Font font, int mouseX, int mouseY, float alpha, float delta) {
        float shown = alpha * popover.row(0);
        UiRestFrame.shift(graphics, 0.0f, popover.drift(0));
        try {
            UiGlass.window(graphics, left + PAD, top + PAD, COLUMN, MODULE_HEIGHT, MODULE_RADIUS, shown, 0.2f);
        } finally {
            UiRestFrame.pop(graphics);
        }
        for (int index = 0; index < rows.size(); index++) {
            paintTile(graphics, font, rows.get(index), index, mouseX, mouseY, alpha, delta);
        }
    }

    private void paintTile(GuiGraphics graphics, Font font, CenterToggle tile, int row, int mouseX, int mouseY,
                           float alpha, float delta) {
        UiRestFrame.shift(graphics, 0.0f, popover.drift(row));
        try {
            tile.render(graphics, font, mouseX, mouseY, alpha * popover.row(row), delta);
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    private void layout() {
        float x = left + PAD;
        float y = top + PAD;
        for (int index = 0; index < rows.size(); index++) {
            rows.get(index).place(x + MODULE_INSET, y + MODULE_INSET + index * ROW_HEIGHT,
                    COLUMN - MODULE_INSET * 2.0f, ROW_HEIGHT);
        }
        float rightX = x + COLUMN + GAP;
        float roundTop = y + MODULE_HEIGHT - ROUND;
        motion.place(rightX, y, COLUMN, roundTop - GAP - y);
        for (int index = 0; index < rounds.size(); index++) {
            rounds.get(index).place(rightX + index * (ROUND + GAP), roundTop, ROUND, ROUND);
        }
        music.place(x, y + MODULE_HEIGHT + GAP, WIDTH - PAD * 2.0f);
    }

    boolean contains(double mouseX, double mouseY) {
        return popover.open() && mouseX >= left && mouseX <= left + WIDTH && mouseY >= top && mouseY <= top + HEIGHT;
    }

    boolean click(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) return false;
        if (music.click(mouseX, mouseY) || motion.click(mouseX, mouseY)) return true;
        for (CenterToggle row : rows) {
            if (row.click(mouseX, mouseY)) return true;
        }
        for (CenterToggle round : rounds) {
            if (round.click(mouseX, mouseY)) return true;
        }
        return true;
    }

    void drag(double mouseX) {
        music.drag(mouseX);
    }

    boolean release() {
        return music.release();
    }
}
