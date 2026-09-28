package com.persiki84.battlecraft.client.menu.desktop;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.wallpaper.WallpaperEntry;
import com.persiki84.battlecraft.client.wallpaper.WallpaperImport;
import com.persiki84.battlecraft.client.wallpaper.WallpaperLibrary;
import com.persiki84.shared.client.menu.GlassScreen;
import com.persiki84.shared.client.menu.MenuHint;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.shared.client.menu.studio.StudioMenu;
import com.persiki84.shared.client.ui.Ambient;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAmbience;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.shared.client.ui.UiIcon;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class WallpaperScreen extends GlassScreen implements Ambient {
    private static final float PAD = 10.0f;
    private static final float TITLE = 16.0f;
    private static final float TILE = 50.0f;
    private static final float GAP = 6.0f;
    private static final int COLUMNS = 4;
    private static final float WIDTH = PAD * 2.0f + COLUMNS * TILE + (COLUMNS - 1) * GAP;
    private static final float INNER = WIDTH - PAD * 2.0f;
    private static final int ROW_HEIGHT = 22;
    private static final float ROW_GAP = 4.0f;
    private static final float SECTION_GAP = 10.0f;
    private static final float HINT_SCALE = 0.72f;
    private static final float HINT_LEADING = 2.0f;
    private static final float NAME_SCALE = 0.68f;
    private static final float NAME_ROOM = TILE - 6.0f;
    private static final float NAME_DROP = 11.0f;
    private static final float ICON_SIZE = 18.0f;
    private static final float ICON_DROP = 20.0f;
    private static final float TILE_RADIUS = 10.0f;
    private static final float TILE_LIFT = 0.25f;
    private static final float TILE_LIFT_HOVER = 0.45f;
    private static final float HOVER_WHITEN = 0.3f;
    private static final float PICK_INSET = 2.0f;
    private static final float PICK_RADIUS = 12.0f;
    private static final float PICK_ALPHA = 0.85f;
    private static final float BAR_INSET = 6.0f;
    private static final float BAR_DROP = 4.0f;
    private static final float BAR_HEIGHT = 2.0f;
    private static final float LAYOUT_SPEED = 14.0f;
    private static final float FADE_SPEED = 16.0f;
    private static final float PICK_SPEED = 20.0f;
    private static final float GONE = 0.02f;
    private static final float SCREEN_MARGIN = 12.0f;
    private static final float GLIDE_SPEED = 11.0f;
    private static final float GLIDE_MAX_ROWS = 2.0f;
    private static final float SCROLLBAR_WIDTH = 2.0f;
    private static final float SCROLLBAR_ALPHA = 0.6f;
    private static final int ERROR_INK = 0xFFE0645A;
    private static final String ELLIPSIS = "...";
    private static final String DROP_KEY = "battlecraft.desktop.wallpaper.drop";
    private static final String IMPORTING_KEY = "battlecraft.desktop.wallpaper.importing";
    private static final String READ_FAILED_KEY = "battlecraft.wallpaper.error.read";

    private record Tile(String id, String name, UiIcon.Kind glyph, Smooth born, Smooth hover) {
        boolean adds() {
            return id == null;
        }
    }

    private record ImportJob(WallpaperImport job, boolean adopt) {
    }

    private final Screen parent;
    private final List<Tile> tiles = new ArrayList<>();
    private final List<ImportJob> imports = new ArrayList<>();
    private final StudioMenu context = new StudioMenu();
    private final Component planetDotted = Component.translatable("battlecraft.desktop.wallpaper.dots.planet");
    private final Smooth gridShown = new Smooth(LAYOUT_SPEED);
    private final Smooth hintHeight = new Smooth(LAYOUT_SPEED);
    private final Smooth hintAlpha = new Smooth(1.0f, FADE_SPEED);
    private final Smooth barShown = new Smooth(0.0f, FADE_SPEED);
    private final Smooth pickX = new Smooth(PICK_SPEED);
    private final Smooth pickY = new Smooth(PICK_SPEED);
    private final Smooth glide = new Smooth(0.0f, GLIDE_SPEED);
    private final Smooth scrollbar = new Smooth(0.0f, FADE_SPEED);
    private int rowScroll;
    private int rowsShown = 1;
    private boolean revealing = true;
    private List<FormattedCharSequence> hintLines = List.of();
    private String hintKey = "";
    private boolean hintError;
    private String failedKey;
    private String removing;
    private float barValue;
    private boolean fresh;
    private boolean placed;
    private ToggleRow dotsRow;
    private ToggleRow themedRow;
    private float left;
    private float top;

    private WallpaperScreen(Screen parent) {
        super(Component.translatable("battlecraft.desktop.wallpapers"));
        this.parent = parent;
        scanFolder();
    }

    public static void open(Screen parent) {
        Minecraft.getInstance().setScreen(new WallpaperScreen(parent));
    }

    public static void openWith(Screen parent, List<Path> dropped) {
        WallpaperScreen screen = new WallpaperScreen(parent);
        screen.accept(dropped);
        Minecraft.getInstance().setScreen(screen);
    }

    // WHY: окно для выбора обоев, и под дымкой настроения окна их было бы не разглядеть: точки
    // WHY: растворяются в ней, и переключатели «Точки» и «Цвет темы» меняли бы невидимое
    @Override
    public UiAmbience.Mood ambience() {
        return UiAmbience.Mood.CLEAR;
    }

    @Override
    protected void init() {
        dotsRow = addRenderableWidget(toggle("battlecraft.desktop.wallpaper.dots",
                HudConfig::wallpaperDots, HudConfig::wallpaperDots));
        themedRow = addRenderableWidget(toggle("battlecraft.desktop.wallpaper.themed",
                HudConfig::wallpaperThemed, HudConfig::wallpaperThemed));
    }

    private static ToggleRow toggle(String key, BooleanSupplier value, Consumer<Boolean> apply) {
        ToggleRow row = new ToggleRow(0, 0, (int) INNER, ROW_HEIGHT, Component.translatable(key), value, apply);
        row.hint(key + MenuHint.SUFFIX);
        return row;
    }

    // WHY: файлы, положенные в папку обоев руками, подхватываются при открытии окна, иначе их
    // WHY: пришлось бы ещё и перетаскивать в игру
    private void scanFolder() {
        List<String> known = new ArrayList<>();
        for (WallpaperEntry entry : WallpaperLibrary.entries()) known.add(entry.name());
        try (Stream<Path> files = Files.list(WallpaperLibrary.folder())) {
            files.filter(Files::isRegularFile).filter(WallpaperLibrary::supports)
                    .filter(file -> !known.contains(file.getFileName().toString()))
                    .filter(file -> !WallpaperLibrary.refused(file))
                    .forEach(file -> imports.add(new ImportJob(WallpaperLibrary.importFile(file), false)));
        } catch (IOException error) {
            failedKey = READ_FAILED_KEY;
        }
    }

    private void accept(List<Path> dropped) {
        for (Path path : dropped) imports.add(new ImportJob(WallpaperLibrary.importFile(path), true));
    }

    // WHY: диалог выбора живёт дольше окна: пока он открыт, окно могли закрыть или открыть заново,
    // WHY: и выбор уходит в то окно, что открыто сейчас, а без окна просто ложится в библиотеку
    private static void receive(List<Path> picked) {
        if (Minecraft.getInstance().screen instanceof WallpaperScreen open) {
            open.accept(picked);
            return;
        }
        for (Path path : picked) WallpaperLibrary.importFile(path);
    }

    @Override
    public void onFilesDrop(List<Path> dropped) {
        accept(dropped);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float delta = UiFrame.delta();
        settleImports();
        releaseRemoved();
        collect();
        wrapHint(graphics, delta);
        layout(delta);
        UiGlass.sheet(graphics, left, top, WIDTH, panelHeight(), UiGlassStyle.radiusPanel() + 3.0f, 1.0f);
        UiGlass.layer(graphics);
        UiRender.textScaled(graphics, this.font, this.title, left + PAD, top + PAD, 1.0f, UiAccent.text(), false);
        paintGrid(graphics, mouseX, mouseY, delta);
        paintScrollbar(graphics, delta);
        placeRows();
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        paintHint(graphics);
        context.render(graphics, mouseX, mouseY);
    }

    // WHY: только что перетащенные обои сразу встают на стол: игрок кинул файл, чтобы его увидеть
    private void settleImports() {
        for (int index = imports.size() - 1; index >= 0; index--) {
            ImportJob item = imports.get(index);
            WallpaperImport.State state = item.job().state();
            if (state == WallpaperImport.State.RUNNING) continue;
            imports.remove(index);
            fresh = false;
            finish(item, state);
        }
    }

    private void finish(ImportJob item, WallpaperImport.State state) {
        if (state == WallpaperImport.State.FAILED) {
            failedKey = item.job().failureKey();
            UiSound.alert();
            return;
        }
        failedKey = null;
        if (!item.adopt()) return;
        HudConfig.wallpaper(item.job().id());
        revealing = true;
        UiSound.toggle(true);
    }

    // WHY: выбранные обои удаляются только после того, как стол снял с них кадр и ушёл на планету:
    // WHY: удаление гасит проигрыватель сразу, и смена начиналась бы с пустого чёрного кадра
    private void releaseRemoved() {
        if (removing == null || DesktopWallpaper.shared().shows(removing)) return;
        WallpaperLibrary.remove(removing);
        removing = null;
        fresh = false;
    }

    private WallpaperImport running() {
        for (ImportJob item : imports) {
            if (item.job().state() == WallpaperImport.State.RUNNING) return item.job();
        }
        return null;
    }

    private void collect() {
        if (fresh) return;
        fresh = true;
        List<Tile> older = new ArrayList<>(tiles);
        tiles.clear();
        tiles.add(tile(older, "", Component.translatable("battlecraft.desktop.wallpaper.planet").getString(),
                UiIcon.Kind.SPARKLE));
        for (WallpaperEntry entry : WallpaperLibrary.entries()) {
            if (entry.id().equals(removing)) continue;
            tiles.add(tile(older, entry.id(), entry.name(), UiIcon.Kind.PHOTO));
        }
        tiles.add(tile(older, null, Component.translatable("battlecraft.desktop.wallpaper.add").getString(),
                UiIcon.Kind.PLUS));
    }

    private Tile tile(List<Tile> older, String id, String name, UiIcon.Kind glyph) {
        for (Tile previous : older) {
            if (Objects.equals(previous.id(), id)) {
                return new Tile(id, fitted(name), glyph, previous.born(), previous.hover());
            }
        }
        return new Tile(id, fitted(name), glyph, new Smooth(placed ? 0.0f : 1.0f, FADE_SPEED),
                new Smooth(0.0f, FADE_SPEED));
    }

    private String fitted(String name) {
        if (UiRender.width(this.font, name) * NAME_SCALE <= NAME_ROOM) return name;
        String cut = name;
        while (!cut.isEmpty() && UiRender.width(this.font, cut + ELLIPSIS) * NAME_SCALE > NAME_ROOM) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + ELLIPSIS;
    }

    // WHY: подсказка переносится по ширине панели и пересобирается только при смене текста; смена
    // WHY: идёт через угасание, а высота панели доезжает за строками, а не прыгает
    private void wrapHint(GuiGraphics graphics, float delta) {
        String wanted = wantedHint();
        boolean swapping = !hintLines.isEmpty() && !wanted.equals(hintKey);
        float shown = hintAlpha.to(swapping ? 0.0f : 1.0f, delta);
        if (hintLines.isEmpty() || swapping && shown <= GONE) {
            hintKey = wanted;
            hintError = wanted.equals(failedKey);
            hintLines = UiRender.split(graphics, this.font, Component.translatable(wanted), HINT_SCALE,
                    (int) (INNER / HINT_SCALE));
        }
        hintHeight.to(linesHeight(hintLines.size()), delta);
    }

    private String wantedHint() {
        if (failedKey != null && !failedKey.isEmpty()) return failedKey;
        return running() != null ? IMPORTING_KEY : DROP_KEY;
    }

    private float linesHeight(int lines) {
        return lines * this.font.lineHeight * HINT_SCALE + Math.max(0, lines - 1) * HINT_LEADING;
    }

    private void layout(float delta) {
        rowsShown = Math.min(rowsTotal(), rowsFitting());
        int allowed = clampScroll(rowScroll);
        if (allowed != rowScroll) scrollGrid(allowed - rowScroll);
        revealChosen();
        gridShown.to(rowsShown * TILE + Math.max(0, rowsShown - 1) * GAP, delta);
        left = (this.width - WIDTH) / 2.0f;
        top = (this.height - panelHeight()) / 2.0f;
        placed = true;
    }

    // WHY: выбранные обои всегда в виду: при открытии окна сетка сразу стоит на их ряду, а только
    // WHY: что добавленные доезжают прокруткой. До первого кадра глайд и рамку трогать нельзя
    private void revealChosen() {
        if (!revealing) return;
        revealing = false;
        int row = chosenIndex() / COLUMNS;
        int wanted = clampScroll(Math.min(row, Math.max(rowScroll, row - rowsShown + 1)));
        if (placed) scrollGrid(wanted - rowScroll);
        else rowScroll = wanted;
    }

    private int rowsTotal() {
        return (tiles.size() + COLUMNS - 1) / COLUMNS;
    }

    // WHY: панель не выше экрана: сетка берёт столько рядов, сколько остаётся под заголовком,
    // WHY: переключателями и подсказкой, остальные ряды прокручиваются колесом
    private int rowsFitting() {
        float chrome = PAD + TITLE + SECTION_GAP + ROW_HEIGHT * 2.0f + ROW_GAP + SECTION_GAP
                + linesHeight(hintLines.size()) + PAD;
        float room = this.height - SCREEN_MARGIN * 2.0f - chrome;
        return Math.max(1, (int) ((room + GAP) / (TILE + GAP)));
    }

    private int clampScroll(int wanted) {
        return Math.max(0, Math.min(wanted, rowsTotal() - rowsShown));
    }

    private boolean scrollable() {
        return rowsTotal() > rowsShown;
    }

    // WHY: ряды встают на новое место сразу, а видимый сдвиг доезжает глайдом к нулю, как у
    // WHY: списков мода. Рамка выбора стоит на тех же рядах, поэтому сдвигается вместе с ними
    private boolean scrollGrid(int step) {
        int next = clampScroll(rowScroll + step);
        if (next == rowScroll) return false;
        float travelled = (next - rowScroll) * (TILE + GAP);
        float cap = GLIDE_MAX_ROWS * (TILE + GAP);
        rowScroll = next;
        glide.snap(Math.max(-cap, Math.min(cap, glide.get() + travelled)));
        pickY.snap(pickY.get() - travelled);
        return true;
    }

    // WHY: весь ход прокрутки заявлен через UiRestFrame: место покоя плиток - их ряд после
    // WHY: прокрутки, и подписи едут без ступеней. Ножницы только у прокручиваемой сетки: у
    // WHY: короткой они срезали бы тень нижнего ряда
    private void paintGrid(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        float glided = glide.to(0.0f, delta);
        boolean clipped = scrollable() || Math.abs(glided) > GONE;
        if (clipped) UiRender.clip(graphics, left, gridTop() - PICK_INSET, WIDTH, gridShown.get() + PICK_INSET * 2.0f);
        UiRestFrame.shift(graphics, 0.0f, glided);
        try {
            paintPick(graphics, delta);
            int hovered = context.showing() ? -1 : tileAt(mouseX, mouseY);
            for (int index = 0; index < tiles.size(); index++) paintTile(graphics, index, index == hovered, delta);
            graphics.flush();
        } finally {
            UiRestFrame.pop(graphics);
            if (clipped) graphics.disableScissor();
        }
    }

    private void paintScrollbar(GuiGraphics graphics, float delta) {
        float shown = scrollbar.to(scrollable() ? 1.0f : 0.0f, delta);
        int total = rowsTotal();
        if (shown <= GONE || total <= 0) return;
        float track = gridShown.get();
        float thumb = track * Math.min(1.0f, rowsShown / (float) total);
        float travel = Math.max(1, total - rowsShown);
        float position = UiAnim.clamp01((rowScroll - glide.get() / (TILE + GAP)) / travel);
        float x = left + WIDTH - (PAD + SCROLLBAR_WIDTH) / 2.0f;
        UiRender.panel(graphics, x, gridTop() + (track - thumb) * position, SCROLLBAR_WIDTH, thumb,
                SCROLLBAR_WIDTH / 2.0f, UiTheme.alpha(UiAccent.textFaint(), SCROLLBAR_ALPHA * shown));
    }

    private float gridTop() {
        return top + PAD + TITLE;
    }

    private float panelHeight() {
        return rowsTop() - top + ROW_HEIGHT * 2.0f + ROW_GAP + SECTION_GAP + hintHeight.get() + PAD;
    }

    private float rowsTop() {
        return top + PAD + TITLE + gridShown.get() + SECTION_GAP;
    }

    private float hintTop() {
        return rowsTop() + ROW_HEIGHT * 2.0f + ROW_GAP + SECTION_GAP;
    }

    private void paintPick(GuiGraphics graphics, float delta) {
        int chosen = chosenIndex();
        float x = pickX.to(tileX(chosen) - PICK_INSET, delta);
        float y = pickY.to(tileY(chosen) - PICK_INSET, delta);
        UiRender.panel(graphics, x, y, TILE + PICK_INSET * 2.0f, TILE + PICK_INSET * 2.0f, PICK_RADIUS,
                UiTheme.alpha(UiAccent.color(), PICK_ALPHA * insideGrid(y + PICK_INSET + glide.get())));
    }

    // WHY: сетка и подсказка растут плавно, а плитки и строки встают на место сразу: всё, что
    // WHY: ещё не поместилось в выросшую панель, проявляется по мере роста, а не висит под ней.
    // WHY: Ряд, уходящий прокруткой за край сетки, так же гаснет, а не обрывается ножницами
    private float insideGrid(float liveY) {
        float below = UiAnim.clamp01((gridTop() + gridShown.get() - liveY) / TILE);
        return below * UiAnim.clamp01((liveY + TILE - gridTop()) / TILE);
    }

    private float insideHint(float y) {
        float line = this.font.lineHeight * HINT_SCALE;
        return UiAnim.clamp01((hintTop() + hintHeight.get() - y) / line);
    }

    private int chosenIndex() {
        String current = HudConfig.wallpaper();
        for (int index = 0; index < tiles.size(); index++) {
            if (current.equals(tiles.get(index).id())) return index;
        }
        return 0;
    }

    private float tileX(int index) {
        return left + PAD + (index % COLUMNS) * (TILE + GAP);
    }

    private float tileY(int index) {
        return gridTop() + (index / COLUMNS - rowScroll) * (TILE + GAP);
    }

    private void paintTile(GuiGraphics graphics, int index, boolean over, float delta) {
        Tile tile = tiles.get(index);
        float x = tileX(index);
        float y = tileY(index);
        float alpha = tile.born().to(1.0f, delta) * insideGrid(y + glide.get());
        float hover = tile.hover().to(over ? 1.0f : 0.0f, delta);
        UiGlass.window(graphics, x, y, TILE, TILE, TILE_RADIUS, alpha, TILE_LIFT + TILE_LIFT_HOVER * hover);
        int ink = UiTheme.mix(UiAccent.text(), UiTheme.WHITE, HOVER_WHITEN * hover);
        UiIcon.draw(graphics, tile.glyph(), x + TILE / 2.0f, y + ICON_DROP, ICON_SIZE, UiTheme.alpha(ink, alpha));
        UiRender.textCentered(graphics, this.font, tile.name(), x + TILE / 2.0f, y + TILE - NAME_DROP,
                NAME_SCALE, UiTheme.alpha(UiAccent.text(), alpha), false);
        if (tile.adds()) paintProgress(graphics, x, y, alpha, delta);
    }

    private void paintProgress(GuiGraphics graphics, float x, float y, float alpha, float delta) {
        WallpaperImport job = running();
        if (job != null) barValue = job.progress();
        float shown = barShown.to(job != null ? 1.0f : 0.0f, delta);
        if (shown <= GONE) return;
        UiGlass.progress(graphics, x + BAR_INSET, y + TILE - BAR_DROP, TILE - BAR_INSET * 2.0f, BAR_HEIGHT,
                barValue, UiAccent.color(), shown * alpha);
    }

    private void placeRows() {
        int x = Math.round(left + PAD);
        float y = rowsTop();
        dotsRow.setX(x);
        dotsRow.setY(Math.round(y));
        themedRow.setX(x);
        themedRow.setY(Math.round(y + ROW_HEIGHT + ROW_GAP));
        boolean planet = HudConfig.wallpaper().isEmpty();
        if (planet == dotsRow.blocked()) return;
        if (planet) {
            dotsRow.block(planetDotted);
        } else {
            dotsRow.unblock();
        }
    }

    private void paintHint(GuiGraphics graphics) {
        float alpha = hintAlpha.get();
        if (alpha <= GONE) return;
        int ink = hintError ? ERROR_INK : UiAccent.textDim();
        float step = this.font.lineHeight * HINT_SCALE + HINT_LEADING;
        float y = hintTop();
        for (FormattedCharSequence line : hintLines) {
            int color = UiTheme.alpha(ink, alpha * insideHint(y));
            UiRender.textLine(graphics, this.font, line, left + PAD, y, HINT_SCALE, color, false);
            y += step;
        }
    }

    private int tileAt(double mouseX, double mouseY) {
        if (mouseY < gridTop() || mouseY > gridTop() + gridShown.get()) return -1;
        for (int index = 0; index < tiles.size(); index++) {
            float x = tileX(index);
            float y = tileY(index) + glide.get();
            if (mouseX >= x && mouseX <= x + TILE && mouseY >= y && mouseY <= y + TILE) return index;
        }
        return -1;
    }

    private boolean insidePanel(double mouseX, double mouseY) {
        return mouseX >= left && mouseX <= left + WIDTH && mouseY >= top && mouseY <= top + panelHeight();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (leaving()) return true;
        double x = localX(mouseX, mouseY);
        double y = localY(mouseX, mouseY);
        if (context.showing()) {
            if (!context.click(x, y)) context.close();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) return offerRemoval(x, y);
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int index = tileAt(x, y);
        if (index >= 0) {
            choose(tiles.get(index));
        } else if (!insidePanel(x, y)) {
            onClose();
        }
        return true;
    }

    private void choose(Tile tile) {
        UiSound.press();
        if (tile.adds()) {
            WallpaperPicker.pick(WallpaperScreen::receive);
            return;
        }
        HudConfig.wallpaper(tile.id());
    }

    private boolean offerRemoval(double x, double y) {
        int index = tileAt(x, y);
        String id = index < 0 ? null : tiles.get(index).id();
        if (id == null || id.isEmpty()) return true;
        context.show(List.of(StudioMenu.Action.careful("battlecraft.desktop.wallpaper.remove", () -> remove(id))),
                x, y, this.width, this.height);
        return true;
    }

    private void remove(String id) {
        fresh = false;
        if (!id.equals(HudConfig.wallpaper())) {
            WallpaperLibrary.remove(id);
            return;
        }
        removing = id;
        HudConfig.wallpaper("");
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (leaving() || context.showing()) return true;
        scrollGrid(amount > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE && context.showing()) {
            context.close();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    protected void closing() {
        if (removing != null) WallpaperLibrary.remove(removing);
        removing = null;
        context.close();
        if (parent instanceof GlassScreen glass) glass.reenter();
        this.minecraft.setScreen(parent);
    }
}
