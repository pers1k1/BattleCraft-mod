package com.persiki84.shared.client.menu.studio;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiMorphText;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSound;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public final class StudioNav {
    public static final int ROW = 18;

    private static final float INDENT = 9.0f;
    private static final float LABEL_SCALE = 0.78f;
    private static final float COUNT_SCALE = 0.62f;
    private static final float HEADING_SCALE = 0.6f;
    private static final float HEADING_TRACKING = 0.6f;
    private static final float PILL_SPEED = 18.0f;
    private static final float HOVER_SPEED = 16.0f;
    private static final float GLIDE_SPEED = 19.0f;
    private static final float MARK_SPEED = 22.0f;
    private static final float APPEAR_MS = 180.0f;
    private static final float STAGGER_MS = 18.0f;
    private static final float APPEAR_SLIDE = 8.0f;
    private static final float CARRY_SCALE = 1.06f;
    private static final float GUIDE_WIDTH = 1.0f;
    private static final float LIFT_SPEED = 14.0f;
    private static final float SETTLE_MS = 220.0f;
    private static final long HOLD_MS = 1500L;
    private static final float GRAB_SPEED = 16.0f;
    private static final float SHIFT_SPEED = 17.0f;
    private static final float HUSH_ALPHA = 0.5f;
    private static final float HUSH_DROP = 2.0f;
    private static final float COUNT_WIDTH = 22.0f;
    private static final long NUDGE_MS = 3000L;

    private record Settling(Node node, float fromY, float toY, float lift, boolean moved, long at) {}

    private record Nudge(String base, int delta, long at) {}

    public enum Drop { NONE, BEFORE, AFTER, INTO }

    public record Node(String key, Component label, Component count, int depth, boolean heading, boolean movable) {
        public static Node item(String key, Component label, Component count, int depth, boolean movable) {
            return new Node(key, label, count, depth, false, movable);
        }

        public static Node heading(String key, Component label) {
            return new Node(key, label, Component.empty(), 0, true, false);
        }
    }

    private final Map<String, Smooth> hover = new HashMap<>();
    private final Smooth pill = new Smooth(0.0f, PILL_SPEED);
    private final Smooth glide = new Smooth(0.0f, GLIDE_SPEED);
    private final Smooth mark = new Smooth(0.0f, MARK_SPEED);
    private final Smooth markShown = new Smooth(0.0f, MARK_SPEED);
    private final Smooth carryLift = new Smooth(0.0f, LIFT_SPEED);
    private final Map<String, UiMorphText> counts = new HashMap<>();
    private final Map<String, Nudge> nudges = new HashMap<>();
    private Settling settling;
    private final Map<String, Smooth> rowShift = new HashMap<>();
    private final Map<String, Integer> rowIndex = new HashMap<>();
    private float grabY;
    private List<Node> shown = List.of();
    private long shownAt = System.currentTimeMillis();
    private String hovered;
    private boolean pillPlaced;
    private float left;
    private float top;
    private float width;
    private float height;
    private int scroll;
    private Node carried;
    private float carryY;
    private Node target;
    private Drop drop = Drop.NONE;
    private String dropKey;

    public void place(float navLeft, float navTop, float navWidth, float navHeight) {
        left = navLeft;
        top = navTop;
        width = navWidth;
        height = navHeight;
    }

    public boolean over(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    private int capacity() {
        return Math.max(1, (int) ((height - UiMetrics.PAD * 2.0f) / ROW));
    }

    public void scrollBy(int step) {
        int next = Math.max(0, Math.min(Math.max(0, shown.size() - capacity()), scroll + step));
        if (next == scroll) return;
        glide.snap(glide.get() + (next - scroll) * ROW);
        scroll = next;
    }

    public void reveal(String key) {
        int index = indexOf(key);
        if (index < 0) return;
        if (index < scroll) scrollBy(index - scroll);
        if (index >= scroll + capacity()) scrollBy(index - scroll - capacity() + 1);
    }

    private int indexOf(String key) {
        for (int index = 0; index < shown.size(); index++) {
            if (shown.get(index).key().equals(key)) return index;
        }
        return -1;
    }

    public Node at(double mouseX, double mouseY) {
        if (!over(mouseX, mouseY)) return null;
        for (int index = 0; index < shown.size(); index++) {
            float y = rowY(index);
            Node node = shown.get(index);
            if (!node.heading() && mouseY >= y && mouseY < y + ROW) return node;
        }
        return null;
    }

    private float rowY(int index) {
        return top + UiMetrics.PAD + (index - scroll) * ROW + glide.get();
    }

    // WHY: взятая строка подъезжает к курсору от своего места, а не прыгает серединой под него
    public void carry(Node node, double mouseY) {
        carried = node;
        carryY = (float) mouseY;
        int own = indexOf(node.key());
        grabY = own < 0 ? 0.0f : shownY(own) - (carryY - ROW / 2.0f);
        carryLift.snap(0.0f);
        target = null;
        drop = Drop.NONE;
    }

    public Node carried() {
        return carried;
    }

    public void carryTo(double mouseX, double mouseY, BiFunction<Node, Node, Drop> rule) {
        carryY = (float) mouseY;
        Node over = at(mouseX, mouseY);
        target = over == null || over == carried ? null : over;
        drop = target == null ? Drop.NONE : rule.apply(carried, target);
        if (drop == Drop.BEFORE && mouseY > rowY(indexOf(target.key())) + ROW / 2.0f) drop = Drop.AFTER;
        if (drop == Drop.AFTER && mouseY < rowY(indexOf(target.key())) + ROW / 2.0f) drop = Drop.BEFORE;
    }

    public Node target() {
        return target;
    }

    public Drop drop() {
        return drop;
    }

    // WHY: отпущенная строка садится туда, куда её бросили, или назад на своё место, и подъём сходит
    // WHY: на нет по пути. Брошенная на новое место ждёт там ответа сервера, а сама строка списка на
    // WHY: это время спрятана: иначе на полсекунды она стояла бы в двух местах сразу
    public void release() {
        boolean moved = target != null && drop != Drop.NONE;
        if (carried != null) settling = new Settling(carried, carryY - ROW / 2.0f + grabY, settleY(),
                carryLift.get(), moved, System.currentTimeMillis());
        carried = null;
        target = null;
        drop = Drop.NONE;
    }

    private float settleY() {
        int own = indexOf(carried.key());
        if (target != null && drop != Drop.NONE) {
            int place = indexOf(target.key());
            float row = rowY(place) + (drop == Drop.AFTER ? ROW : 0.0f);
            return own >= 0 && own < place ? row - ROW : row;
        }
        return own < 0 ? carryY - ROW / 2.0f : shownY(own);
    }

    private float shownY(int index) {
        Smooth shift = rowShift.get(shown.get(index).key());
        return rowY(index) + (shift == null ? 0.0f : shift.get());
    }

    public boolean lifting() {
        return carried != null || settling != null;
    }

    // WHY: центр строки раздела в координатах экрана: туда улетает плитка, брошенная в раздел
    public float[] anchor(String key) {
        int index = indexOf(key);
        if (index < 0) return null;
        Node node = shown.get(index);
        float x = rowLeft(node);
        return new float[] {(x + left + width - UiMetrics.GAP) / 2.0f, rowY(index) + ROW / 2.0f};
    }

    // WHY: число раздела меняется сразу на броске, не дожидаясь сервера: перенос иначе полсекунды
    // WHY: выглядел несработавшим. Поправка живёт, пока сервер не прислал своё число или NUDGE_MS
    public void nudge(String key, int delta) {
        if (key == null || delta == 0) return;
        Node node = shown.stream().filter(candidate -> candidate.key().equals(key)).findFirst().orElse(null);
        if (node == null) return;
        Nudge known = nudges.get(key);
        int carriedDelta = known != null && known.base().equals(node.count().getString()) ? known.delta() : 0;
        nudges.put(key, new Nudge(node.count().getString(), carriedDelta + delta, System.currentTimeMillis()));
    }

    private String countOf(Node node) {
        String base = node.count().getString();
        Nudge nudge = nudges.get(node.key());
        if (nudge == null) return base;
        if (!nudge.base().equals(base) || System.currentTimeMillis() - nudge.at() > NUDGE_MS) {
            nudges.remove(node.key());
            return base;
        }
        try {
            return String.valueOf(Math.max(0, Integer.parseInt(base.trim()) + nudge.delta()));
        } catch (NumberFormatException notNumber) {
            return base;
        }
    }

    public void hoverDrop(double mouseX, double mouseY, Predicate<Node> accepts) {
        Node over = at(mouseX, mouseY);
        dropKey = over != null && accepts.test(over) ? over.key() : null;
    }

    public String dropKey() {
        return dropKey;
    }

    public void clearDrop() {
        dropKey = null;
    }

    public void render(GuiGraphics graphics, List<Node> nodes, String selected, int mouseX, int mouseY) {
        shown = nodes;
        glide.to(0.0f, UiFrame.delta());
        trackRows();
        advanceCounts();
        UiRender.clip(graphics, left, top, width, height);
        try {
            paintPill(graphics, selected);
            paintRows(graphics, selected, mouseX, mouseY);
            paintMarker(graphics);
            graphics.flush();
        } finally {
            graphics.disableScissor();
        }
    }

    // WHY: строки, сменившие место после ответа сервера, доезжают до него, а не перескакивают. Строку,
    // WHY: которую несли, принимает её поднятая копия: она стоит ровно там, где строка теперь живёт
    private void trackRows() {
        float delta = UiFrame.delta();
        for (int index = 0; index < shown.size(); index++) {
            String key = shown.get(index).key();
            Integer was = rowIndex.put(key, index);
            Smooth shift = rowShift.computeIfAbsent(key, unused -> new Smooth(0.0f, SHIFT_SPEED));
            if (was != null && was != index) shift.snap(shift.get() + (was - index) * ROW);
            if (was != null && was != index && settling != null && settling.node().key().equals(key)) {
                shift.snap(settledY() - rowY(index));
                settling = null;
            }
            shift.to(0.0f, delta);
        }
    }

    // WHY: число раздела перекатывается по цифрам, как таймер острова, а не подменяется разом
    private void advanceCounts() {
        float delta = UiFrame.delta();
        for (Node node : shown) {
            String value = countOf(node);
            if (value.isEmpty()) continue;
            UiMorphText morph = counts.computeIfAbsent(node.key(), key -> {
                UiMorphText made = new UiMorphText();
                made.snap(value);
                return made;
            });
            morph.set(value);
            morph.advance(delta);
        }
    }

    // WHY: поднятая строка рисуется поверх всех окон, после пересъёмки интерфейса экраном
    public void renderLifted(GuiGraphics graphics) {
        if (settling != null) paintSettling(graphics);
        if (carried != null) paintCarried(graphics);
    }

    private void paintPill(GuiGraphics graphics, String selected) {
        int index = selected == null ? -1 : indexOf(selected);
        if (index < 0) {
            pillPlaced = false;
            return;
        }
        float targetY = rowY(index) - glide.get();
        if (!pillPlaced) pill.snap(targetY);
        pillPlaced = true;
        float y = pill.to(targetY, UiFrame.delta()) + glide.get();
        float x = rowLeft(shown.get(index));
        float w = left + width - UiMetrics.GAP - x;
        UiGlass.panel(graphics, x, y + 1.0f, w, ROW - 2.0f, UiMetrics.radius(ROW - 2.0f), 1.0f, 0.55f);
        UiRender.rim(graphics, x, y + 1.0f, w, ROW - 2.0f, UiMetrics.radius(ROW - 2.0f), 1.1f,
                UiTheme.alpha(UiAccent.color(), 0.8f));
    }

    private void paintRows(GuiGraphics graphics, String selected, int mouseX, int mouseY) {
        String pointed = null;
        for (int index = 0; index < shown.size(); index++) {
            Node node = shown.get(index);
            float y = shownY(index);
            if (y + ROW < top || y > top + height) continue;
            if (settling != null && settling.node().key().equals(node.key())) continue;
            boolean over = !node.heading() && carried == null && mouseY >= y && mouseY < y + ROW && over(mouseX, mouseY);
            if (over) pointed = node.key();
            paintRow(graphics, node, index, y, node.key().equals(selected), over);
        }
        announce(pointed);
    }

    private void announce(String pointed) {
        if (pointed != null && !pointed.equals(hovered)) UiSound.hover();
        hovered = pointed;
    }

    private void paintRow(GuiGraphics graphics, Node node, int index, float y, boolean chosen, boolean over) {
        float appear = UiAnim.easeOut((System.currentTimeMillis() - shownAt - index * STAGGER_MS) / APPEAR_MS);
        float slide = (1.0f - appear) * APPEAR_SLIDE;
        if (node.heading()) {
            paintHeading(graphics, node, y, appear, slide);
            return;
        }
        float focus = hover.computeIfAbsent(node.key(), key -> new Smooth(0.0f, HOVER_SPEED))
                .to(over ? 1.0f : 0.0f, UiFrame.delta());
        float dim = carried != null && carried.key().equals(node.key()) ? 0.35f : 1.0f;
        paintBody(graphics, node, y, appear * dim, focus, chosen, slide);
    }

    private void paintHeading(GuiGraphics graphics, Node node, float y, float appear, float slide) {
        UiRender.textTrackedBox(graphics, font(), node.label(), left + UiMetrics.PAD_WIDE + slide, y + 3.0f,
                ROW - 3.0f, width - UiMetrics.PAD_WIDE * 2.0f, HEADING_SCALE, HEADING_TRACKING,
                UiTheme.alpha(UiAccent.textFaint(), appear), false, 0.0f);
    }

    private void paintBody(GuiGraphics graphics, Node node, float y, float appear, float focus, boolean chosen,
                           float slide) {
        float x = rowLeft(node) + slide;
        float w = left + width - UiMetrics.GAP - x;
        boolean target = node.key().equals(dropKey) || drop == Drop.INTO && this.target == node;
        if (!chosen) UiGlass.panel(graphics, x, y + 1.0f, w, ROW - 2.0f, UiMetrics.radius(ROW - 2.0f),
                0.55f * appear, focus * 0.3f);
        if (target) UiRender.rim(graphics, x, y + 1.0f, w, ROW - 2.0f, UiMetrics.radius(ROW - 2.0f), 1.3f,
                UiTheme.alpha(UiAccent.color(), UiAnim.pulse(700.0f, 0.5f, 1.0f)));
        if (node.depth() > 0) UiRender.rect(graphics, x - INDENT / 2.0f - 1.0f, y + 2.0f, GUIDE_WIDTH, ROW - 4.0f,
                UiTheme.alpha(UiAccent.textFaint(), 0.5f * appear));
        paintText(graphics, node, x, y, w, appear, chosen || focus > 0.5f);
    }

    private void paintText(GuiGraphics graphics, Node node, float x, float y, float w, float appear, boolean lit) {
        int tone = lit ? UiAccent.text() : UiAccent.textDim();
        UiMorphText count = node.count().getString().isEmpty() ? null : counts.get(node.key());
        float countWidth = count == null ? 0.0f : COUNT_WIDTH;
        UiRender.textTrackedBox(graphics, font(), node.label(), x + UiMetrics.PAD, y, ROW,
                w - UiMetrics.PAD * 2.0f - countWidth, LABEL_SCALE, 0.0f, UiTheme.alpha(tone, appear), false, 0.0f);
        if (count != null) {
            count.drawRight(graphics, font(), x + w - UiMetrics.PAD, UiRender.centerY(y, ROW, COUNT_SCALE),
                    COUNT_SCALE, UiTheme.alpha(UiAccent.textFaint(), appear));
        }
    }

    private void paintMarker(GuiGraphics graphics) {
        boolean wanted = carried != null && (drop == Drop.BEFORE || drop == Drop.AFTER);
        float shownAmount = markShown.to(wanted ? 1.0f : 0.0f, UiFrame.delta());
        if (!wanted || shownAmount <= 0.02f) return;

        float targetY = rowY(indexOf(target.key())) + (drop == Drop.AFTER ? ROW : 0.0f) - 1.0f;
        if (shownAmount < 0.1f) mark.snap(targetY);
        float y = mark.to(targetY, UiFrame.delta());
        float x = rowLeft(target);
        UiRender.panel(graphics, x, y, left + width - UiMetrics.GAP - x, 2.0f, 1.0f,
                UiTheme.alpha(UiAccent.color(), shownAmount));
    }

    private void paintCarried(GuiGraphics graphics) {
        float delta = UiFrame.delta();
        grabY *= (float) Math.exp(-GRAB_SPEED * delta);
        paintRaised(graphics, carried, carryY - ROW / 2.0f + grabY, carryLift.to(1.0f, delta), 1.0f);
    }

    // WHY: строка, вернувшаяся на своё место, отдаёт его списку, как только доехала. Брошенная на новое
    // WHY: место стоит там до ответа сервера, а если ответа нет (отказ), через HOLD_MS уходит
    private void paintSettling(GuiGraphics graphics) {
        long age = System.currentTimeMillis() - settling.at();
        boolean arrived = age >= SETTLE_MS;
        if (arrived && (!settling.moved() || age >= HOLD_MS)) {
            settling = null;
            return;
        }
        float travel = UiAnim.easeOut(Math.min(1.0f, age / SETTLE_MS));
        paintRaised(graphics, settling.node(), settledY(), settling.lift() * (1.0f - travel), 1.0f);
    }

    private float settledY() {
        float travel = UiAnim.easeOut(Math.min(1.0f, (System.currentTimeMillis() - settling.at()) / SETTLE_MS));
        return settling.fromY() + (settling.toY() - settling.fromY()) * travel;
    }

    private void paintRaised(GuiGraphics graphics, Node node, float y, float lift, float alpha) {
        float x = rowLeft(node);
        float w = left + width - UiMetrics.GAP - x;
        float scale = 1.0f + (CARRY_SCALE - 1.0f) * lift;
        float centerY = y + ROW / 2.0f;
        graphics.pose().pushPose();
        graphics.pose().translate(x + w / 2.0f, centerY, 60.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().translate(-(x + w / 2.0f), -centerY, 0.0f);
        try {
            UiGlass.hush(graphics, x, y + HUSH_DROP * lift, w, ROW - 1.0f, UiMetrics.radius(ROW - 1.0f),
                    HUSH_ALPHA * lift * alpha);
            UiGlass.panel(graphics, x, y, w, ROW - 1.0f, UiMetrics.radius(ROW - 1.0f), alpha, 0.7f * lift);
            paintText(graphics, node, x, y, w, alpha, true);
        } finally {
            graphics.pose().popPose();
        }
    }

    private float rowLeft(Node node) {
        return left + UiMetrics.GAP + node.depth() * INDENT;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}
