package com.persiki84.shared.client.menu.gunsmith;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.gunsmith.GunSlot;
import com.persiki84.shared.gunsmith.GunSmith;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class GunSlotBoard {
    private static final float CARD = 24.0f;
    private static final float CARD_GAP = 3.0f;
    private static final float BASE_TILE = 44.0f;
    private static final float BASE_GAP = 5.0f;
    private static final float ICON_TOP = 6.0f;
    private static final float ICON_HALF = 8.0f;
    private static final float LABEL_RISE = 16.0f;
    private static final float LABEL_HEIGHT = 12.0f;
    private static final float LABEL_INSET = 3.0f;
    private static final float APPEAR_LIFT = 6.0f;
    private static final float HOVER_RISE = 1.0f;
    private static final float SECTION_GAP = 8.0f;
    private static final float RADIUS = 6.0f;
    private static final float HOVER_SPEED = 16.0f;
    private static final float CHOICE_SPEED = 13.0f;
    private static final float OPEN_SPEED = 12.0f;
    private static final float NAME_SCALE = 0.55f;
    private static final float LEVEL_SCALE = 1.1f;
    private static final float CARD_SCALE = 0.72f;
    private static final float GLIDE_SPEED = 15.0f;

    private final List<Smooth> cardHover = new ArrayList<>();
    private final List<Smooth> tileHover = new ArrayList<>();
    private final List<Smooth> tileLit = new ArrayList<>();
    private final Smooth opened = new Smooth(0.0f, OPEN_SPEED);
    private final Smooth chosenGlow = new Smooth(0.0f, CHOICE_SPEED);
    private final Smooth glide = new Smooth(0.0f, GLIDE_SPEED);
    private float left;
    private float top;
    private float width;
    private float height;
    private float tileSize = BASE_TILE;
    private float gapSize = BASE_GAP;
    private float unit = 1.0f;
    private int columns = 1;
    private int chosen = -1;
    private int scroll;

    public void place(float boardLeft, float boardTop, float boardWidth, float boardHeight) {
        left = boardLeft;
        top = boardTop;
        width = boardWidth;
        height = boardHeight;
        fit();
    }

    // WHY: ряд плиток занимает всю ширину доски: число столбцов берётся от заводской плитки, а сама
    // WHY: плитка растягивается на остаток, иначе справа висела полоса пустоты. Нутро растёт той же долей
    private void fit() {
        columns = Math.max(1, (int) ((width + BASE_GAP) / (BASE_TILE + BASE_GAP)));
        float share = columns + (columns - 1) * BASE_GAP / BASE_TILE;
        tileSize = width <= 0.0f ? BASE_TILE : UiMetrics.snap(width / share);
        gapSize = columns > 1 ? Math.max(0.0f, (width - tileSize * columns) / (columns - 1)) : 0.0f;
        unit = tileSize / BASE_TILE;
    }

    private float step() {
        return tileSize + gapSize;
    }

    public int chosen() {
        return chosen;
    }

    public void choose(int slot) {
        chosen = slot;
        scroll = 0;
        glide.snap(0.0f);
        opened.snap(0.0f);
        chosenGlow.snap(0.0f);
    }

    public boolean over(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    public void scroll(int step) {
        scroll = Math.max(0, scroll + step);
    }

    public int slotAt(double mouseX, double mouseY, int count) {
        for (int index = 0; index < count; index++) {
            float y = cardY(index);
            if (mouseX >= left && mouseX < left + width && mouseY >= y && mouseY < y + CARD) return index;
        }
        return -1;
    }

    private float cardY(int index) {
        return top + index * (CARD + CARD_GAP);
    }

    private float tilesTop(int slots) {
        return cardY(slots) + SECTION_GAP;
    }

    private float tileX(int place) {
        return left + (place % columns) * step();
    }

    private float tileY(int place, int slots) {
        return tilesTop(slots) + (place / columns - glide.get()) * step();
    }

    public void render(GuiGraphics graphics, List<GunSlot> slots, Function<GunSlot, String> installed,
                       int mouseX, int mouseY) {
        grow(cardHover, slots.size());
        if (chosen >= slots.size()) chosen = -1;
        for (int index = 0; index < slots.size(); index++) {
            paintCard(graphics, slots.get(index), installed.apply(slots.get(index)), index, mouseX, mouseY);
        }
        if (slots.isEmpty()) {
            UiRender.textCentered(graphics, font(), Component.translatable("gunsmith.no_slots"), left + width / 2.0f,
                    top + height / 2.0f, 0.75f, UiAccent.textDim(), false);
            return;
        }
        if (chosen < 0) {
            UiRender.textCentered(graphics, font(), Component.translatable("gunsmith.pick_slot"), left + width / 2.0f,
                    tilesTop(slots.size()) + 10.0f, 0.7f, UiAccent.textDim(), false);
            return;
        }
        paintOptions(graphics, slots.get(chosen), installed.apply(slots.get(chosen)), slots.size(), mouseX, mouseY);
    }

    private void paintCard(GuiGraphics graphics, GunSlot slot, String current, int index, int mouseX, int mouseY) {
        float y = cardY(index);
        boolean hovered = mouseX >= left && mouseX < left + width && mouseY >= y && mouseY < y + CARD;
        float focus = cardHover.get(index).to(hovered ? 1.0f : 0.0f, UiFrame.delta());
        float taken = index == chosen ? chosenGlow.to(1.0f, UiFrame.delta()) : 0.0f;
        UiGlass.panel(graphics, left, y, width, CARD, RADIUS, 1.0f, 0.1f + focus * 0.4f + taken * 0.3f);
        if (taken > 0.01f) {
            UiRender.rim(graphics, left, y, width, CARD, RADIUS, 1.2f, UiTheme.alpha(UiAccent.color(), 0.75f * taken));
        }
        UiRender.textScaled(graphics, font(), Component.translatable(slot.label()), left + UiMetrics.PAD,
                y + (CARD - 8.0f * CARD_SCALE) / 2.0f, CARD_SCALE, UiAccent.text(), false);
        ItemStack icon = GunSmith.preview(current);
        float right = left + width - UiMetrics.PAD;
        if (!icon.isEmpty()) {
            graphics.renderItem(icon, (int) (right - 16.0f), (int) (y + (CARD - 16.0f) / 2.0f));
            right -= 20.0f;
        }
        Component value = current.isEmpty() ? Component.translatable("gunsmith.empty") : optionName(slot, current);
        UiRender.textRight(graphics, font(), value, right, y + (CARD - 8.0f * CARD_SCALE) / 2.0f, CARD_SCALE,
                current.isEmpty() ? UiAccent.textFaint() : UiAccent.color(), false);
    }

    private static Component optionName(GunSlot slot, String option) {
        for (GunSlot.GunOption candidate : slot.options()) {
            if (candidate.value().equals(option)) return Component.literal(candidate.label());
        }
        return GunSmith.optionName(option);
    }

    private void paintOptions(GuiGraphics graphics, GunSlot slot, String current, int slots, int mouseX, int mouseY) {
        int count = slot.options().size() + 1;
        grow(tileHover, count);
        grow(tileLit, count);
        clampScroll(count, slots);
        glide.to(scroll, UiFrame.delta());
        float appear = UiAnim.easeOut(opened.to(1.0f, UiFrame.delta()));
        float bandTop = tilesTop(slots);
        UiRender.clip(graphics, left - 2.0f, bandTop - 2.0f, width + 4.0f, top + height - bandTop + 2.0f);
        try {
            for (int place = 0; place < count; place++) {
                String value = place == 0 ? "" : slot.options().get(place - 1).value();
                String label = place == 0 ? "" : slot.options().get(place - 1).label();
                paintTile(graphics, value, label, value.equals(current), place, slots, appear, mouseX, mouseY);
            }
            graphics.flush();
        } finally {
            graphics.disableScissor();
        }
    }

    private void clampScroll(int count, int slots) {
        int rows = (count + columns - 1) / columns;
        int visible = Math.max(1, (int) ((top + height - tilesTop(slots) + gapSize) / step()));
        scroll = Math.max(0, Math.min(scroll, rows - visible));
    }

    // WHY: плитка рисуется на своём месте покоя, а прокрутка, вход и подъём под курсором заявлены ходом
    // WHY: от него: подпись варианта стоит на пиксельной сетке покоя и не идёт ступенями по пикселю
    private void paintTile(GuiGraphics graphics, String value, String label, boolean installed, int place, int slots,
                           float appear, int mouseX, int mouseY) {
        float x = tileX(place);
        float restY = restY(place, slots);
        float y = tileY(place, slots) + (1.0f - appear) * APPEAR_LIFT * unit;
        if (y + tileSize < tilesTop(slots) - gapSize || y > top + height) return;
        boolean hovered = mouseX >= x && mouseX < x + tileSize && mouseY >= y && mouseY < y + tileSize
                && mouseY >= tilesTop(slots);
        float delta = UiFrame.delta();
        float focus = tileHover.get(place).to(hovered ? 1.0f : 0.0f, delta);
        float lit = tileLit.get(place).to(installed ? 1.0f : 0.0f, delta);
        UiRestFrame.shift(graphics, 0.0f, y - restY - focus * wholeRise(graphics));
        try {
            float radius = RADIUS * unit;
            UiGlass.panel(graphics, x, restY, tileSize, tileSize, radius, appear, 0.15f + focus * 0.45f + lit * 0.3f);
            if (lit > 0.01f) {
                UiRender.rim(graphics, x, restY, tileSize, tileSize, radius, 1.3f,
                        UiTheme.alpha(UiAccent.color(), 0.85f * lit));
            }
            paintTileBody(graphics, value, label, x, restY, lit);
        } finally {
            UiRestFrame.pop(graphics);
        }
    }

    private float restY(int place, int slots) {
        return tilesTop(slots) + (place / columns - scroll) * step();
    }

    // WHY: подъём держится, пока плитка наведена, поэтому он встаёт на целый пиксель: на дробном
    // WHY: подъёме подпись всё время наведения стояла бы на полупикселе и читалась мягче соседних
    private float wholeRise(GuiGraphics graphics) {
        float rise = HOVER_RISE * unit;
        float pixels = UiRender.pixels(graphics) / UiRestFrame.stretchX();
        return pixels <= 0.01f ? rise : Math.round(rise * pixels) / pixels;
    }

    // WHY: значок ставится матрицей, а не целыми координатами renderItem: на плавной прокрутке
    // WHY: целые координаты вели его ступенями, и он отставал от своей плитки
    private void paintTileBody(GuiGraphics graphics, String value, String label, float x, float y, float lit) {
        int ink = UiTheme.mix(UiAccent.text(), UiAccent.color(), lit);
        float centerX = x + tileSize / 2.0f;
        float textWidth = tileSize - LABEL_INSET * 2.0f * unit;
        if (value.isEmpty()) {
            UiRender.textTrackedFit(graphics, font(), Component.translatable("gunsmith.empty"), centerX, y, tileSize,
                    textWidth, NAME_SCALE * unit, 0.0f, ink, false);
            return;
        }
        ItemStack icon = GunSmith.preview(value);
        if (icon.isEmpty()) {
            UiRender.textTrackedFit(graphics, font(), Component.literal(label), centerX, y, tileSize,
                    textWidth, LEVEL_SCALE * 0.6f * unit, 0.0f, ink, false);
            return;
        }
        paintIcon(graphics, icon, centerX - ICON_HALF * unit, y + ICON_TOP * unit);
        UiRender.textTrackedFit(graphics, font(), Component.literal(label), centerX, y + tileSize - LABEL_RISE * unit,
                LABEL_HEIGHT * unit, textWidth, NAME_SCALE * unit, 0.0f, ink, false);
    }

    private void paintIcon(GuiGraphics graphics, ItemStack icon, float x, float y) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(unit, unit, 1.0f);
        try {
            graphics.renderItem(icon, 0, 0);
        } finally {
            graphics.pose().popPose();
        }
    }

    // WHY: под курсором ищется номер варианта, а не запись: опрос идёт каждый кадр ради подсказки
    public String hoveredOption(double mouseX, double mouseY, List<GunSlot> slots) {
        if (chosen < 0 || chosen >= slots.size() || mouseY < tilesTop(slots.size())) return null;

        GunSlot slot = slots.get(chosen);
        int count = slot.options().size() + 1;
        for (int place = 0; place < count; place++) {
            float x = tileX(place);
            float y = tileY(place, slots.size());
            if (mouseX < x || mouseX >= x + tileSize || mouseY < y || mouseY >= y + tileSize) continue;
            return place == 0 ? "" : slot.options().get(place - 1).value();
        }
        return null;
    }

    private static void grow(List<Smooth> list, int size) {
        while (list.size() < size) {
            list.add(new Smooth(0.0f, HOVER_SPEED));
        }
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}
