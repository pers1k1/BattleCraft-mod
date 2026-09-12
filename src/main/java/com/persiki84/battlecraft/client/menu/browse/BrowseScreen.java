package com.persiki84.battlecraft.client.menu.browse;

import com.persiki84.battlecraft.client.menu.MenuBackground;
import com.persiki84.shared.client.menu.GlassScreen;
import com.persiki84.shared.client.menu.GlidingRow;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.ScrollHint;
import com.persiki84.shared.client.menu.ScrollLanes;
import com.persiki84.shared.client.ui.Ambient;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAmbience;
import com.persiki84.shared.client.ui.UiBackdrop;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiFarewell;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTitle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public abstract class BrowseScreen extends GlassScreen implements Ambient {
    protected static final int LIST_WIDTH = 250;
    protected static final int PREVIEW_WIDTH = 200;
    protected static final int PANEL_GAP = 10;
    protected static final int PANEL_PAD = 9;
    protected static final int CARD_HEIGHT = 34;
    protected static final int CARD_GAP = 4;
    protected static final int ACTION_HEIGHT = 18;
    protected static final int ACTION_GAP = 4;
    protected static final float PANEL_RADIUS = 9.0f;

    private static final int HEADER_HEIGHT = 58;
    private static final int SEARCH_HEIGHT = 18;
    private static final int SEARCH_LIMIT = 64;
    private static final int FOOTER_BLOCK = 30;
    private static final int PANEL_HEIGHT_MIN = 140;
    private static final int PANEL_HEIGHT_LIMIT = 340;
    private static final float FIT_MARGIN = 12.0f;
    private static final float TITLE_TOP = 12.0f;
    private static final float TITLE_SCALE = 1.0f;
    private static final float TITLE_TRACKING = 2.4f;
    private static final float HINT_SCALE = 0.75f;
    private static final float GLIDE_SPEED = 11.0f;
    private static final float GLIDE_MAX_CARDS = 3.0f;
    private static final float ENTER_SECONDS = 0.85f;

    protected final List<BrowseCard> cards = new ArrayList<>();

    private final List<BrowseCard> placed = new ArrayList<>();
    private final Smooth glide = new Smooth(0.0f, GLIDE_SPEED);
    private final ScrollLanes lanes = new ScrollLanes();
    private final ScrollHint above = new ScrollHint();
    private final ScrollHint below = new ScrollHint();
    private final Screen parent;
    private EditBox search;
    private BrowseCard picked;
    private int scroll;
    private int capacity = 1;

    protected BrowseScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    protected abstract void fill(String filter);

    protected abstract void paintPreview(GuiGraphics graphics, int left, int top, int width, int height);

    protected abstract void buildActions(int left, int top, int width);

    protected abstract void buildFooter(int centerX, int y);

    protected abstract Component emptyHint();

    protected abstract Component searchHint();

    public abstract void enter();

    @Override
    public UiAmbience.Mood ambience() {
        return UiAmbience.Mood.FOCUS;
    }

    public BrowseCard picked() {
        return picked;
    }

    public void pick(BrowseCard card) {
        picked = card;
        rebuild();
    }

    protected void forget() {
        picked = null;
    }

    @Override
    protected void init() {
        if (search == null) search = createSearch();
        refill();
        setInitialFocus(search);
    }

    private EditBox createSearch() {
        EditBox box = new EditBox(this.font, 0, 0, LIST_WIDTH, SEARCH_HEIGHT, searchHint());
        box.setBordered(false);
        box.setMaxLength(SEARCH_LIMIT);
        box.setHint(searchHint());
        box.setTextColor(UiAccent.text());
        box.setResponder(text -> refill());
        return box;
    }

    protected void refill() {
        String kept = picked == null ? null : picked.identity();
        for (BrowseCard card : cards) card.close();
        cards.clear();
        fill(search == null ? "" : search.getValue());
        picked = kept == null ? null : matching(kept);
        rebuild();
    }

    private BrowseCard matching(String identity) {
        for (BrowseCard card : cards) {
            if (identity.equals(card.identity())) return card;
        }
        return null;
    }

    protected void rebuild() {
        boolean typing = search != null && search.isFocused();
        clearWidgets();
        placeSearch();
        placeCards();
        buildActions(previewLeft() + PANEL_PAD, actionsTop(), PREVIEW_WIDTH - PANEL_PAD * 2);
        buildFooter(Math.round(centerX()), footerTop());
        if (typing) setFocused(search);
    }

    private void placeSearch() {
        search.setX(contentLeft() + PANEL_PAD);
        search.setY(searchTop());
        search.setWidth(LIST_WIDTH - PANEL_PAD * 2);
        addRenderableWidget(search);
    }

    // WHY: карточки живут вне списка renderables: колонка режется ножницами, а поле поиска
    // WHY: и кнопки действий стоят за её краем и обрезались бы вместе с ней
    private void placeCards() {
        placed.clear();
        capacity = capacity(panelHeight(), cards.size());
        scroll = Math.max(0, Math.min(scroll, Math.max(0, cards.size() - capacity)));

        int lead = lead(scroll);
        int y = cardsTop() - lead * (CARD_HEIGHT + CARD_GAP);
        int index = -lead;
        for (BrowseCard card : around(cards, scroll, capacity)) {
            card.setX(contentLeft() + PANEL_PAD);
            card.setWidth(LIST_WIDTH - PANEL_PAD * 2);
            card.anchor(y, GlidingRow.Lane.ROWS);
            card.stagger(Math.max(0, index));
            addWidget(card);
            placed.add(card);
            y += CARD_HEIGHT + CARD_GAP;
            index++;
        }
    }

    private int capacity(int listHeight, int total) {
        int plain = fits(listHeight - PANEL_PAD * 2);
        if (total <= plain) return plain;

        return fits(listHeight - PANEL_PAD * 2 - ScrollHint.BAND_TOP - ScrollHint.BAND_BOTTOM);
    }

    private static int fits(int room) {
        return Math.max(1, (room + CARD_GAP) / (CARD_HEIGHT + CARD_GAP));
    }

    protected boolean scrollable() {
        return cards.size() > capacity;
    }

    protected int contentWidth() {
        return LIST_WIDTH + PREVIEW_WIDTH + PANEL_GAP;
    }

    protected int contentLeft() {
        return (this.width - contentWidth()) / 2;
    }

    protected int previewLeft() {
        return contentLeft() + LIST_WIDTH + PANEL_GAP;
    }

    protected float centerX() {
        return contentLeft() + contentWidth() / 2.0f;
    }

    protected int panelHeight() {
        int room = this.height - HEADER_HEIGHT - FOOTER_BLOCK - PANEL_GAP;
        return Math.max(PANEL_HEIGHT_MIN, Math.min(PANEL_HEIGHT_LIMIT, room));
    }

    protected int contentTop() {
        return Math.max(HEADER_HEIGHT, (this.height - panelHeight()) / 2);
    }

    private int searchTop() {
        return contentTop() - SEARCH_HEIGHT - PANEL_GAP;
    }

    private int cardsTop() {
        return contentTop() + PANEL_PAD + (scrollable() ? ScrollHint.BAND_TOP : 0);
    }

    protected int actionsTop() {
        return contentTop() + panelHeight() - PANEL_PAD - actionsHeight();
    }

    protected int actionsHeight() {
        return ACTION_HEIGHT * 2 + ACTION_GAP;
    }

    private int footerTop() {
        return contentTop() + panelHeight() + PANEL_GAP;
    }

    @Override
    protected float contentScale() {
        float needed = contentWidth() + FIT_MARGIN * 2.0f;
        return needed <= this.width ? 1.0f : this.width / needed;
    }

    @Override
    protected float revealTop() {
        return searchTop();
    }

    @Override
    protected float revealSpan() {
        return footerTop() + ACTION_HEIGHT - searchTop();
    }

    @Override
    protected float enterSeconds() {
        return ENTER_SECONDS;
    }

    @Override
    protected void renderBackdrop(GuiGraphics graphics) {
        MenuBackground.shared().render(graphics, this.width, this.height);
        graphics.flush();
        UiBackdrop.restage();
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int top = contentTop();
        int height = panelHeight();

        UiTitle.render(graphics, this.font, getTitle(), centerX(), TITLE_TOP,
                TITLE_SCALE, TITLE_TRACKING, UiAccent.text());
        UiGlass.sunken(graphics, contentLeft(), searchTop(), LIST_WIDTH, SEARCH_HEIGHT,
                UiMetrics.radius(SEARCH_HEIGHT), 1.0f);
        UiGlass.window(graphics, contentLeft(), top, LIST_WIDTH, height, PANEL_RADIUS, 1.0f);
        UiGlass.window(graphics, previewLeft(), top, PREVIEW_WIDTH, height, PANEL_RADIUS, 1.0f);
        UiGlass.layer(graphics);

        paintPreview(graphics, previewLeft() + PANEL_PAD, top + PANEL_PAD,
                PREVIEW_WIDTH - PANEL_PAD * 2, actionsTop() - top - PANEL_PAD * 2);
        paintList(graphics, mouseX, mouseY, partialTick, top, height);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        MenuFeedback.render(graphics, centerX(), footerTop() + ACTION_HEIGHT + PANEL_GAP);
    }

    private void paintList(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                           int top, int height) {
        if (cards.isEmpty()) {
            UiRender.textCentered(graphics, this.font, emptyHint(),
                    contentLeft() + LIST_WIDTH / 2.0f, top + height / 2.0f - 4.0f,
                    HINT_SCALE, UiAccent.textFaint(), false);
        }
        UiRender.clip(graphics, contentLeft(), top, LIST_WIDTH, height);
        try {
            glideCards();
            for (BrowseCard card : placed) card.render(graphics, mouseX, mouseY, partialTick);
            graphics.flush();
        } finally {
            graphics.disableScissor();
        }
        hints(graphics, top, height);
    }

    private void hints(GuiGraphics graphics, int top, int height) {
        float centerX = contentLeft() + LIST_WIDTH / 2.0f;
        above.render(graphics, centerX, top + (PANEL_PAD + ScrollHint.BAND_TOP) / 2.0f, false,
                scrollable() && scroll > 0);
        below.render(graphics, centerX, top + height - (PANEL_PAD + ScrollHint.BAND_BOTTOM) / 2.0f,
                true, scrollable() && scroll < cards.size() - capacity);
    }

    private void glideCards() {
        int top = contentTop();
        lanes.rows(glide.to(0.0f, UiFrame.delta()), top + PANEL_PAD, top + panelHeight() - PANEL_PAD);
        for (BrowseCard card : placed) card.glide(lanes);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (leaving()) return true;

        int step = amount > 0 ? -1 : 1;
        int next = Math.max(0, Math.min(scroll + step, Math.max(0, cards.size() - capacity)));
        if (next == scroll) return true;

        glide.snap(capped(glide.get() + (next - scroll) * (CARD_HEIGHT + CARD_GAP)));
        scroll = next;
        rebuild();
        return true;
    }

    private static float capped(float offset) {
        float limit = (CARD_HEIGHT + CARD_GAP) * GLIDE_MAX_CARDS;
        return Math.max(-limit, Math.min(limit, offset));
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            enter();
            return true;
        }
        if (super.keyPressed(key, scan, modifiers)) return true;
        return search != null && search.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean charTyped(char symbol, int modifiers) {
        return search != null && search.charTyped(symbol, modifiers);
    }

    protected UiButton action(int x, int y, int width, Component label, Runnable run) {
        return new UiButton(x, y, width, ACTION_HEIGHT, label, pressed -> run.run());
    }

    protected void leave() {
        dismiss();
    }

    @Override
    protected void closing() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void removed() {
        if (!UiFarewell.defer(this, this::dispose)) dispose();
        super.removed();
    }

    private void dispose() {
        for (BrowseCard card : cards) card.close();
        cards.clear();
        placed.clear();
    }
}
