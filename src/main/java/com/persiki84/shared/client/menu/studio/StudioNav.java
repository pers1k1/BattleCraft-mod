package com.persiki84.shared.client.menu.studio;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
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
    private static final float CARRY_SCALE = 1.04f;
    private static final float GUIDE_WIDTH = 1.0f;

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

    public void carry(Node node, double mouseY) {
        carried = node;
        carryY = (float) mouseY;
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

    public void release() {
        carried = null;
        target = null;
        drop = Drop.NONE;
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
        UiRender.clip(graphics, left, top, width, height);
        try {
            paintPill(graphics, selected);
            paintRows(graphics, selected, mouseX, mouseY);
            paintMarker(graphics);
            graphics.flush();
        } finally {
            graphics.disableScissor();
        }
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
            float y = rowY(index);
            if (y + ROW < top || y > top + height) continue;
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
        float countWidth = node.count().getString().isEmpty() ? 0.0f : 22.0f;
        UiRender.textTrackedBox(graphics, font(), node.label(), x + UiMetrics.PAD, y, ROW,
                w - UiMetrics.PAD * 2.0f - countWidth, LABEL_SCALE, 0.0f, UiTheme.alpha(tone, appear), false, 0.0f);
        if (countWidth > 0.0f) {
            UiRender.textTrackedBox(graphics, font(), node.count(), x + w - UiMetrics.PAD - countWidth, y, ROW,
                    countWidth, COUNT_SCALE, 0.0f, UiTheme.alpha(UiAccent.textFaint(), appear), false, 1.0f);
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
        float x = rowLeft(carried);
        float w = left + width - UiMetrics.GAP - x;
        float y = carryY - ROW / 2.0f;
        graphics.pose().pushPose();
        graphics.pose().translate(x + w / 2.0f, carryY, 60.0f);
        graphics.pose().scale(CARRY_SCALE, CARRY_SCALE, 1.0f);
        graphics.pose().translate(-(x + w / 2.0f), -carryY, 0.0f);
        try {
            UiGlass.panel(graphics, x, y, w, ROW - 1.0f, UiMetrics.radius(ROW - 1.0f), 1.0f, 0.7f);
            paintText(graphics, carried, x, y, w, 1.0f, true);
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
