package com.persiki84.shared.client.menu;

import com.persiki84.shared.client.menu.studio.StudioNav;
import com.persiki84.shared.client.menu.studio.StudioScreen;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

// WHY: панель настроек без видимой вещи стоит в той же студии, что и редакторы: страницы узлами
// WHY: слева, строки в центре, справа что это за страница и её рычаги. Так все экраны /bc читаются
// WHY: одинаково, а строки, пересобранные по снимку, переносят свои анимации через GlassScreen
public abstract class PanelScreen extends StudioScreen {
    protected static final int ROW_HEIGHT = 22;
    protected static final String HINT_SUFFIX = MenuHint.SUFFIX;

    private static final String PAGE = "page:";
    private static final int ART = 96;
    private static final int ABOUT_LINES = 7;
    private static final float LINE_SCALE = 0.72f;

    private int tab;

    protected PanelScreen(Component title) {
        super(title, null);
    }

    protected abstract String menuId();

    protected abstract List<Page> pages();

    public void openOn(int index) {
        tab = Math.max(0, index);
    }

    protected void buildSide(StudioStack stack, int x, int width) {
    }

    protected List<UiButton> panelButtons() {
        return List.of();
    }

    protected Component summary() {
        Page current = current();
        return current == null ? Component.empty() : current.title();
    }

    private Page current() {
        List<Page> pages = pages();
        if (pages.isEmpty()) return null;
        tab = Math.max(0, Math.min(tab, pages.size() - 1));
        return pages.get(tab);
    }

    @Override
    protected int carryScope() {
        return tab;
    }

    @Override
    protected Object stamp() {
        return MenuData.state(menuId());
    }

    @Override
    protected List<StudioNav.Node> nodes() {
        List<StudioNav.Node> nodes = new ArrayList<>();
        List<Page> pages = pages();
        for (int index = 0; index < pages.size(); index++) {
            nodes.add(StudioNav.Node.item(PAGE + index, pages.get(index).title(), Component.empty(), 0, false));
        }
        return nodes;
    }

    @Override
    protected String selectedNode() {
        return PAGE + tab;
    }

    @Override
    protected void selectNode(StudioNav.Node node) {
        tab = Integer.parseInt(node.key().substring(PAGE.length()));
    }

    @Override
    protected boolean gridCanvas() {
        return false;
    }

    @Override
    protected Component stats() {
        if (MenuData.state(menuId()).isEmpty()) return Component.translatable("battlecraft.menu.waiting");
        return summary();
    }

    @Override
    protected List<UiButton> headerButtons() {
        return panelButtons();
    }

    @Override
    protected Object inspectorSubject() {
        return PAGE + tab;
    }

    @Override
    protected void buildInspector(StudioStack stack, int x, int width) {
        buildSide(stack, x, width);
    }

    @Override
    protected void buildCanvas(StudioStack stack, int x, int width) {
        Page current = current();
        if (current == null) return;
        boolean first = true;
        for (AbstractWidget row : current.rows().get()) {
            row.setWidth(width);
            stack.add(row, x, row instanceof HeadingRow && !first ? ROW_HEIGHT / 3 : 0);
            first = false;
        }
    }

    // WHY: справа о странице говорится словами: что она меняет и когда это действует. Ключ
    // WHY: «<ключ вкладки>.about» есть не у каждой страницы, и без него колонка остаётся под рычаги
    private Component about() {
        Page current = current();
        if (current == null || !(current.title().getContents() instanceof TranslatableContents contents)) return null;
        String key = contents.getKey() + ".about";
        return I18n.exists(key) ? Component.translatable(key) : null;
    }

    @Override
    protected int artHeight() {
        return about() == null ? 0 : ART;
    }

    @Override
    protected void renderArt(GuiGraphics graphics, float left, float top, float width, float appear,
                             int mouseX, int mouseY) {
        Component about = about();
        Page current = current();
        if (about == null || current == null) return;
        UiRender.textTrackedFit(graphics, this.font, current.title(), left + width / 2.0f, top, 14.0f, width, 0.95f,
                0.0f, UiTheme.alpha(UiAccent.text(), appear), false);
        List<FormattedCharSequence> lines = UiRender.split(graphics, this.font, about, LINE_SCALE,
                (int) (width / LINE_SCALE));
        float y = top + 18.0f;
        for (int index = 0; index < Math.min(ABOUT_LINES, lines.size()); index++) {
            UiRender.textCentered(graphics, this.font, Component.literal(UiRender.flatten(lines.get(index))),
                    left + width / 2.0f, y, LINE_SCALE, UiTheme.alpha(UiAccent.textDim(), appear), false);
            y += this.font.lineHeight * LINE_SCALE + 2.0f;
        }
    }

    @Override
    protected Component emptyCanvas() {
        return Component.translatable("battlecraft.menu.waiting");
    }

    @Override
    public void tick() {
        if (ticks % 10 == 0) MenuData.request(menuId());
        super.tick();
    }

    protected void rebuild() {
        layout();
    }

    protected int rowsLeft() {
        return canvasRowsLeft();
    }

    protected int rowsWidth() {
        return canvasRowsWidth();
    }

    protected NumberRow number(String label, IntSupplier value, IntConsumer apply,
                               int minimum, int maximum, int step) {
        NumberRow row = new NumberRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.translatable(label),
                value, apply, minimum, maximum, step);
        row.hint(label + HINT_SUFFIX);
        return row;
    }

    protected ToggleRow toggle(String label, BooleanSupplier value, Consumer<Boolean> apply) {
        ToggleRow row = new ToggleRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable(label), value, apply);
        row.hint(label + HINT_SUFFIX);
        return row;
    }

    protected PickRow pick(String label, List<Component> options, IntSupplier index, IntConsumer apply) {
        PickRow row = new PickRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, Component.translatable(label),
                options, index, apply);
        row.hint(label + HINT_SUFFIX);
        return row;
    }

    protected ActionRow action(Component label, Component value, Runnable run) {
        return new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, label, () -> value, run);
    }

    protected ActionRow action(String label, String value, Runnable run) {
        ActionRow row = new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable(label), () -> Component.translatable(value), run);
        row.hint(label + HINT_SUFFIX);
        return row;
    }

    protected ActionRow reading(String label, Supplier<Component> value) {
        ActionRow row = new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable(label), value, () -> { });
        row.hint(label + HINT_SUFFIX);
        row.active = false;
        return row;
    }

    protected HeadingRow heading(Component label) {
        return new HeadingRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT, label);
    }

    public record Page(Component title, Supplier<List<AbstractWidget>> rows) {}
}
