package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.menu.MenuBackground;
import com.persiki84.battlecraft.client.menu.MenuIntro;
import com.persiki84.battlecraft.client.menu.MinimalTitleScreen;
import com.persiki84.battlecraft.client.menu.browse.ServerBrowseScreen;
import com.persiki84.battlecraft.client.menu.browse.WorldBrowseScreen;
import com.persiki84.battlecraft.client.menu.custom.CustomizeScreen;
import com.persiki84.shared.client.menu.studio.StudioMenu;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiAssemble;
import com.persiki84.shared.client.ui.UiBackdrop;
import com.persiki84.shared.client.ui.UiBoot;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.shared.client.ui.UiGlassText;
import com.persiki84.shared.client.ui.UiIcon;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiStage;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;

public class DesktopTitleScreen extends MinimalTitleScreen {
    private static final float INTRO_CHARGE = 0.55f;
    private static final float CLOCK_TOP_SHARE = 0.12f;
    private static final float CLOCK_SCALE_SHARE = 0.011f;
    private static final float CLOCK_SCALE_LEAST = 3.0f;
    private static final float CLOCK_SCALE_MOST = 6.0f;
    private static final float CENTER_MARGIN = 5.0f;
    private static final float CLOCK_SLEEP_GROW = 0.12f;
    private static final float DOCK_DROP = 24.0f;
    private static final float HINT_SCALE = 1.4f;
    private static final float HINT_BOTTOM = 32.0f;
    private static final float HINT_ALPHA = 0.9f;
    private static final float HINT_BREATH_LOW = 0.8f;
    private static final float HINT_BREATH_MS = 4200.0f;
    private static final Component UNLOCK = Component.translatable("battlecraft.desktop.sleep.unlock");
    // WHY: меню по правой кнопке рисуется поверх окна обоев и центра управления, а те подняты по
    // WHY: глубине до 350: без подъёма его текст отсекался тестом глубины, и строки были пустыми
    private static final float CONTEXT_DEPTH = 200.0f;
    private static final int NOWHERE = -10_000;

    private final DesktopClock clock = new DesktopClock();
    private final DesktopDock dock;
    private final DesktopMenuBar menuBar;
    private final ControlCenter center;
    private final DesktopCalendar calendar = new DesktopCalendar();
    private final Spotlight spotlight;
    private final StudioMenu context = new StudioMenu();
    private final DesktopIsland island = new DesktopIsland();
    private final DesktopSleep sleep = new DesktopSleep();
    private boolean introArmed = true;

    public DesktopTitleScreen() {
        dock = new DesktopDock(List.of(
                new DesktopDock.Item(UiIcon.Kind.PERSON, "menu.singleplayer", this::openWorlds, false),
                new DesktopDock.Item(UiIcon.Kind.GLOBE, "menu.multiplayer", this::openServers, false),
                new DesktopDock.Item(UiIcon.Kind.GEAR, "menu.options", this::openOptions, false),
                new DesktopDock.Item(UiIcon.Kind.SLIDERS, "battlecraft.desktop.customize", CustomizeScreen::open, false),
                new DesktopDock.Item(UiIcon.Kind.PHOTO, "battlecraft.desktop.wallpapers", this::openGallery, false),
                new DesktopDock.Item(UiIcon.Kind.POWER, "menu.quit", this::quit, true)));
        menuBar = new DesktopMenuBar(this::pickBar);
        center = new ControlCenter(this::openGallery, () -> search(""));
        spotlight = new Spotlight(new SpotlightSource(List.of(
                action("menu.singleplayer", UiIcon.Kind.PERSON, this::openWorlds),
                action("menu.multiplayer", UiIcon.Kind.GLOBE, this::openServers),
                action("menu.options", UiIcon.Kind.GEAR, this::openOptions),
                action("battlecraft.desktop.customize", UiIcon.Kind.SLIDERS, CustomizeScreen::open),
                action("battlecraft.desktop.wallpapers", UiIcon.Kind.PHOTO, this::openGallery),
                action("menu.quit", UiIcon.Kind.POWER, this::quit))));
    }

    private static SpotlightSource.Result action(String key, UiIcon.Kind glyph, Runnable run) {
        return new SpotlightSource.Result(Component.translatable(key),
                Component.translatable("battlecraft.desktop.search.action"), glyph, owner -> run.run());
    }

    // WHY: меню по правой кнопке поднято по глубине выше поиска и осталось бы висеть над ним
    // WHY: мёртвым окном, поэтому поиск, открытый клавиатурой, сперва его закрывает
    private void search(String first) {
        context.close();
        spotlight.show(this, first);
    }

    // WHY: выбор мира и настройки возвращаются в тот же экземпляр экрана, а init зовёт ещё и ресайз:
    // WHY: проявление и сборка обоев взводятся только на открытии экрана
    @Override
    public void added() {
        super.added();
        introArmed = true;
        sleep.reset();
        spotlight.prepare();
    }

    @Override
    protected void init() {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        doze();
        MenuBackground.shared().render(graphics, this.width, this.height);
        UiBackdrop.capture();
        if (UiBoot.busy()) return;

        if (introArmed) {
            introArmed = false;
            MenuIntro.begin(this);
            DesktopWallpaper.shared().assembleOnce();
        }
        MenuIntro.advance();
        graphics.flush();
        boolean staged = MenuIntro.running() && UiStage.begin();
        float outerPresence = UiGlassStyle.scalePresence(MenuIntro.presence(staged));
        try {
            content(graphics, mouseX, mouseY);
        } finally {
            UiGlassStyle.restorePresence(outerPresence);
            if (staged) {
                graphics.flush();
                UiStage.end();
            }
        }
        if (staged) {
            UiAssemble.draw(graphics, this.width, this.height, UiStage.texture(),
                    MenuIntro.phase(), MenuIntro.seconds(), MenuIntro.mode(), 0.0f, this.height, INTRO_CHARGE);
        }
        spotlight.render(graphics, this.font, this.width, this.height, mouseX, mouseY);
    }

    // WHY: часы рисуются раньше острова, и раскрытая карточка ложится поверх них и строки меню, как
    // WHY: Dynamic Island; центр управления и меню по правой кнопке подняты по глубине и остаются над ней
    private void content(GuiGraphics graphics, int mouseX, int mouseY) {
        float gone = sleep.chromeGone();
        boolean modal = modal();
        int panelX = modal ? NOWHERE : mouseX;
        int panelY = modal ? NOWHERE : mouseY;
        boolean shaded = modal || shaded(mouseX, mouseY);
        int underX = shaded ? NOWHERE : mouseX;
        int underY = shaded ? NOWHERE : mouseY;
        clock(graphics);
        dock(graphics, underX, underY, gone);
        menuBar(graphics, underX, underY, gone);
        center.render(graphics, this.font, this.width - CENTER_MARGIN, DesktopMenuBar.HEIGHT + 3.0f,
                menuBar.middleOf(DesktopMenuBar.Slot.CENTER), panelX, panelY);
        calendar.render(graphics, this.font, this.width - CENTER_MARGIN, DesktopMenuBar.HEIGHT + 3.0f,
                menuBar.middleOf(DesktopMenuBar.Slot.CLOCK), panelX, panelY);
        contextMenu(graphics, mouseX, mouseY);
        hint(graphics);
    }

    // WHY: открытые поиск и меню по правой кнопке забирают любой щелчок себе, поэтому всё под ними
    // WHY: не подсвечивается и не озвучивается: щелчок туда только закрыл бы их
    private boolean modal() {
        return spotlight.open() || context.showing();
    }

    // WHY: центр управления и календарь лежат над островом и доком: курсор над ними не должен
    // WHY: подсвечивать и озвучивать кнопки, скрытые под панелью
    private boolean shaded(int mouseX, int mouseY) {
        return center.contains(mouseX, mouseY) || calendar.contains(mouseX, mouseY);
    }

    private void contextMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, CONTEXT_DEPTH);
        try {
            context.render(graphics, mouseX, mouseY);
        } finally {
            graphics.pose().popPose();
        }
    }

    // WHY: часы едут к середине экрана целым числом пикселей: ход через позу держит дату на сетке
    // WHY: покоя, и дробный конечный сдвиг оставил бы её размытой на всё время сна
    private void clock(GuiGraphics graphics) {
        float scale = Math.max(CLOCK_SCALE_LEAST, Math.min(CLOCK_SCALE_MOST, this.height * CLOCK_SCALE_SHARE));
        float settled = sleep.amount();
        float top = DesktopMenuBar.HEIGHT + this.height * CLOCK_TOP_SHARE;
        float middle = (this.height - clock.height(scale * (1.0f + CLOCK_SLEEP_GROW))) / 2.0f;
        double gui = Math.max(1.0, this.minecraft.getWindow().getGuiScale());
        float travel = (float) (Math.round((middle - top) * gui) / gui);
        UiRestFrame.shift(graphics, 0.0f, travel * settled);
        try {
            clock.render(graphics, this.font, this.width / 2.0f, top, scale * (1.0f + CLOCK_SLEEP_GROW * settled),
                    1.0f);
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    private void dock(GuiGraphics graphics, int mouseX, int mouseY, float gone) {
        if (gone >= 0.99f) return;

        UiRestFrame.shift(graphics, 0.0f, DOCK_DROP * gone);
        try {
            dock.render(graphics, this.font, this.width, this.height, mouseX, mouseY, 1.0f - gone);
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    private void menuBar(GuiGraphics graphics, int mouseX, int mouseY, float gone) {
        if (gone >= 0.99f) return;

        menuBar.hold(DesktopMenuBar.Slot.CENTER, center.open());
        menuBar.hold(DesktopMenuBar.Slot.CLOCK, calendar.open());
        boolean overIsland = island.contains(mouseX, mouseY);
        UiRestFrame.shift(graphics, 0.0f, -DesktopMenuBar.HEIGHT * gone);
        try {
            menuBar.render(graphics, this.font, this.width, overIsland ? NOWHERE : mouseX,
                    overIsland ? NOWHERE : mouseY, 1.0f - gone);
            island.render(graphics, this.font, this.width, menuBar.room(this.width / 2.0f), mouseX, mouseY,
                    1.0f - gone);
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    // WHY: подсказка лежит поверх размытого фона обычным чётким текстом, без стекла: её читают с
    // WHY: расстояния, а дыхание прозрачностью едва заметно, чтобы спящий экран не мигал
    private void hint(GuiGraphics graphics) {
        float shown = sleep.hint();
        if (shown <= 0.01f) return;

        float breath = UiAnim.pulse(HINT_BREATH_MS, HINT_BREATH_LOW, 1.0f);
        UiRender.labelCentered(graphics, this.font, UNLOCK, this.width / 2.0f, this.height - HINT_BOTTOM,
                HINT_SCALE, UiTheme.withAlpha(UiTheme.WHITE, HINT_ALPHA * breath * shown));
    }

    // WHY: открытая панель, поиск и перетаскивание полосы - это работа игрока, а не простой: сон
    // WHY: не отнимает их из-под руки, даже если две минуты курсор стоит на месте
    private void doze() {
        boolean held = introArmed || MenuIntro.running() || UiBoot.busy() || spotlight.open() || island.seeking()
                || center.open() || calendar.open() || context.showing();
        if (sleep.advance(held, UiFrame.delta())) fallAsleep();
        DesktopWallpaper.shared().sleep(sleep.amount());
    }

    private void fallAsleep() {
        island.collapse();
        center.close();
        calendar.close();
        context.close();
        spotlight.close();
    }

    private void pickBar(DesktopMenuBar.Slot slot, float anchorX, float anchorY) {
        switch (slot) {
            case BRAND -> context.show(List.of(
                    StudioMenu.Action.of("menu.options", this::openOptions),
                    StudioMenu.Action.of("battlecraft.desktop.customize", CustomizeScreen::open),
                    StudioMenu.Action.danger("menu.quit", this::quit)), anchorX, anchorY, this.width, this.height);
            case GAME -> context.show(List.of(
                    StudioMenu.Action.of("menu.singleplayer", this::openWorlds),
                    StudioMenu.Action.of("menu.multiplayer", this::openServers)), anchorX, anchorY, this.width, this.height);
            case VIEW -> context.show(viewActions(), anchorX, anchorY, this.width, this.height);
            case SEARCH -> search("");
            case CENTER -> {
                calendar.close();
                center.toggle();
            }
            case CLOCK -> {
                center.close();
                calendar.toggle();
            }
        }
    }

    private List<StudioMenu.Action> viewActions() {
        return List.of(
                StudioMenu.Action.of("battlecraft.desktop.wallpaper.change", this::openGallery),
                StudioMenu.Action.of("battlecraft.desktop.classic", this::switchToClassic));
    }

    private void switchToClassic() {
        HudConfig.menuDesktop(false);
        this.minecraft.setScreen(new MinimalTitleScreen());
    }

    private void openGallery() {
        center.close();
        WallpaperScreen.open(this);
    }

    private void openWorlds() {
        this.minecraft.setScreen(HudConfig.browseScreens() ? new WorldBrowseScreen(this) : new SelectWorldScreen(this));
    }

    private void openServers() {
        this.minecraft.setScreen(HudConfig.browseScreens() ? new ServerBrowseScreen(this) : new JoinMultiplayerScreen(this));
    }

    private void openOptions() {
        this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options));
    }

    private void quit() {
        this.minecraft.stop();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (sleep.wakeByButton(button)) return true;
        if (MenuIntro.running()) {
            MenuIntro.skip();
            return true;
        }
        if (spotlight.open()) {
            if (!spotlight.click(mouseX, mouseY)) spotlight.close();
            return true;
        }
        if (context.showing()) {
            if (!context.click(mouseX, mouseY)) context.close();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) return rightClick(mouseX, mouseY);
        return button == GLFW.GLFW_MOUSE_BUTTON_LEFT && leftClick(mouseX, mouseY);
    }

    private boolean leftClick(double mouseX, double mouseY) {
        if (center.click(mouseX, mouseY) || calendar.click(mouseX, mouseY)
                || !island.contains(mouseX, mouseY) && menuBar.click(mouseX, mouseY)) {
            island.collapse();
            return true;
        }
        center.close();
        calendar.close();
        if (island.mouseClicked(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_LEFT)) return true;
        return dock.click(mouseX, mouseY);
    }

    private boolean rightClick(double mouseX, double mouseY) {
        if (center.contains(mouseX, mouseY) || calendar.contains(mouseX, mouseY)) return true;
        if (island.mouseClicked(mouseX, mouseY, GLFW.GLFW_MOUSE_BUTTON_RIGHT)) return true;
        List<StudioMenu.Action> offered = clock.contains(mouseX, mouseY) ? clock.menu() : viewActions();
        context.show(offered, mouseX, mouseY, this.width, this.height);
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (sleep.moved(mouseX, mouseY, this.minecraft.getWindow().getGuiScale())) sleep.stir();
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (sleep.holdsButton(button)) return true;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (island.mouseDragged(mouseX, mouseY)) return true;
        center.drag(mouseX);
        return true;
    }

    // WHY: перемотку начинает только левая кнопка, и отпущенная посреди неё другая кнопка не должна
    // WHY: применять перемотку раньше времени
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (sleep.releaseButton(button)) return true;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseReleased(mouseX, mouseY, button);
        if (island.mouseReleased()) return true;
        return center.release() || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (sleep.stir()) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (sleep.wakeByKey(key)) return true;
        if (MenuIntro.running()) {
            MenuIntro.skip();
            sleep.holdKey(key);
            return true;
        }
        if (spotlight.key(key)) return true;
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (control && key == GLFW.GLFW_KEY_SPACE) {
            search("");
            sleep.holdKey(key);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) return closeTop();
        return super.keyPressed(key, scan, modifiers);
    }

    private boolean closeTop() {
        if (context.showing()) context.close();
        else if (center.open()) center.close();
        else if (calendar.open()) calendar.close();
        else if (island.expanded()) island.collapse();
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        if (sleep.releaseKey(key)) return true;
        return super.keyReleased(key, scan, modifiers);
    }

    // WHY: как в Spotlight, печать на пустом столе сразу становится поиском, без отдельного вызова
    @Override
    public boolean charTyped(char symbol, int modifiers) {
        if (sleep.holdsTyping()) return true;
        if (spotlight.open()) return spotlight.typed(symbol);
        if (!Character.isLetterOrDigit(symbol) || center.open() || calendar.open()) return false;
        search(String.valueOf(symbol));
        return true;
    }

    @Override
    public void onFilesDrop(List<Path> dropped) {
        sleep.stir();
        WallpaperScreen.openWith(this, dropped);
    }

    @Override
    public void removed() {
        spotlight.dismiss();
        context.dismiss();
        center.dismiss();
        calendar.dismiss();
        island.dismiss();
        dock.forget();
        menuBar.forget();
        sleep.reset();
        DesktopWallpaper.shared().sleep(0.0f);
        UiGlassText.release();
        super.removed();
    }

    public static Screen fresh() {
        return HudConfig.menuDesktop() ? new DesktopTitleScreen() : new MinimalTitleScreen();
    }
}
