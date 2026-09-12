package com.persiki84.battlecraft.client.custom;

import com.persiki84.shared.client.ui.Spring;
import com.persiki84.shared.client.ui.UiFrame;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class HudLayout {
    public static final float STACK_GAP = 6.0f;
    public static final float DOCK_GAP = 6.0f;
    public static final float SAFE_MARGIN = 8.0f;

    private static final HudSlot[] SLOTS = HudSlot.values();
    private static final int COUNT = SLOTS.length;
    private static final float PUSH_LIMIT = 48.0f;
    private static final float PUSH_RESPONSE = 0.26f;
    private static final float PUSH_DAMPING = 0.9f;
    private static final int RELAX_PASSES = 4;
    private static final float FRESH_SECONDS = 0.6f;
    private static final float GROWTH_MARK = 0.75f;
    private static final float SETTLE_THRESHOLD = 0.5f;

    private static final Map<HudSlot, HudPlacement> placements = new EnumMap<>(HudSlot.class);
    private static final Map<HudSlot, HudBox> boxes = new EnumMap<>(HudSlot.class);
    private static final Map<HudSlot, Float> heights = new EnumMap<>(HudSlot.class);
    private static final Map<HudSlot, Float> stackOffsets = new EnumMap<>(HudSlot.class);
    private static final Set<HudSlot> seen = EnumSet.noneOf(HudSlot.class);
    private static final Set<HudSlot> drawnLastFrame = EnumSet.noneOf(HudSlot.class);

    private static final float[] baseX = new float[COUNT];
    private static final float[] baseY = new float[COUNT];
    private static final float[] shownWidth = new float[COUNT];
    private static final float[] shownHeight = new float[COUNT];
    private static final Spring[] driftX = new Spring[COUNT];
    private static final Spring[] driftY = new Spring[COUNT];
    private static final float[] freshFor = new float[COUNT];
    private static final float[] priorWidth = new float[COUNT];
    private static final float[] priorHeight = new float[COUNT];
    private static final boolean[] priorLive = new boolean[COUNT];
    private static final float[] leanX = new float[COUNT];
    private static final float[] leanY = new float[COUNT];
    private static final float[] sampleWidth = new float[COUNT];
    private static final float[] sampleHeight = new float[COUNT];

    private static float bottomClearance;
    private static HudSlot dragged;

    static {
        for (HudSlot slot : SLOTS) {
            placements.put(slot, new HudPlacement(slot));
            boxes.put(slot, new HudBox(slot));
            heights.put(slot, 0.0f);
            stackOffsets.put(slot, 0.0f);
            driftX[slot.ordinal()] = new Spring(PUSH_RESPONSE, PUSH_DAMPING, 0.0f);
            driftY[slot.ordinal()] = new Spring(PUSH_RESPONSE, PUSH_DAMPING, 0.0f);
            leanX[slot.ordinal()] = Float.NaN;
            leanY[slot.ordinal()] = Float.NaN;
        }
    }

    private HudLayout() {}

    public static HudPlacement of(HudSlot slot) {
        return placements.get(slot);
    }

    public static HudBox box(HudSlot slot) {
        return boxes.get(slot);
    }

    public static void resetAll() {
        for (HudPlacement placement : placements.values()) {
            placement.reset();
        }
    }

    public static void resetPositions() {
        for (HudPlacement placement : placements.values()) {
            placement.resetPosition();
        }
    }

    public static float dim(HudSlot slot) {
        return placements.get(slot).dim();
    }

    public static float corner(HudSlot slot) {
        return placements.get(slot).corner();
    }

    public static void beginFrame(float clearance, float screenWidth, float screenHeight) {
        bottomClearance = clearance;
        HudSolver.room(screenWidth, screenHeight);
        forgetMissing();
        drawnLastFrame.clear();
        drawnLastFrame.addAll(seen);
        markBoxes();
        seen.clear();
        buildStacks();
        feedSolver(UiFrame.delta());
        HudSolver.solve(DOCK_GAP, PUSH_LIMIT, RELAX_PASSES);
        applyPushes(UiFrame.delta());
    }

    private static void markBoxes() {
        for (HudSlot slot : SLOTS) {
            if (!seen.contains(slot)) boxes.get(slot).clear();
        }
    }

    private static void forgetMissing() {
        for (HudSlot slot : SLOTS) {
            if (!seen.contains(slot)) heights.put(slot, 0.0f);
        }
    }

    private static void feedSolver(float delta) {
        HudSolver.begin();
        for (int index = 0; index < COUNT; index++) {
            HudSlot slot = SLOTS[index];
            HudPlacement placement = placements.get(slot);
            HudDock dock = placement.dock();
            boolean live = drawnLastFrame.contains(slot) && placement.visible();
            markFresh(index, live, delta);
            HudSolver.feed(index, baseX[index], baseY[index], shownWidth[index], shownHeight[index],
                    live, priority(slot, index), dock == null ? -1 : dock.target().ordinal(), edge(slot));
        }
    }

    private static int priority(HudSlot slot, int index) {
        if (slot == dragged) return -2;
        return freshFor[index] > 0.0f ? -1 : index;
    }

    private static void markFresh(int index, boolean live, float delta) {
        freshFor[index] = Math.max(0.0f, freshFor[index] - delta);
        boolean grew = Math.abs(shownWidth[index] - priorWidth[index]) > GROWTH_MARK
                || Math.abs(shownHeight[index] - priorHeight[index]) > GROWTH_MARK;
        if (live && (!priorLive[index] || grew)) freshFor[index] = FRESH_SECONDS;

        priorWidth[index] = shownWidth[index];
        priorHeight[index] = shownHeight[index];
        priorLive[index] = live;
    }

    private static void applyPushes(float delta) {
        for (int index = 0; index < COUNT; index++) {
            if (SLOTS[index] == dragged) {
                driftX[index].snap(0.0f);
                driftY[index].snap(0.0f);
                continue;
            }
            driftX[index].to(HudSolver.pushX(index), delta);
            driftY[index].to(HudSolver.pushY(index), delta);
        }
    }

    private static void buildStacks() {
        Map<HudAnchor, Float> used = new EnumMap<>(HudAnchor.class);
        for (HudSlot slot : SLOTS) {
            HudPlacement placement = placements.get(slot);
            if (!slot.stacked() || placement.dock() != null) {
                stackOffsets.put(slot, 0.0f);
                continue;
            }
            HudAnchor anchor = placement.anchor();
            float taken = used.getOrDefault(anchor, 0.0f);
            stackOffsets.put(slot, taken);
            float height = heights.getOrDefault(slot, 0.0f);
            if (height > 0.0f) used.put(anchor, taken + height + STACK_GAP);
        }
    }

    public static HudBox place(HudSlot slot, float width, float height, float screenWidth, float screenHeight) {
        seen.add(slot);
        return lay(slot, width, height, screenWidth, screenHeight);
    }

    public static HudBox placeBelow(HudSlot slot, float width, float height, float screenWidth,
                                    float screenHeight, float ceilingY) {
        HudBox box = place(slot, width, height, screenWidth, screenHeight);
        if (moved(slot) || box.y() >= ceilingY) return box;

        return shiftTo(slot, ceilingY, screenHeight);
    }

    public static HudBox placeAbove(HudSlot slot, float width, float height, float screenWidth,
                                    float screenHeight, float floorY) {
        HudBox box = place(slot, width, height, screenWidth, screenHeight);
        float shown = shownHeight[slot.ordinal()];
        if (moved(slot) || box.y() + shown <= floorY) return box;

        return shiftTo(slot, floorY - shown, screenHeight);
    }

    private static HudBox shiftTo(HudSlot slot, float y, float screenHeight) {
        int index = slot.ordinal();
        baseY[index] = clamp(y, shownHeight[index], screenHeight, edge(slot));
        HudBox box = boxes.get(slot);
        box.moveY(clamp(baseY[index] + driftY[index].get(), shownHeight[index], screenHeight, edge(slot)));
        return box;
    }

    public static void outlineMissing(float screenWidth, float screenHeight) {
        for (HudSlot slot : SLOTS) {
            if (drawnLastFrame.contains(slot)) continue;
            lay(slot, outlineWidth(slot), outlineHeight(slot), screenWidth, screenHeight);
        }
    }

    public static void sample(HudSlot slot, float width, float height) {
        sampleWidth[slot.ordinal()] = width;
        sampleHeight[slot.ordinal()] = height;
    }

    public static float outlineWidth(HudSlot slot) {
        float measured = sampleWidth[slot.ordinal()];
        return measured > 0.0f ? measured : slot.outlineWidth();
    }

    public static float outlineHeight(HudSlot slot) {
        float measured = sampleHeight[slot.ordinal()];
        return measured > 0.0f ? measured : slot.outlineHeight();
    }

    private static HudBox lay(HudSlot slot, float width, float height, float screenWidth, float screenHeight) {
        HudPlacement placement = placements.get(slot);
        int index = slot.ordinal();
        float scale = placement.scale();
        float shownW = width * scale;
        float shownH = height * scale;

        if (seen.contains(slot)) heights.put(slot, shownH);
        shownWidth[index] = shownW;
        shownHeight[index] = shownH;

        float freeX = anchorX(slot, placement, shownW, screenWidth);
        float freeY = anchorY(slot, placement, shownH, screenHeight);
        HudDock dock = placement.dock();
        baseX[index] = clamp(dock == null ? freeX : dockedX(index, dock, shownW, freeX),
                shownW, screenWidth, edge(slot));
        baseY[index] = clamp(dock == null ? freeY : dockedY(index, dock, shownH, freeY),
                shownH, screenHeight, edge(slot));

        HudBox box = boxes.get(slot);
        box.set(clamp(baseX[index] + driftX[index].get(), shownW, screenWidth, edge(slot)),
                clamp(baseY[index] + driftY[index].get(), shownH, screenHeight, edge(slot)),
                width, height, scale, placement.alpha());
        return box;
    }

    private static float anchorX(HudSlot slot, HudPlacement placement, float shownWidth, float screenWidth) {
        return placement.anchor().originX(screenWidth, shownWidth, slot.margin()) + placement.offsetX();
    }

    private static float anchorY(HudSlot slot, HudPlacement placement, float shownHeight, float screenHeight) {
        return placement.anchor().originY(screenHeight, shownHeight, slot.margin()) + placement.offsetY()
                + slot.baseOffsetY() + verticalShift(slot, placement.anchor());
    }

    private static float dockedX(int index, HudDock dock, float shownWidth, float fallback) {
        HudBox target = boxes.get(dock.target());
        if (!drawnLastFrame.contains(dock.target())) return kept(leanX[index], fallback);

        float span = target.width() * target.scale();
        leanX[index] = leaned(dock.side(), HudSide.LEFT, HudSide.RIGHT, target.x(), span, shownWidth,
                dock.align(), fallback);
        return leanX[index];
    }

    private static float dockedY(int index, HudDock dock, float shownHeight, float fallback) {
        HudBox target = boxes.get(dock.target());
        if (!drawnLastFrame.contains(dock.target())) return kept(leanY[index], fallback);

        float span = target.height() * target.scale();
        leanY[index] = leaned(dock.side(), HudSide.TOP, HudSide.BOTTOM, target.y(), span, shownHeight,
                dock.align(), fallback);
        return leanY[index];
    }

    private static float leaned(HudSide side, HudSide before, HudSide after, float origin, float span,
                                float shown, HudAlign align, float fallback) {
        if (side == before) return origin - DOCK_GAP - shown;
        if (side == after) return origin + span + DOCK_GAP;
        return along(align, origin, span, shown, fallback);
    }

    private static float kept(float held, float fallback) {
        return Float.isNaN(held) ? fallback : held;
    }

    private static float along(HudAlign align, float origin, float span, float shown, float fallback) {
        if (align == HudAlign.START) return origin;
        if (align == HudAlign.CENTER) return origin + (span - shown) / 2.0f;
        if (align == HudAlign.END) return origin + span - shown;
        return fallback;
    }

    private static float verticalShift(HudSlot slot, HudAnchor anchor) {
        float lift = anchor.bottom() && !pinned(slot) ? bottomClearance : 0.0f;
        float stacked = stackOffsets.getOrDefault(slot, 0.0f);
        return anchor.bottom() ? -(stacked + lift) : stacked;
    }

    private static float edge(HudSlot slot) {
        return Math.min(SAFE_MARGIN, slot.margin());
    }

    private static float clamp(float value, float size, float limit, float margin) {
        float room = limit - size - margin;
        if (room < margin) return Math.max(0.0f, (limit - size) / 2.0f);
        return Math.max(margin, Math.min(room, value));
    }

    public static boolean drawn(HudSlot slot) {
        return drawnLastFrame.contains(slot);
    }

    public static boolean visible(HudSlot slot) {
        return placements.get(slot).visible();
    }

    public static boolean pinned(HudSlot slot) {
        HudPlacement placement = placements.get(slot);
        return placement.dock() != null || placement.anchor() != slot.fallback()
                || placement.offsetX() != 0.0f || placement.offsetY() != 0.0f;
    }

    // WHY: заводская стыковка - это не выбор игрока, а место слота по умолчанию, поэтому поправки
    // WHY: вроде placeAbove обязаны работать при ней и отступать только перед перетащенным элементом
    public static boolean moved(HudSlot slot) {
        HudPlacement placement = placements.get(slot);
        return !placement.leaning() || placement.anchor() != slot.fallback()
                || placement.offsetX() != 0.0f || placement.offsetY() != 0.0f;
    }

    public static float slideX(HudSlot slot, float travel) {
        HudAnchor anchor = placements.get(slot).anchor();
        if (anchor.right()) return travel;
        if (anchor.left()) return -travel;
        return 0.0f;
    }

    public static int tint(HudSlot slot, int fallback) {
        return placements.get(slot).tinted(fallback);
    }

    public static void washTints(float delta) {
        for (HudSlot slot : SLOTS) {
            placements.get(slot).washTint(delta);
        }
    }

    public static void push(net.minecraft.client.gui.GuiGraphics graphics, HudBox box) {
        com.persiki84.shared.client.ui.UiVital.dress(corner(box.slot()), dim(box.slot()));
        if (box.scale() == 1.0f) return;

        graphics.pose().pushPose();
        graphics.pose().translate(box.x(), box.y(), 0.0f);
        graphics.pose().scale(box.scale(), box.scale(), 1.0f);
        graphics.pose().translate(-box.x(), -box.y(), 0.0f);
    }

    public static void pop(net.minecraft.client.gui.GuiGraphics graphics, HudBox box) {
        com.persiki84.shared.client.ui.UiVital.undress();
        if (box.scale() == 1.0f) return;
        graphics.pose().popPose();
    }

    public static HudSlot pick(double pointX, double pointY) {
        HudSlot found = null;
        float smallest = Float.MAX_VALUE;
        for (HudBox box : boxes.values()) {
            if (!box.holds(pointX, pointY)) continue;
            float area = box.width() * box.height();
            if (area < smallest) {
                smallest = area;
                found = box.slot();
            }
        }
        return found;
    }

    public static boolean free(HudSlot slot, HudSlot target) {
        HudSlot walker = target;
        for (int step = 0; walker != null && step <= COUNT; step++) {
            if (walker == slot) return false;
            HudDock dock = placements.get(walker).dock();
            walker = dock == null ? null : dock.target();
        }
        return true;
    }

    public static void dragging(HudSlot slot) {
        dragged = slot;
    }
    public static void moveTo(HudSlot slot, float targetX, float targetY, float screenWidth, float screenHeight) {
        HudPlacement placement = placements.get(slot);
        float shownW = shownWidth[slot.ordinal()];
        float shownH = shownHeight[slot.ordinal()];

        HudAnchor anchor = HudAnchor.nearest(targetX + shownW / 2.0f, targetY + shownH / 2.0f,
                screenWidth, screenHeight);
        placement.anchor(anchor);

        float originX = anchor.originX(screenWidth, shownW, slot.margin());
        float originY = anchor.originY(screenHeight, shownH, slot.margin())
                + slot.baseOffsetY() + verticalShift(slot, anchor);
        placement.offset(settle(targetX - originX), settle(targetY - originY));
    }

    private static float settle(float offset) {
        return Math.abs(offset) < SETTLE_THRESHOLD ? 0.0f : offset;
    }

    public static void center(HudSlot slot, float screenWidth, float screenHeight) {
        float shownW = shownWidth[slot.ordinal()];
        float shownH = shownHeight[slot.ordinal()];
        placements.get(slot).dock(null);
        moveTo(slot, (screenWidth - shownW) / 2.0f, (screenHeight - shownH) / 2.0f,
                screenWidth, screenHeight);
    }
}
