package com.persiki84.battlecraft.client.menu.panel.loot;

import com.persiki84.airdrop.cache.CacheTier;
import com.persiki84.airdrop.cache.LootCache;
import com.persiki84.airdrop.cache.RefillMode;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.MenuRow;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.PickRow;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.zones.client.menu.ItemTurntable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

final class CacheInspector {
    private static final int ROW = 22;
    private static final int CONTROL = 16;
    private static final int GAP = 4;
    private static final int CACHE_ART = 110;
    private static final int NODE_ART = 44;
    private static final float TURNTABLE_HEIGHT = 70.0f;
    private static final float LINE_SCALE = 0.72f;
    private static final String DROP = "drop-cache";
    private static final CacheTier[] TIERS = CacheTier.values();
    private static final RefillMode[] MODES = RefillMode.values();
    private static final Component SURE = Component.translatable("studio.sure");
    private static final Component REMOVE = Component.translatable("airdrop.menu.action.remove");

    private final AirDropStudioScreen screen;
    private final ItemTurntable turntable = new ItemTurntable();
    private final HeadingRow nodeHeading = heading("airdrop.menu.group.caches");
    private final HeadingRow cacheHeading = heading("airdrop.menu.group.cache");
    private final ToggleRow clearRow;
    private final ToggleRow lockRow;
    private final ActionRow openRow;
    private final PickRow tierRow;
    private final PickRow refillRow;
    private final NumberRow secondsRow;
    private final UiButton fillButton;
    private final UiButton emptyButton;
    private final UiButton tpButton;
    private final ActionRow removeRow;
    private List<String> pickedNames = List.of();
    private PickRow tableRow;
    private int countBeforeAdd = -1;

    CacheInspector(AirDropStudioScreen screen) {
        this.screen = screen;
        clearRow = flag("airdrop.menu.cache_clear", "cacheClear", "cache_clear");
        lockRow = flag("airdrop.menu.cache_lock", "cacheLock", "cache_lock");
        openRow = hinted(new ActionRow(0, 0, 10, ROW, Component.translatable("airdrop.menu.cache_edit_table"),
                () -> Component.translatable("airdrop.menu.action.open"), this::openTable), "airdrop.menu.cache_edit_table");
        tierRow = hinted(new PickRow(0, 0, 10, ROW, Component.translatable("airdrop.menu.cache_tier"), tierLabels(),
                () -> tier().ordinal(), picked -> run("tier " + TIERS[picked].id())), "airdrop.menu.cache_tier");
        refillRow = hinted(new PickRow(0, 0, 10, ROW, Component.translatable("airdrop.menu.cache_refill"), modeLabels(),
                () -> mode().ordinal(), picked -> run("refill " + MODES[picked].id())), "airdrop.menu.cache_refill");
        secondsRow = hinted(new NumberRow(0, 0, 10, ROW, Component.translatable("airdrop.menu.cache_refill_seconds"),
                this::seconds, set -> later("seconds", "refill " + mode().id() + " " + set),
                LootCache.REFILL_MIN, LootCache.REFILL_MAX, 30), "airdrop.menu.cache_refill_seconds");
        fillButton = button("airdrop.menu.action.fill", "fill");
        emptyButton = button("airdrop.menu.action.empty", "empty");
        tpButton = button("airdrop.menu.action.tp", "tp");
        removeRow = new ActionRow(0, 0, 10, ROW, Component.translatable("airdrop.menu.cache_remove"),
                () -> screen.isArmed(DROP) ? SURE : REMOVE, this::pressRemove).alerting();
        removeRow.hint("airdrop.menu.cache_remove.hint");
    }

    private static HeadingRow heading(String key) {
        return new HeadingRow(0, 0, 10, ROW, Component.translatable(key));
    }

    private static <T extends MenuRow> T hinted(T row, String key) {
        row.hint(key + ".hint");
        return row;
    }

    private ToggleRow flag(String label, String key, String command) {
        return hinted(new ToggleRow(0, 0, 10, ROW, Component.translatable(label),
                () -> AirDropStudioScreen.state().getBoolean(key),
                on -> screen.now("airdrop toggle " + command + " " + on)), label);
    }

    private UiButton button(String label, String verb) {
        return new UiButton(0, 0, 10, CONTROL, Component.translatable(label), pressed -> run(verb));
    }

    private static List<Component> tierLabels() {
        List<Component> labels = new ArrayList<>();
        for (CacheTier tier : TIERS) {
            labels.add(Component.translatable(tier.label()));
        }
        return labels;
    }

    private static List<Component> modeLabels() {
        List<Component> labels = new ArrayList<>();
        for (RefillMode mode : MODES) {
            labels.add(Component.translatable(mode.label()));
        }
        return labels;
    }

    private CacheTier tier() {
        CacheTile cache = screen.cache();
        return cache == null ? CacheTier.of("") : cache.tier();
    }

    private RefillMode mode() {
        CacheTile cache = screen.cache();
        return RefillMode.of(cache == null ? "" : cache.refill());
    }

    private int seconds() {
        CacheTile cache = screen.cache();
        return cache == null ? LootCache.REFILL_DEFAULT : cache.refillSeconds();
    }

    private String prefix() {
        CacheTile cache = screen.cache();
        return cache == null ? "" : "airdrop cache " + cache.id() + " ";
    }

    private void run(String tail) {
        String prefix = prefix();
        if (!prefix.isEmpty()) screen.now(prefix + tail);
    }

    private void later(String key, String tail) {
        String prefix = prefix();
        if (!prefix.isEmpty()) screen.later(key, prefix + tail);
    }

    private void openTable() {
        CacheTile cache = screen.cache();
        if (cache != null) screen.openTable(cache.table());
    }

    private void pressRemove() {
        if (!screen.confirmed(DROP)) return;
        run("remove");
        screen.selectCache(-1);
        screen.relayout();
    }

    void tick() {
    }

    void expectNew(int count) {
        countBeforeAdd = count;
    }

    // WHY: после добавления по прицелу выбор встаёт на новый тайник: иначе только что добавленное
    // WHY: приходится искать по координатам среди всех плиток
    int arrived(List<CacheTile> caches) {
        if (countBeforeAdd < 0 || caches.size() <= countBeforeAdd) return -1;
        countBeforeAdd = -1;
        return caches.get(caches.size() - 1).id();
    }

    private PickRow tableRow() {
        List<String> names = LootSnapshot.names();
        if (tableRow != null && names.equals(pickedNames)) return tableRow;
        pickedNames = names;
        List<Component> labels = new ArrayList<>();
        for (String name : names) {
            labels.add(Component.literal(name));
        }
        tableRow = hinted(new PickRow(0, 0, 10, ROW, Component.translatable("airdrop.menu.cache_table"), labels,
                () -> Math.max(0, pickedNames.indexOf(screen.cache() == null ? "" : screen.cache().table())),
                picked -> run("table " + pickedNames.get(picked))), "airdrop.menu.cache_table");
        return tableRow;
    }

    void build(StudioStack stack, int x, int width) {
        CacheTile cache = screen.cache();
        if (cache == null) {
            stack.add(sized(nodeHeading, width), x);
            stack.add(sized(clearRow, width), x);
            stack.add(sized(lockRow, width), x);
            return;
        }
        stack.add(sized(cacheHeading, width), x);
        if (!LootSnapshot.names().isEmpty()) stack.add(sized(tableRow(), width), x);
        stack.add(sized(openRow, width), x);
        stack.add(sized(tierRow, width), x);
        stack.add(sized(refillRow, width), x);
        if (mode().timed()) stack.add(sized(secondsRow, width), x);
        placeButtons(stack, x, width);
        stack.add(sized(removeRow, width), x, GAP);
    }

    private void placeButtons(StudioStack stack, int x, int width) {
        int third = (width - GAP * 2) / 3;
        stack.add(sized(fillButton, third), x, GAP * 2);
        stack.beside(sized(emptyButton, third), x + third + GAP);
        stack.beside(sized(tpButton, width - third * 2 - GAP * 2), x + (third + GAP) * 2);
    }

    private static <T extends AbstractWidget> T sized(T widget, int width) {
        widget.setWidth(width);
        return widget;
    }

    Component stats() {
        List<CacheTile> caches = screen.cacheTiles();
        int empty = 0;
        int missing = 0;
        for (CacheTile cache : caches) {
            if (cache.missing()) missing++;
            if (cache.empty()) empty++;
        }
        return Component.translatable("studio.cache.stats", caches.size(), empty, missing);
    }

    int artHeight() {
        return screen.cache() != null ? CACHE_ART : NODE_ART;
    }

    void renderArt(GuiGraphics graphics, float left, float top, float width, float appear) {
        CacheTile cache = screen.cache();
        if (cache == null) {
            UiRender.textTrackedFit(graphics, font(), stats(), left + width / 2.0f, top, 14.0f, width, 0.9f, 0.0f,
                    UiTheme.alpha(UiAccent.text(), appear), false);
            line(graphics, Component.translatable("studio.cache.art.how"), left, width, top + 16.0f, appear);
            line(graphics, Component.translatable("studio.cache.art.how2"), left, width, top + 27.0f, appear);
            return;
        }
        turntable.render(graphics, cache.icon(), left, top, width, TURNTABLE_HEIGHT);
        float y = top + TURNTABLE_HEIGHT + 4.0f;
        UiRender.textTrackedFit(graphics, font(), cache.name(), left + width / 2.0f, y, 12.0f, width, 0.85f, 0.0f,
                UiTheme.alpha(UiAccent.text(), appear), false);
        line(graphics, Component.translatable("studio.cache.art.place", cache.corner(), cache.dimension()),
                left, width, y + 15.0f, appear);
        line(graphics, Component.translatable("studio.cache.art.state", Component.translatable(cache.tier().label()),
                Component.translatable(mode().label())), left, width, y + 26.0f, appear);
    }

    private static void line(GuiGraphics graphics, Component text, float left, float width, float y, float appear) {
        UiRender.textTrackedFit(graphics, font(), text, left + width / 2.0f, y, 10.0f, width, LINE_SCALE, 0.0f,
                UiTheme.alpha(UiAccent.textDim(), appear), false);
    }

    boolean pressArt(double mouseX, double mouseY) {
        if (screen.cache() == null || !turntable.over(mouseX, mouseY)) return false;
        turntable.beginDrag();
        return true;
    }

    boolean dragArt(double dragX, double dragY) {
        if (!turntable.dragging()) return false;
        turntable.drag(dragX, dragY);
        return true;
    }

    void releaseArt() {
        turntable.endDrag();
    }

    boolean scrollArt(double mouseX, double mouseY, double amount) {
        if (screen.cache() == null || !turntable.over(mouseX, mouseY)) return false;
        turntable.magnify(amount);
        return true;
    }

    private static net.minecraft.client.gui.Font font() {
        return Minecraft.getInstance().font;
    }
}
