package com.persiki84.battlecraft.client.custom;

public final class HudDock {
    private final HudSlot target;
    private final HudSide side;
    private final HudAlign align;

    public HudDock(HudSlot target, HudSide side, HudAlign align) {
        this.target = target;
        this.side = side;
        this.align = align == null ? HudAlign.FREE : align;
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
