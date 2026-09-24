package com.persiki84.battlecraft.client.menu.browse;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;

// WHY: метка выбора одна на список и переезжает к новой карточке, а не гаснет в одной и загорается
// WHY: в другой: голова едет быстрее хвоста, и по пути метка вытягивается, как капля
final class PickMark {
    private static final float WIDTH = 2.2f;
    private static final float INSET = 3.5f;
    private static final float HEAD_SPEED = 21.0f;
    private static final float TAIL_SPEED = 10.0f;
    private static final float FADE_SPEED = 13.0f;
    private static final float PEAK_ALPHA = 0.98f;
    private static final float SNAP_BELOW = 0.05f;
    private static final float GONE = 0.01f;

    private final Smooth head = new Smooth(0.0f, HEAD_SPEED);
    private final Smooth tail = new Smooth(0.0f, TAIL_SPEED);
    private final Smooth shown = new Smooth(0.0f, FADE_SPEED);
    private BrowseCard followed;
    private float followedTop;

    void carry(BrowseCard from, BrowseCard to) {
        if (followed == from) followed = to;
    }

    void render(GuiGraphics graphics, BrowseCard target, float left, float span, float clipTop, float clipBottom) {
        float delta = UiFrame.delta();
        float before = shown.get();
        float alpha = shown.to(target == null ? 0.0f : 1.0f, delta);
        if (target != null) aim(target.shownTop() + INSET, target, before, delta);
        if (alpha <= GONE) return;

        float top = Math.max(clipTop, Math.min(head.get(), tail.get()));
        float bottom = Math.min(clipBottom, Math.max(head.get(), tail.get()) + span - INSET * 2.0f);
        if (bottom <= top) return;

        UiRender.panel(graphics, left + INSET, top, WIDTH, bottom - top, WIDTH / 2.0f,
                UiTheme.alpha(UiAccent.color(), alpha * PEAK_ALPHA));
    }

    // WHY: прокрутка двигает ту же карточку, и метка обязана ехать с ней вплотную, а не догонять:
    // WHY: догоняющая метка отставала бы от карточки на каждом шаге колеса
    private void aim(float top, BrowseCard target, float before, float delta) {
        if (target == followed) {
            shift(top - followedTop);
        } else if (followed == null || before < SNAP_BELOW) {
            head.snap(top);
            tail.snap(top);
        }
        followed = target;
        followedTop = top;
        head.to(top, delta);
        tail.to(top, delta);
    }

    private void shift(float distance) {
        head.snap(head.get() + distance);
        tail.snap(tail.get() + distance);
    }
}
