package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.client.custom.CustomPreset;
import com.persiki84.battlecraft.client.custom.Customization;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.custom.PaletteKey;
import com.persiki84.battlecraft.client.island.AvatarPicture;
import com.persiki84.battlecraft.client.island.AvatarSource;
import com.persiki84.battlecraft.client.voice.VoiceOptions;
import com.persiki84.shared.client.menu.KeyRow;
import com.persiki84.shared.client.menu.ManagerScreen;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.MenuSearch;
import com.persiki84.shared.client.menu.PaletteWindow;
import com.persiki84.shared.client.menu.SearchBeacon;
import com.persiki84.shared.client.menu.SearchField;
import com.persiki84.shared.client.menu.studio.StudioMenu;
import com.persiki84.shared.client.menu.studio.StudioNav;
import com.persiki84.shared.client.menu.studio.StudioScreen;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiMetrics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

// WHY: кастомизация стоит на студии, как все редакторы: слева вкладки и их группы, в центре живой
// WHY: образец того, что правится (стекло, цвета мира, остров, плашки HUD, пресеты плитками), справа
// WHY: все ручки выбранной группы разом. Строки собраны один раз каталогом и только переставляются
public final class CustomizeScreen extends StudioScreen implements CustomHost {
    private static final String MENU_ID = "battlecraft:customize";
    private static final int CANVAS = 262;
    private static final int INSPECTOR = 300;
    private static final int ROWS_WIDTH = INSPECTOR - (int) UiMetrics.PAD * 2;
    private static final float ENTER_SECONDS = 1.05f;
    private static final float BEACON_READY = 0.99f;
    private static final String PRESETS = CustomTab.PRESETS.nodeKey();
    private static final Object SEARCHING = new Object();
    private static final List<UiButton> BUTTONS = List.of();

    private final CustomCatalog catalog = new CustomCatalog(this, ROWS_WIDTH);
    private final CustomInspector columns = new CustomInspector(catalog);
    private final PresetDesk desk = new PresetDesk(catalog.kit());
    private final CustomPreview preview = new CustomPreview();
    private final SearchField search;
    private final SearchBeacon beacon = new SearchBeacon();
    private final List<StudioNav.Node> nodes;
    private String selected = CustomTab.GLASS.nodeKey();
    private String needle = "";
    private int scope;
    private AbstractWidget sought;
    private String soughtPreset;
    private AbstractWidget lit;
    private boolean entered;
    private String presetSource;
    private Object presetSubject = PRESETS;
    private CustomPreset statsPreset;
    private Component statsText = Component.empty();
    private Object token = new Object();
    private boolean shownLiquid = Customization.liquid();
    private boolean shownInk = Customization.accentInk();

    public CustomizeScreen() {
        this(null);
    }

    public CustomizeScreen(Screen parent) {
        super(Component.translatable("battlecraft.custom.title"), parent);
        this.search = new SearchField(Component.translatable("battlecraft.menu.search"), this::onSearch);
        this.nodes = buildNodes();
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new CustomizeScreen());
    }

    public static void openAt(String rowKey) {
        CustomizeScreen screen = new CustomizeScreen();
        screen.seek(rowKey);
        Minecraft.getInstance().setScreen(screen);
    }

    // WHY: ориентиры для поиска рабочего стола собирает тот же каталог, что и экран, но без экрана:
    // WHY: строки не показываются, и отклик им не нужен, поэтому хозяин у них немой
    public static List<ManagerScreen.Landmark> searchIndex() {
        return new CustomCatalog(Inert.HOST, ROWS_WIDTH).landmarks();
    }

    private List<StudioNav.Node> buildNodes() {
        List<StudioNav.Node> built = new ArrayList<>();
        for (CustomTab tab : CustomTab.values()) {
            built.add(StudioNav.Node.item(tab.nodeKey(), tab.title(), Component.empty(), 0, false));
            for (CustomSection section : catalog.of(tab)) {
                built.add(StudioNav.Node.item(section.key(), section.title(), Component.empty(), 1, false));
            }
        }
        return List.copyOf(built);
    }

    private void seek(String rowKey) {
        CustomTab tab = CustomCatalog.tabTitled(rowKey);
        if (tab != null) {
            choose(tab.nodeKey());
            return;
        }
        if (seekPreset(rowKey)) return;
        CustomTab reset = catalog.resetTab(rowKey);
        if (reset != null) {
            choose(reset.nodeKey());
            sought = catalog.resetRow(reset, rowKey);
            return;
        }
        CustomSection section = catalog.holding(rowKey);
        if (section == null) return;
        choose(section.key());
        sought = catalog.row(section, rowKey);
    }

    // WHY: пресет и общий сброс лежат в галерее пресетов: поиск открывает её на нужной плитке, а общий
    // WHY: сброс показывает у текущего встроенного пресета, под которым стоит его строка
    private boolean seekPreset(String rowKey) {
        CustomPreset preset = CustomCatalog.presetTitled(rowKey);
        boolean resetAll = CustomCatalog.RESET_ALL.equals(rowKey);
        if (preset == null && !resetAll) return false;
        choose(PRESETS);
        soughtPreset = PresetCard.keyOf(preset != null ? preset : Customization.preset());
        if (resetAll) sought = desk.resetRow();
        return true;
    }

    private void choose(String key) {
        selected = key;
        scope = 0;
        for (int index = 0; index < nodes.size(); index++) {
            if (nodes.get(index).key().equals(key)) scope = index;
        }
    }

    @Override
    protected void init() {
        if (!entered) {
            entered = true;
            nodeShown();
        }
        super.init();
        revealSought();
    }

    // WHY: строка, к которой привёл поиск рабочего стола, встаёт в середину колонки и один раз
    // WHY: разгорается: на длинной группе её иначе пришлось бы искать глазами второй раз
    private void revealSought() {
        if (sought == null) return;
        lit = sought;
        sought = null;
        if (inspector.center(lit)) layout();
        beacon.arm();
    }

    private void nodeShown() {
        if (!PRESETS.equals(selected)) return;
        desk.openedOn(soughtPreset);
        soughtPreset = null;
    }

    @Override
    protected String menuId() {
        return MENU_ID;
    }

    @Override
    protected float enterSeconds() {
        return ENTER_SECONDS;
    }

    @Override
    protected int canvasWidth() {
        return CANVAS;
    }

    @Override
    protected int inspectorWidth() {
        return INSPECTOR;
    }

    @Override
    protected SearchField search() {
        return search;
    }

    @Override
    protected int carryScope() {
        return searching() ? -1 : scope;
    }

    @Override
    protected List<StudioNav.Node> nodes() {
        return nodes;
    }

    @Override
    protected String selectedNode() {
        return selected;
    }

    @Override
    protected void selectNode(StudioNav.Node node) {
        choose(node.key());
        if (searching()) search.clear();
        nodeShown();
    }

    @Override
    protected Component stats() {
        CustomPreset preset = Customization.preset();
        if (preset != statsPreset) {
            statsPreset = preset;
            statsText = Component.translatable("battlecraft.custom.preset.current",
                    Component.translatable(preset.translationKey()));
        }
        return statsText;
    }

    @Override
    protected List<UiButton> headerButtons() {
        return BUTTONS;
    }

    // WHY: жидкое стекло и акцент текста меняют блокировки соседних строк, и меняют их не только
    // WHY: строки экрана: пресет, мастер, поиск рабочего стола. Смена сама переложит колонку
    @Override
    protected Object stamp() {
        boolean liquid = Customization.liquid();
        boolean ink = Customization.accentInk();
        if (liquid != shownLiquid || ink != shownInk) {
            shownLiquid = liquid;
            shownInk = ink;
            token = new Object();
        }
        return token;
    }

    @Override
    protected Object inspectorSubject() {
        if (searching()) return SEARCHING;
        if (!PRESETS.equals(selected)) return selected;
        String chosen = desk.selected();
        if (chosen != presetSource) {
            presetSource = chosen;
            presetSubject = PRESETS + chosen;
        }
        return presetSubject;
    }

    @Override
    protected void buildInspector(StudioStack stack, int x, int width) {
        catalog.holdLocks();
        if (searching()) {
            columns.search(stack, x, width, needle);
            return;
        }
        if (presetsShown()) {
            desk.buildInspector(stack, x);
            return;
        }
        CustomTab tab = tabOf(selected);
        if (tab != null) {
            columns.tab(stack, x, width, tab);
            return;
        }
        CustomSection section = catalog.section(selected);
        if (section != null) columns.section(stack, x, width, section);
    }

    @Override
    protected int artHeight() {
        return presetsShown() && !searching() ? PresetDesk.ART_HEIGHT : 0;
    }

    @Override
    protected void renderArt(GuiGraphics graphics, float left, float top, float width, float appear,
                             int mouseX, int mouseY) {
        if (presetsShown() && !searching()) desk.renderArt(graphics, this.font, left, top, width, appear);
    }

    @Override
    protected boolean gridCanvas() {
        return false;
    }

    @Override
    protected boolean paintsCanvas() {
        return true;
    }

    @Override
    protected void renderPainted(GuiGraphics graphics, float left, float top, float width, float height,
                                 int mouseX, int mouseY) {
        holdTalkKey();
        if (presetsShown()) {
            desk.render(graphics, left, top, width, height, mouseX, mouseY);
            return;
        }
        preview.render(graphics, focus(), caption(), left, top, width, height, mouseX, mouseY);
    }

    // WHY: строка клавиши разговора живая только при разговоре по нажатию: при голосовой активации
    // WHY: клавиши нет, и строка гаснет, как и раньше
    private void holdTalkKey() {
        KeyRow talk = catalog.kit().talkKey();
        if (talk != null) talk.active = VoiceOptions.pushToTalk();
    }

    @Override
    protected boolean pressPainted(double mouseX, double mouseY) {
        if (presetsShown()) return desk.press(mouseX, mouseY);
        HudSlot slot = preview.slotAt(focus(), mouseX, mouseY);
        if (slot != null) {
            PreviewHud.toggle(slot);
            return false;
        }
        PaletteKey key = preview.colorAt(mouseX, mouseY);
        if (key != null) openPalette(key);
        return false;
    }

    @Override
    protected void scrollPainted(double mouseX, double mouseY, double amount) {
        if (!presetsShown()) {
            preview.scroll(mouseX, mouseY, amount);
        } else if (desk.over(mouseX, mouseY)) {
            desk.scroll(amount);
        }
    }

    @Override
    protected List<StudioMenu.Action> paintedActions(double mouseX, double mouseY) {
        if (presetsShown()) return desk.actions(mouseX, mouseY);
        HudSlot slot = preview.slotAt(focus(), mouseX, mouseY);
        if (slot != null) return slotActions(slot);
        List<StudioMenu.Action> actions = new ArrayList<>();
        PaletteKey key = preview.colorAt(mouseX, mouseY);
        if (key != null) colorActions(actions, key);
        actions.addAll(groupActions(selected));
        return actions;
    }

    private void colorActions(List<StudioMenu.Action> actions, PaletteKey key) {
        Component name = Component.translatable(key.translationKey());
        actions.add(new StudioMenu.Action(Component.translatable("battlecraft.custom.color.edit", name),
                () -> openPalette(key), false, false, true));
        actions.add(new StudioMenu.Action(Component.translatable("battlecraft.custom.color.clear", name),
                () -> clearColor(key), false, false, Customization.colorTouched(key)));
    }

    private List<StudioMenu.Action> slotActions(HudSlot slot) {
        boolean visible = HudLayout.of(slot).visible();
        return List.of(
                StudioMenu.Action.of(visible ? "battlecraft.custom.hud.hide" : "battlecraft.custom.hud.show",
                        () -> PreviewHud.toggle(slot)),
                StudioMenu.Action.careful("battlecraft.custom.reset.hud",
                        () -> TabResets.done(catalog.kit(), Customization::resetHud)));
    }

    @Override
    protected List<StudioMenu.Action> nodeActions(StudioNav.Node node) {
        return groupActions(node.key());
    }

    private List<StudioMenu.Action> groupActions(String key) {
        CustomTab tab = tabOf(key);
        if (tab != null) return TabResets.actions(tab, catalog.kit());
        CustomSection section = catalog.section(key);
        if (section == null || !section.resettable()) return List.of();
        return List.of(StudioMenu.Action.of("battlecraft.custom.reset.group",
                () -> TabResets.done(catalog.kit(), section.reset())));
    }

    @Override
    protected void renderCanvasOverlay(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean ready = !revealing() && !leaving() && shown() >= BEACON_READY;
        float strength = beacon.advance(ready, UiFrame.delta());
        if (!beacon.armed()) lit = null;
        if (strength <= 0.0f || lit == null || !this.renderables.contains(lit)) return;
        SearchBeacon.paint(graphics, lit.getX(), lit.getY(), lit.getWidth(), lit.getHeight(),
                UiMetrics.radius(lit.getHeight()), strength);
    }

    @Override
    protected boolean submit() {
        if (desk.submit(getFocused())) return true;
        return super.submit();
    }

    @Override
    protected void deleteSelected() {
        if (presetsShown() && !searching() && desk.deletable()) desk.deleteSelected();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!leaving() && !inputCaptured()) {
            for (KeyRow row : catalog.kit().bindingRows()) {
                if (inspector.placed().contains(row) && row.takeButton(button)) return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        for (KeyRow row : catalog.kit().bindingRows()) {
            if (inspector.placed().contains(row) && row.take(key, scan)) return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private void onSearch(String typed) {
        String next = MenuSearch.needle(typed);
        if (next.equals(needle)) return;
        needle = next;
        if (this.minecraft != null) layout();
    }

    private boolean searching() {
        return !needle.isEmpty();
    }

    private boolean presetsShown() {
        return PRESETS.equals(selected);
    }

    private static CustomTab tabOf(String key) {
        for (CustomTab tab : CustomTab.values()) {
            if (tab.nodeKey().equals(key)) return tab;
        }
        return null;
    }

    private PreviewFocus focus() {
        CustomTab tab = tabOf(selected);
        if (tab != null) return switch (tab) {
            case GLASS -> PreviewFocus.GLASS;
            case COLORS -> PreviewFocus.TEXT;
            case INTERFACE -> PreviewFocus.GENERAL;
            case PRESETS -> PreviewFocus.PRESETS;
        };
        CustomSection section = catalog.section(selected);
        return section == null ? PreviewFocus.GLASS : section.focus();
    }

    private Component caption() {
        CustomTab tab = tabOf(selected);
        if (tab != null) return tab.title();
        CustomSection section = catalog.section(selected);
        return section == null ? Component.empty() : section.title();
    }

    @Override
    public void openPalette(PaletteKey key) {
        palette(key.id(), PaletteWindow.Kind.LOCAL, Component.translatable(key.translationKey()),
                Customization.color(key), argb -> {
                    Customization.color(key, argb);
                    Customization.save();
                }, () -> clearColor(key));
    }

    private void clearColor(PaletteKey key) {
        Customization.clearColor(key);
        Customization.save();
        refresh();
    }

    @Override
    public void refresh() {
        if (this.minecraft == null) return;
        catalog.holdLocks();
        layout();
    }

    // WHY: выбор картинки сразу ставит её в остров: игрок, открывший диалог, хочет видеть её, а не
    // WHY: искать второй переключатель
    @Override
    public void pickAvatarPicture() {
        AvatarPicture.pick(() -> {
            Customization.avatar(AvatarSource.PICTURE);
            Customization.save();
            MenuFeedback.show(Component.translatable("battlecraft.custom.island.avatar_picture.done"), false);
        }, reason -> MenuFeedback.show(reason, true));
    }

    @Override
    public boolean sure(String key) {
        return confirm(key);
    }

    @Override
    public boolean asking(String key) {
        return armed(key);
    }

    @Override
    public void focus(AbstractWidget widget) {
        setFocused(widget);
        if (inspector.center(widget)) layout();
    }

    private static final class Inert implements CustomHost {
        private static final Inert HOST = new Inert();

        @Override
        public void refresh() {
        }

        @Override
        public void openPalette(PaletteKey key) {
        }

        @Override
        public void pickAvatarPicture() {
        }

        @Override
        public boolean sure(String key) {
            return false;
        }

        @Override
        public boolean asking(String key) {
            return false;
        }

        @Override
        public void focus(AbstractWidget widget) {
        }
    }
}
