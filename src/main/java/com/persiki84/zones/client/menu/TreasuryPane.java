package com.persiki84.zones.client.menu;

import com.persiki84.capturepoints.client.ClientTreasury;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiMorphText;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

// WHY: казна показывается на месте витрины теми же стеклянными плитками: стопка, имя и число, которое
// WHY: перекатывается по цифрам, когда доход капает или кто-то из команды берёт. Взятая стопка
// WHY: улетает вниз к инвентарю и тает, её место пустеет сразу, а не после ответа сервера
final class TreasuryPane {
    private static final int TILE = ShopGrid.TILE_SIZE;
    private static final int GAP = ShopGrid.TILE_GAP;
    private static final float RADIUS = 7.0f;
    private static final float LEAD = 3.0f;
    private static final float ICON_SCALE = 1.5f;
    private static final float ICON_TOP = 9.0f;
    private static final float NAME_TOP = 36.0f;
    private static final float NAME_SCALE = 0.62f;
    private static final float PLATE_HEIGHT = 12.0f;
    private static final float PLATE_BOTTOM = 5.0f;
    private static final float PLATE_SCALE = 0.75f;
    private static final float HOVER_SPEED = 16.0f;
    private static final float CHOICE_SPEED = 13.0f;
    private static final float APPEAR_MS = 200.0f;
    private static final float STAGGER_MS = 16.0f;
    private static final float APPEAR_LIFT = 5.0f;
    private static final float LEAVE_MS = 340.0f;
    private static final float LEAVE_DROP = 70.0f;
    private static final float ART_SCALE = 3.0f;
    private static final float LINE_SCALE = 0.75f;

    private record Leaving(ItemStack stack, float x, float y, long at) {}

    private final List<Smooth> hover = new ArrayList<>();
    private final List<UiMorphText> counts = new ArrayList<>();
    private final Smooth choice = new Smooth(1.0f, CHOICE_SPEED);
    private float left;
    private float top;
    private float width;
    private float height;
    private int scroll;
    private int chosen = -1;
    private int vanished = -1;
    private int seenVersion = -1;
    private long shownAt = System.currentTimeMillis();
    private Leaving leaving;
    private ItemStack chosenSeen = ItemStack.EMPTY;

    void place(float paneLeft, float paneTop, float paneWidth, float paneHeight) {
        left = paneLeft;
        top = paneTop;
        width = paneWidth;
        height = paneHeight;
    }

    void reset() {
        scroll = 0;
        chosen = -1;
        shownAt = System.currentTimeMillis();
    }

    int chosen() {
        List<ItemStack> items = ClientTreasury.items();
        return chosen >= 0 && chosen < items.size() && chosen != vanished ? chosen : -1;
    }

    ItemStack chosenStack() {
        int index = chosen();
        return index < 0 ? ItemStack.EMPTY : ClientTreasury.items().get(index);
    }

    private int columns() {
        return Math.max(1, (int) ((width - UiMetrics.PAD * 2.0f + GAP) / (TILE + GAP)));
    }

    private int rows() {
        return Math.max(1, (int) ((height - LEAD + GAP) / (TILE + GAP)));
    }

    private float tileX(int slot) {
        return left + UiMetrics.PAD + slot % columns() * (TILE + GAP);
    }

    private float tileY(int slot) {
        return top + LEAD + (slot / columns() - scroll) * (TILE + GAP);
    }

    void scrollBy(int step) {
        int total = ClientTreasury.items().size();
        int lastRow = Math.max(0, (total + columns() - 1) / columns() - rows());
        scroll = Math.max(0, Math.min(lastRow, scroll + step));
    }

    int pick(double mouseX, double mouseY) {
        List<ItemStack> items = ClientTreasury.items();
        for (int index = 0; index < items.size(); index++) {
            float x = tileX(index);
            float y = tileY(index);
            boolean inside = mouseX >= x && mouseX < x + TILE && mouseY >= y && mouseY < y + TILE;
            if (inside && y >= top && y + TILE <= top + height && index != vanished) return index;
        }
        return -1;
    }

    void choose(int index) {
        if (index == chosen) return;
        chosen = index;
        List<ItemStack> items = ClientTreasury.items();
        chosenSeen = index >= 0 && index < items.size() ? items.get(index).copy() : ItemStack.EMPTY;
        choice.snap(0.0f);
    }

    void leave(int index) {
        List<ItemStack> items = ClientTreasury.items();
        if (index < 0 || index >= items.size()) return;
        leaving = new Leaving(items.get(index).copy(), tileX(index), tileY(index), System.currentTimeMillis());
        vanished = index;
        chosen = -1;
    }

    void render(GuiGraphics graphics, int mouseX, int mouseY) {
        settle();
        List<ItemStack> items = ClientTreasury.items();
        grow(items.size());
        if (items.isEmpty() && leaving == null) {
            UiRender.textCentered(graphics, font(), Component.translatable("zones.shop.treasury.empty"),
                    left + width / 2.0f, top + height / 2.0f, LINE_SCALE, UiAccent.textDim(), false);
            return;
        }
        choice.to(1.0f, UiFrame.delta());
        UiRender.clip(graphics, left, top, width, height);
        try {
            for (int index = 0; index < items.size(); index++) {
                if (index != vanished) paintSlot(graphics, items.get(index), index, mouseX, mouseY);
            }
            graphics.flush();
        } finally {
            graphics.disableScissor();
        }
        if (leaving != null) UiGlass.layer(graphics);
        paintLeaving(graphics);
    }

    // WHY: новый снимок казны с сервера и есть ответ на взятие: только тогда место взятой стопки
    // WHY: можно отдать соседям, иначе на долю секунды в нём мелькнула бы следующая стопка
    private void settle() {
        if (seenVersion == ClientTreasury.version()) return;
        seenVersion = ClientTreasury.version();
        vanished = -1;
        chosen = follow(ClientTreasury.items());
    }

    // WHY: после чужого взятия стопки съезжают, и выбор держится за свою стопку, а не за номер:
    // WHY: иначе он молча переехал бы на соседнюю, и двойной щелчок забрал бы её
    private int follow(List<ItemStack> items) {
        if (chosen < 0 || chosenSeen.isEmpty()) return -1;
        if (chosen < items.size() && ItemStack.isSameItemSameTags(items.get(chosen), chosenSeen)) return chosen;
        for (int index = 0; index < items.size(); index++) {
            if (ItemStack.isSameItemSameTags(items.get(index), chosenSeen)) return index;
        }
        return -1;
    }

    private void grow(int total) {
        while (hover.size() < total) hover.add(new Smooth(0.0f, HOVER_SPEED));
        while (counts.size() < total) counts.add(new UiMorphText());
    }

    private void paintSlot(GuiGraphics graphics, ItemStack stack, int index, int mouseX, int mouseY) {
        float x = tileX(index);
        float y = tileY(index);
        if (y + TILE < top || y > top + height) return;

        float appear = UiAnim.easeOut((System.currentTimeMillis() - shownAt - index * STAGGER_MS) / APPEAR_MS);
        boolean over = mouseX >= x && mouseX < x + TILE && mouseY >= y && mouseY < y + TILE;
        float focus = hover.get(index).to(over ? 1.0f : 0.0f, UiFrame.delta());
        float taken = index == chosen ? choice.get() : 0.0f;
        float lifted = y + (1.0f - appear) * APPEAR_LIFT - focus;
        UiGlass.panel(graphics, x, lifted, TILE, TILE, RADIUS, appear, 0.35f * taken + focus * 0.5f);
        if (taken > 0.01f) {
            UiRender.rim(graphics, x, lifted, TILE, TILE, RADIUS, 1.2f, UiTheme.alpha(UiAccent.color(), 0.75f * taken));
        }
        paintFace(graphics, stack, x, lifted);
        paintCount(graphics, counts.get(index), stack.getCount(), x, lifted, Math.max(taken, focus));
    }

    private void paintFace(GuiGraphics graphics, ItemStack stack, float x, float y) {
        graphics.pose().pushPose();
        graphics.pose().translate(x + TILE / 2.0f - 8.0f * ICON_SCALE, y + ICON_TOP, 0.0f);
        graphics.pose().scale(ICON_SCALE, ICON_SCALE, 1.0f);
        try {
            graphics.renderItem(stack, 0, 0);
        } finally {
            graphics.pose().popPose();
        }
        UiRender.textTrackedFit(graphics, font(), stack.getHoverName(), x + TILE / 2.0f, y + NAME_TOP, 10.0f,
                TILE - 8.0f, NAME_SCALE, 0.0f, UiAccent.text(), false);
    }

    private void paintCount(GuiGraphics graphics, UiMorphText morph, int count, float x, float y, float lit) {
        float plateX = x + UiMetrics.GAP;
        float plateWidth = TILE - UiMetrics.GAP * 2.0f;
        float plateY = y + TILE - PLATE_HEIGHT - PLATE_BOTTOM;
        UiGlass.sunken(graphics, plateX, plateY, plateWidth, PLATE_HEIGHT, UiMetrics.radius(PLATE_HEIGHT), 0.9f);
        String text = "x" + count;
        if (morph.raw().isEmpty()) morph.snap(text);
        morph.set(text);
        morph.advance(UiFrame.delta());
        float shown = morph.measure(graphics, font(), PLATE_SCALE);
        morph.draw(graphics, font(), x + (TILE - shown) / 2.0f, UiRender.centerY(plateY, PLATE_HEIGHT, PLATE_SCALE),
                PLATE_SCALE, UiTheme.mix(UiAccent.text(), UiAccent.color(), lit));
    }

    private void paintLeaving(GuiGraphics graphics) {
        if (leaving == null) return;
        float age = (System.currentTimeMillis() - leaving.at()) / LEAVE_MS;
        if (age >= 1.0f) {
            leaving = null;
            return;
        }
        float travel = UiAnim.easeOut(age);
        float fade = 1.0f - UiAnim.smoothstep(0.4f, 1.0f, age);
        float scale = 1.0f - 0.45f * travel;
        float centerX = leaving.x() + TILE / 2.0f;
        float centerY = leaving.y() + TILE / 2.0f + LEAVE_DROP * travel;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 50.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().translate(-TILE / 2.0f, -TILE / 2.0f, 0.0f);
        try {
            UiGlass.panel(graphics, 0.0f, 0.0f, TILE, TILE, RADIUS, fade, 0.6f);
            paintFace(graphics, leaving.stack(), 0.0f, 0.0f);
        } finally {
            graphics.pose().popPose();
        }
    }

    void renderPreview(GuiGraphics graphics, float paneLeft, float paneTop, float paneWidth, float paneHeight) {
        ItemStack stack = chosenStack();
        float centerX = paneLeft + paneWidth / 2.0f;
        if (stack.isEmpty()) {
            UiRender.textCentered(graphics, font(), Component.translatable("zones.shop.treasury.pick"), centerX,
                    paneTop + paneHeight / 2.0f, LINE_SCALE, UiAccent.textDim(), false);
            paintNote(graphics, paneLeft, paneTop + paneHeight * 0.62f, paneWidth);
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(centerX - 8.0f * ART_SCALE, paneTop + UiMetrics.PAD_WIDE * 2.0f, 0.0f);
        graphics.pose().scale(ART_SCALE, ART_SCALE, 1.0f);
        try {
            graphics.renderItem(stack, 0, 0);
        } finally {
            graphics.pose().popPose();
        }
        float textTop = paneTop + UiMetrics.PAD_WIDE * 3.0f + 16.0f * ART_SCALE;
        UiRender.textTrackedFit(graphics, font(), stack.getHoverName(), centerX, textTop, 14.0f,
                paneWidth - UiMetrics.PAD_WIDE * 2.0f, 0.95f, 0.0f, UiAccent.text(), false);
        UiRender.textCentered(graphics, font(), Component.translatable("zones.shop.treasury.count", stack.getCount()),
                centerX, textTop + 16.0f, LINE_SCALE, UiAccent.textDim(), false);
        paintNote(graphics, paneLeft, textTop + 34.0f, paneWidth);
    }

    private void paintNote(GuiGraphics graphics, float paneLeft, float y, float paneWidth) {
        float step = font().lineHeight * NAME_SCALE + 2.0f;
        int wrap = (int) ((paneWidth - UiMetrics.PAD_WIDE * 2.0f) / NAME_SCALE);
        for (FormattedCharSequence line : UiRender.split(graphics, font(), Component.translatable("zones.shop.treasury.note"), NAME_SCALE, wrap)) {
            UiRender.textCentered(graphics, font(), Component.literal(UiRender.flatten(line)), paneLeft + paneWidth / 2.0f, y,
                    NAME_SCALE, UiAccent.textFaint(), false);
            y += step;
        }
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}
