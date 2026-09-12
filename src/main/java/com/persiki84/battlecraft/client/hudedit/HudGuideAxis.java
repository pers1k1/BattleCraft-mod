package com.persiki84.battlecraft.client.hudedit;

import com.persiki84.battlecraft.client.custom.HudAlign;
import com.persiki84.battlecraft.client.custom.HudSide;
import com.persiki84.battlecraft.client.custom.HudSlot;

public final class HudGuideAxis {
    private static final float REACH = 5.0f;

    private float origin;
    private float position;
    private float line;
    private float distance;
    private HudGuideKind kind = HudGuideKind.NONE;
    private HudSlot target;
    private HudSide side;
    private HudAlign align = HudAlign.FREE;

    public void reset(float value) {
        origin = value;
        position = value;
        line = 0.0f;
        distance = REACH;
        kind = HudGuideKind.NONE;
        target = null;
        side = null;
        align = HudAlign.FREE;
    }

    public void offer(HudGuideKind offered, float guide, float placed,
                      HudSlot other, HudSide dockSide, HudAlign edge) {
        float apart = Math.abs(placed - origin);
        if (apart > distance) return;
        if (apart == distance && kind == HudGuideKind.DOCK) return;

        distance = apart;
        position = placed;
        line = guide;
        kind = offered;
        target = other;
        side = dockSide;
        align = edge;
    }

    public float position() {
        return position;
    }

    public float line() {
        return line;
    }

    public HudGuideKind kind() {
        return kind;
    }

    public HudSlot target() {
        return target;
    }

    public HudSide side() {
        return side;
    }

    public HudAlign align() {
        return align;
    }
}
