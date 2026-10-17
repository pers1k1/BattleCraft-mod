package com.persiki84.zones.client.menu;

import com.persiki84.capturepoints.client.ClientTreasury;
import com.persiki84.sellmod.client.ClientSellData;
import com.persiki84.sellmod.client.CurrencyIcon;
import com.persiki84.shared.client.menu.TickerLine;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiRestFrame;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiTicker;
import com.persiki84.shared.client.ui.UiTickerPool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    private static final float CREDIT_ICON = 10.0f;
    private static final Component NOTE = Component.translatable("zones.shop.treasury.note");
    private static final Component ACCOUNT_NOTE = Component.translatable("zones.shop.treasury.note.account");

    private record Leaving(ItemStack stack, Component name, float x, float y, long at) {}

    private final List<Smooth> hover = new ArrayList<>();
    private static final UiTicker.Format TIMES = count -> "x" + count;

    private final UiTickerPool counts = new UiTickerPool(UiTicker.CENTER);
    private final TickerLine countLine = new TickerLine(0.5f);
    private final TickerLine creditTicker = new TickerLine(0.0f);
    private Object[] keys = new Object[0];
    private Component[] names = new Component[0];
    private final Smooth choice = new Smooth(1.0f, CHOICE_SPEED);
    private float left;
    private float top;
    private float width;
    private float height;
    private float tileSize = TILE;
    private float gapSize = GAP;
    private float unit = 1.0f;
    private int columnCount = 1;
    private int scroll;
    private int chosen = -1;
    private int vanished = -1;
    private int seenVersion = -1;
    private long shownAt = System.currentTimeMillis();
    private Leaving leaving;
    private ItemStack chosenSeen = ItemStack.EMPTY;
    private int creditCount = -1;
    private int creditVersion = Integer.MIN_VALUE;
    private Component creditLine = Component.empty();
    private int countShown = -1;
    private Component countText;

    void place(float paneLeft, float paneTop, float paneWidth, float paneHeight) {
        left = paneLeft;
        top = paneTop;
        width = paneWidth;
        height = paneHeight;
        fit(paneWidth - UiMetrics.PAD * 2.0f);
    }

    // WHY: ряд занимает панель от поля до поля, как у StudioGrid.fit: столбцы считаются от заводской
    // WHY: плитки, а сама плитка растягивается на остаток, иначе справа оставалась полоса пустоты
    private void fit(float room) {
        if (room <= 0.0f) return;
        columnCount = Math.max(1, (int) ((room + GAP) / (TILE + GAP)));
        tileSize = UiMetrics.snap(room / (columnCount + (columnCount - 1) * (float) GAP / TILE));
        gapSize = columnCount > 1 ? Math.max(0.0f, (room - tileSize * columnCount) / (columnCount - 1)) : 0.0f;
        unit = tileSize / TILE;
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
        return columnCount;
    }

    private float step() {
        return tileSize + gapSize;
    }

    private int rows() {
        return Math.max(1, (int) ((height - LEAD * unit + gapSize) / step()));
    }

    private float tileX(int slot) {
        return left + UiMetrics.PAD + slot % columns() * step();
    }

    private float tileY(int slot) {
        return top + LEAD * unit + (slot / columns() - scroll) * step();
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
            boolean inside = mouseX >= x && mouseX < x + tileSize && mouseY >= y && mouseY < y + tileSize;
            if (inside && y >= top && y + tileSize <= top + height && index != vanished) return index;
        }
        return -1;
    }

    void choose(int index) {
        if (index == chosen) return;
        chosen = index;
        List<ItemStack> items = ClientTreasury.items();
        chosenSeen = index >= 0 && index < items.size() ? items.get(index).copy() : ItemStack.EMPTY;
        countLine.forget();
        creditTicker.forget();
        choice.snap(0.0f);
    }

    void leave(int index) {
        List<ItemStack> items = ClientTreasury.items();
        if (index < 0 || index >= items.size()) return;
        ItemStack stack = items.get(index).copy();
        leaving = new Leaving(stack, stack.getHoverName(), tileX(index), tileY(index), System.currentTimeMillis());
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
        keys = keysOf(ClientTreasury.items());
        names = namesOf(ClientTreasury.items());
    }

    // WHY: имя стопки собирается раз на снимок, а не в кадре: getHoverName строит компонент заново
    private static Component[] namesOf(List<ItemStack> items) {
        Component[] made = new Component[items.size()];
        for (int index = 0; index < items.size(); index++) made[index] = items.get(index).getHoverName();
        return made;
    }

    // WHY: число стопки держится за саму стопку (предмет, теги и номер среди таких же), а не за место:
    // WHY: после взятия соседние съезжают, и по номеру ход уехал бы к чужой стопке
    private static Object[] keysOf(List<ItemStack> items) {
        Object[] made = new Object[items.size()];
        Map<String, Integer> seen = new HashMap<>();
        for (int index = 0; index < items.size(); index++) {
            ItemStack stack = items.get(index);
            String kind = BuiltInRegistries.ITEM.getKey(stack.getItem()) + "#" + Objects.hashCode(stack.getTag());
            made[index] = kind + "#" + seen.merge(kind, 1, Integer::sum);
        }
        return made;
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
    }

    private void paintSlot(GuiGraphics graphics, ItemStack stack, int index, int mouseX, int mouseY) {
        float x = tileX(index);
        float y = tileY(index);
        if (y + tileSize < top || y > top + height) return;

        float appear = UiAnim.easeOut((System.currentTimeMillis() - shownAt - index * STAGGER_MS) / APPEAR_MS);
        boolean over = mouseX >= x && mouseX < x + tileSize && mouseY >= y && mouseY < y + tileSize;
        float focus = hover.get(index).to(over ? 1.0f : 0.0f, UiFrame.delta());
        float taken = index == chosen ? choice.get() : 0.0f;
        float lifted = y + (1.0f - appear) * APPEAR_LIFT * unit - focus;
        UiGlass.panel(graphics, x, lifted, tileSize, tileSize, RADIUS * unit, appear, 0.35f * taken + focus * 0.5f);
        if (taken > 0.01f) {
            UiRender.rim(graphics, x, lifted, tileSize, tileSize, RADIUS * unit, 1.2f,
                    UiTheme.alpha(UiAccent.color(), 0.75f * taken));
        }
        paintFace(graphics, stack, index < names.length ? names[index] : stack.getHoverName(), x, lifted);
        if (index < keys.length) paintCount(graphics, counts.of(keys[index]), stack.getCount(), x, lifted, Math.max(taken, focus));
    }

    private void paintFace(GuiGraphics graphics, ItemStack stack, Component name, float x, float y) {
        graphics.pose().pushPose();
        float iconScale = ICON_SCALE * unit;
        graphics.pose().translate(x + tileSize / 2.0f - 8.0f * iconScale, y + ICON_TOP * unit, 0.0f);
        graphics.pose().scale(iconScale, iconScale, 1.0f);
        try {
            graphics.renderItem(stack, 0, 0);
        } finally {
            graphics.pose().popPose();
        }
        UiRender.textTrackedFit(graphics, font(), name, x + tileSize / 2.0f, y + NAME_TOP * unit,
                10.0f * unit, tileSize - 8.0f * unit, NAME_SCALE * unit, 0.0f, UiAccent.text(), false);
    }

    private void paintCount(GuiGraphics graphics, UiTicker ticker, int count, float x, float y, float lit) {
        float plateX = x + UiMetrics.GAP * unit;
        float plateWidth = tileSize - UiMetrics.GAP * 2.0f * unit;
        float plateHeight = PLATE_HEIGHT * unit;
        float plateY = y + tileSize - plateHeight - PLATE_BOTTOM * unit;
        UiGlass.sunken(graphics, plateX, plateY, plateWidth, plateHeight, UiMetrics.radius(plateHeight), 0.9f);
        ticker.set(count, TIMES);
        float scale = PLATE_SCALE * unit;
        ticker.draw(graphics, font(), x + tileSize / 2.0f, UiRender.centerY(plateY, plateHeight, scale),
                scale, UiTheme.mix(UiAccent.text(), UiAccent.color(), lit));
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
        float centerX = leaving.x() + tileSize / 2.0f;
        float centerY = leaving.y() + tileSize / 2.0f;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 50.0f);
        UiRestFrame.push(graphics, centerX, centerY, scale, scale, 0.0f, LEAVE_DROP * unit * travel);
        try {
            UiGlass.panel(graphics, leaving.x(), leaving.y(), tileSize, tileSize, RADIUS * unit, fade, 0.6f);
            paintFace(graphics, leaving.stack(), leaving.name(), leaving.x(), leaving.y());
        } finally {
            UiRestFrame.pop(graphics);
            graphics.pose().popPose();
        }
    }

    // WHY: в режиме «Счёт» сервер зачисляет валюту из казны на счёт взявшего, а не в инвентарь
    static boolean creditsAccount(ItemStack stack) {
        return ClientSellData.accounts() && !stack.isEmpty() && stack.is(ClientSellData.currency());
    }

    void renderPreview(GuiGraphics graphics, float paneLeft, float paneTop, float paneWidth, float paneHeight) {
        ItemStack stack = chosenStack();
        float centerX = paneLeft + paneWidth / 2.0f;
        if (stack.isEmpty()) {
            UiRender.textCentered(graphics, font(), Component.translatable("zones.shop.treasury.pick"), centerX,
                    paneTop + paneHeight / 2.0f, LINE_SCALE, UiAccent.textDim(), false);
            paintNote(graphics, paneLeft, paneTop + paneHeight * 0.62f, paneWidth, NOTE);
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
        if (creditsAccount(stack)) {
            paintCredit(graphics, stack, centerX, textTop + 16.0f);
            paintNote(graphics, paneLeft, textTop + 34.0f, paneWidth, ACCOUNT_NOTE);
            return;
        }
        countLine.at(graphics, font(), countLabel(stack), centerX, textTop + 16.0f, LINE_SCALE, UiAccent.textDim());
        paintNote(graphics, paneLeft, textTop + 34.0f, paneWidth, NOTE);
    }

    private void paintCredit(GuiGraphics graphics, ItemStack stack, float centerX, float y) {
        if (creditCount != stack.getCount() || creditVersion != ClientSellData.version()) {
            creditCount = stack.getCount();
            creditVersion = ClientSellData.version();
            creditLine = Component.translatable("zones.shop.treasury.credit", ClientSellData.amountText(creditCount));
        }
        float width = UiRender.measure(graphics, font(), creditLine, LINE_SCALE);
        float left = centerX - (width + CREDIT_ICON + UiMetrics.GAP) / 2.0f;
        CurrencyIcon.draw(graphics, left, y - 1.5f, CREDIT_ICON, UiAccent.color(), 1.0f);
        creditTicker.at(graphics, font(), creditLine, left + CREDIT_ICON + UiMetrics.GAP, y, LINE_SCALE,
                UiAccent.color());
    }

    private Component countLabel(ItemStack stack) {
        if (countShown != stack.getCount() || countText == null) {
            countShown = stack.getCount();
            countText = Component.translatable("zones.shop.treasury.count", stack.getCount());
        }
        return countText;
    }

    private void paintNote(GuiGraphics graphics, float paneLeft, float y, float paneWidth, Component note) {
        float step = font().lineHeight * NAME_SCALE + 2.0f;
        int wrap = (int) ((paneWidth - UiMetrics.PAD_WIDE * 2.0f) / NAME_SCALE);
        for (FormattedCharSequence line : UiRender.split(graphics, font(), note, NAME_SCALE, wrap)) {
            UiRender.textCentered(graphics, font(), Component.literal(UiRender.flatten(line)), paneLeft + paneWidth / 2.0f, y,
                    NAME_SCALE, UiAccent.textFaint(), false);
            y += step;
        }
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}
