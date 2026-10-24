package com.persiki84.battlecraft.client.roster;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTicker;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

final class RosterGroup {
    enum Kind { TEAM, LONERS, WATCHERS }

    // WHY: у группы без цвета команды тон берётся из акцента в момент отрисовки: акцент переезжает
    // WHY: плавно, и защёлкнутый в тике цвет отставал бы от остального интерфейса
    static final int ACCENT = 0;

    private static final float GLIDE_SPEED = 9.0f;
    private static final float FADE_SPEED = 12.0f;

    final Kind kind;
    final String key;
    final List<RosterRow> members = new ArrayList<>();
    final UiTicker count = new UiTicker().align(UiTicker.RIGHT);
    final Smooth x = new Smooth(0.0f, GLIDE_SPEED);
    final Smooth y = new Smooth(0.0f, GLIDE_SPEED);
    final Smooth width = new Smooth(0.0f, GLIDE_SPEED);
    final Smooth height = new Smooth(0.0f, GLIDE_SPEED);
    final Smooth presence;

    private Component title = Component.empty();
    private int color = ACCENT;
    private float targetX;
    private float targetY;
    private float targetWidth;
    private float targetHeight;
    private boolean listed;

    public RosterGroup(Kind kind, String key, boolean fresh) {
        this.kind = kind;
        this.key = key;
        this.presence = new Smooth(fresh ? 0.0f : 1.0f, FADE_SPEED);
    }

    public void open(Component nextTitle, int nextColor) {
        if (nextTitle != title) title = nextTitle;
        color = nextColor;
        members.clear();
        listed = true;
    }

    public void unlist() {
        members.clear();
        listed = false;
    }

    public void aim(float nextX, float nextY, float nextWidth, float nextHeight) {
        if (presence.get() <= RosterRow.GONE) {
            x.snap(nextX);
            y.snap(nextY);
            width.snap(nextWidth);
            height.snap(nextHeight);
        }
        targetX = nextX;
        targetY = nextY;
        targetWidth = nextWidth;
        targetHeight = nextHeight;
    }

    public void settle() {
        x.snap(targetX);
        y.snap(targetY);
        width.snap(targetWidth);
        height.snap(targetHeight);
        presence.snap(listed ? 1.0f : 0.0f);
    }

    public boolean advance(float delta) {
        x.to(targetX, delta);
        y.to(targetY, delta);
        width.to(targetWidth, delta);
        height.to(targetHeight, delta);
        if (listed) count.set(members.size());
        float shown = presence.to(listed ? 1.0f : 0.0f, delta);
        return listed || shown > RosterRow.GONE;
    }

    public Component title() {
        return title;
    }

    public int color() {
        return color == ACCENT ? UiAccent.faint() : color;
    }

    public boolean listed() {
        return listed;
    }
}
