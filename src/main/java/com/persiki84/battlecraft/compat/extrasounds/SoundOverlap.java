package com.persiki84.battlecraft.compat.extrasounds;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.shared.client.ui.UiCue;
import com.persiki84.shared.client.ui.UiSound;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

// WHY: ExtraSounds озвучивает те же действия, что и мы, и оба сигнала звучат разом. Свой звук
// WHY: главнее, чужой остаётся там, где своего нет, поэтому список пересечений задан поимённо
public final class SoundOverlap {
    private static final String NAMESPACE = "extrasounds";

    private static final Set<String> SCREEN_ACTIONS = Set.of("inventory.open", "inventory.close");
    private static final Set<String> HOTBAR_ACTIONS = Set.of("hotbar_scroll");
    private static final Set<String> EFFECT_ACTIONS = Set.of(
            "effect.add.positive", "effect.add.negative",
            "effect.remove.positive", "effect.remove.negative");

    private SoundOverlap() {}

    public static boolean hotbarSwitch() {
        return UiSound.audible(UiCue.SLOT);
    }

    public static boolean covers(ResourceLocation sound) {
        if (sound == null || !NAMESPACE.equals(sound.getNamespace())) return false;

        String action = sound.getPath();
        if (SCREEN_ACTIONS.contains(action)) return UiSound.audible(UiCue.SCREEN_OPEN);
        if (HOTBAR_ACTIONS.contains(action)) return hotbarSwitch();
        if (EFFECT_ACTIONS.contains(action)) return effectChange();
        return false;
    }

    private static boolean effectChange() {
        return HudConfig.effectChips() && UiSound.audible(UiCue.CHIP_UP);
    }
}
