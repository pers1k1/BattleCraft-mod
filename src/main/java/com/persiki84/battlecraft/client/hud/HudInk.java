package com.persiki84.battlecraft.client.hud;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiWash;

// WHY: выключатель подменял одну тройку другой прямо в чтении, и худ перекрашивался кадром, пока
// WHY: меню по тому же выключателю переезжает: переезд стоит на своём выходе, как у UiAccent
public final class HudInk {
    private static final float WASH_SECONDS = 0.44f;

    private static final UiWash textWash = new UiWash(WASH_SECONDS, UiTheme.TEXT);
    private static final UiWash dimWash = new UiWash(WASH_SECONDS, UiTheme.TEXT_DIM);
    private static final UiWash faintWash = new UiWash(WASH_SECONDS, UiTheme.TEXT_FAINT);

    private HudInk() {}

    public static void advance(float delta) {
        boolean accent = HudConfig.hudAccentText();
        textWash.aim(accent ? UiAccent.accentText() : UiAccent.text());
        dimWash.aim(accent ? UiAccent.accentTextDim() : UiAccent.textDim());
        faintWash.aim(accent ? UiAccent.accentTextFaint() : UiAccent.textFaint());
        textWash.advance(delta);
        dimWash.advance(delta);
        faintWash.advance(delta);
    }

    public static int text() {
        return textWash.get();
    }

    public static int textDim() {
        return dimWash.get();
    }

    public static int textFaint() {
        return faintWash.get();
    }
}
