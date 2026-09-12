package com.persiki84.battlecraft.client.hudedit;

import com.persiki84.battlecraft.client.custom.HudAlign;
import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.battlecraft.client.custom.HudDock;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudSide;
import com.persiki84.battlecraft.client.custom.HudSlot;

public final class HudGuides {
    private static final HudGuideAxis horizontal = new HudGuideAxis();
    private static final HudGuideAxis vertical = new HudGuideAxis();

    private HudGuides() {}

    public static HudGuideAxis across() {
        return horizontal;
    }

    public static HudGuideAxis down() {
        return vertical;
    }

    public static void clear() {
        horizontal.reset(0.0f);
        vertical.reset(0.0f);
    }

    public static void solve(HudSlot slot, float x, float y, float width, float height,
                             float screenWidth, float screenHeight) {
        horizontal.reset(x);
        vertical.reset(y);
        offerFrame(horizontal, width, screenWidth);
        offerFrame(vertical, height, screenHeight);
        for (HudSlot other : HudSlot.values()) {
            if (other == slot || !HudLayout.drawn(other) || !HudLayout.visible(other)) continue;
            offerNeighbour(other, width, height);
        }
    }

    private static void offerFrame(HudGuideAxis axis, float span, float screen) {
        float start = HudLayout.SAFE_MARGIN;
        float end = screen - HudLayout.SAFE_MARGIN;
        float third = (end - start) / 3.0f;
        offerLine(axis, start, span);
        offerLine(axis, start + third, span);
        offerLine(axis, end - third, span);
        offerLine(axis, end, span);
    }

    private static void offerLine(HudGuideAxis axis, float guide, float span) {
        axis.offer(HudGuideKind.GRID, guide, guide, null, null, HudAlign.START);
        axis.offer(HudGuideKind.GRID, guide, guide - span / 2.0f, null, null, HudAlign.CENTER);
        axis.offer(HudGuideKind.GRID, guide, guide - span, null, null, HudAlign.END);
    }

    private static void offerNeighbour(HudSlot other, float width, float height) {
        HudBox box = HudLayout.box(other);
        float spanX = box.width() * box.scale();
        float spanY = box.height() * box.scale();
        offerAlign(horizontal, other, box.x(), spanX, width);
        offerAlign(vertical, other, box.y(), spanY, height);
        offerDock(horizontal, other, box.x(), spanX, width, HudSide.LEFT, HudSide.RIGHT);
        offerDock(vertical, other, box.y(), spanY, height, HudSide.TOP, HudSide.BOTTOM);
    }

    private static void offerAlign(HudGuideAxis axis, HudSlot other, float origin, float span, float own) {
        axis.offer(HudGuideKind.ALIGN, origin, origin, other, null, HudAlign.START);
        axis.offer(HudGuideKind.ALIGN, origin + span / 2.0f, origin + (span - own) / 2.0f,
                other, null, HudAlign.CENTER);
        axis.offer(HudGuideKind.ALIGN, origin + span, origin + span - own, other, null, HudAlign.END);
    }

    private static void offerDock(HudGuideAxis axis, HudSlot other, float origin, float span, float own,
                                  HudSide before, HudSide after) {
        float ahead = origin - HudLayout.DOCK_GAP;
        float behind = origin + span + HudLayout.DOCK_GAP;
        axis.offer(HudGuideKind.DOCK, ahead, ahead - own, other, before, HudAlign.FREE);
        axis.offer(HudGuideKind.DOCK, behind, behind, other, after, HudAlign.FREE);
    }

    public static HudDock dock() {
        if (horizontal.kind() == HudGuideKind.DOCK) {
            return new HudDock(horizontal.target(), horizontal.side(), crossAlign(vertical, horizontal.target()));
        }
        if (vertical.kind() == HudGuideKind.DOCK) {
            return new HudDock(vertical.target(), vertical.side(), crossAlign(horizontal, vertical.target()));
        }
        return null;
    }

    private static HudAlign crossAlign(HudGuideAxis axis, HudSlot target) {
        if (axis.kind() != HudGuideKind.ALIGN || axis.target() != target) return HudAlign.FREE;
        return axis.align();
    }
}
