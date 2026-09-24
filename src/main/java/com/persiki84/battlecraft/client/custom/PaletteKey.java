package com.persiki84.battlecraft.client.custom;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiWorldPalette;

import java.util.Locale;
import java.util.function.IntSupplier;

public enum PaletteKey {
    ACCENT(Group.ACCENT, "accent", UiAccent::color, UiAccent::overrideColor),
    TEXT(Group.ACCENT, "text", UiAccent::text, UiAccent::overrideText),
    TEXT_DIM(Group.ACCENT, "textDim", UiAccent::textDim, UiAccent::overrideTextDim),
    TEXT_FAINT(Group.ACCENT, "textFaint", UiAccent::textFaint, UiAccent::overrideTextFaint),
    ALERT(Group.ACCENT, "alert", UiPalette::alert, UiPalette::alert),

    BACKDROP(Group.SURFACE, "backdrop", UiPalette::backdrop, UiPalette::backdrop),
    PANEL(Group.SURFACE, "panel", UiPalette::panel, UiPalette::panel),
    GLASS(Group.GLASS, "glass", UiPalette::glassTop, PaletteKey::glass),
    WELL_TOP(Group.SURFACE, "wellTop", UiPalette::wellTop, PaletteKey::wellTop),
    WELL_BOTTOM(Group.SURFACE, "wellBottom", UiPalette::wellBottom, PaletteKey::wellBottom),
    STROKE(Group.SURFACE, "stroke", UiPalette::stroke, UiPalette::stroke),

    ZONE_FRIENDLY(Group.WORLD, "zoneFriendly", UiWorldPalette::friendly, UiWorldPalette::friendly),
    ZONE_HOSTILE(Group.WORLD, "zoneHostile", UiWorldPalette::hostile, UiWorldPalette::hostile),
    ZONE_SHOP(Group.WORLD, "zoneShop", UiWorldPalette::shop, UiWorldPalette::shop),
    ZONE_CONTESTED(Group.WORLD, "zoneContested", UiWorldPalette::contested, UiWorldPalette::contested),
    MARK(Group.WORLD, "mark", UiWorldPalette::mark, UiWorldPalette::mark),

    DAMAGE(Group.DAMAGE, "damage", UiWorldPalette::damage, UiWorldPalette::damage),
    DAMAGE_CRIT(Group.DAMAGE, "damageCrit", UiWorldPalette::damageCrit, UiWorldPalette::damageCrit);

    public enum Group {
        ACCENT,
        SURFACE,
        GLASS,
        WORLD,
        DAMAGE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean onGlassTab() {
            return this == GLASS;
        }

        public boolean onInterfaceTab() {
            return this == DAMAGE;
        }
    }

    public interface Setter {
        void set(int argb);
    }

    private static final float GLASS_FALL = 0.30f;

    private final Group group;
    private final String id;
    private final IntSupplier live;
    private final Setter setter;

    PaletteKey(Group group, String id, IntSupplier live, Setter setter) {
        this.group = group;
        this.id = id;
        this.live = live;
        this.setter = setter;
    }

    public Group group() {
        return group;
    }

    public String id() {
        return id;
    }

    public int live() {
        return live.getAsInt();
    }

    public void apply(int argb) {
        setter.set(argb);
    }

    public String translationKey() {
        return "battlecraft.custom.color." + id;
    }

    public String hintKey() {
        return translationKey() + ".hint";
    }

    public static PaletteKey byId(String id) {
        for (PaletteKey key : values()) {
            if (key.id.equals(id)) return key;
        }
        return null;
    }

    // WHY: низ стекла темнее верха на заводскую долю, и один выбранный цвет обязан эту разницу сохранить
    private static void glass(int argb) {
        UiPalette.glass(argb, UiTheme.mix(argb, UiTheme.BLACK, GLASS_FALL));
    }

    private static void wellTop(int argb) {
        UiPalette.well(argb, UiPalette.wellBottom());
    }

    private static void wellBottom(int argb) {
        UiPalette.well(UiPalette.wellTop(), argb);
    }
}
