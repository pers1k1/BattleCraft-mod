package com.persiki84.battlecraft.client.island;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.shared.client.ui.UiAnim;

// WHY: обложка не гаснет и не появляется заново, а поворачивается: лицевая сторона это уходящий
// WHY: трек, обратная это пришедший, поэтому у переворота одна доля хода на обе стороны
public final class IslandFlip {
    private static final float TURN_SECONDS = 1.05f;
    private static final float SLOWEST = 0.05f;
    private static final float MASH_FULL = 0.72f;
    private static final float MASH_END = 0.97f;
    private static final float SEED_STEP = 13.7f;
    private static final float SEED_DRIFT = 0.8f;

    private static float phase = 1.0f;
    private static float way = 1.0f;
    private static float seed;

    private IslandFlip() {}

    public static void begin(int direction) {
        way = direction < 0 ? -1.0f : 1.0f;
        phase = 0.0f;
        seed += SEED_STEP;
    }

    public static void advance(float delta) {
        if (phase >= 1.0f) return;

        phase = Math.min(1.0f, phase + delta / seconds());
    }

    private static float seconds() {
        return TURN_SECONDS / Math.max(SLOWEST, HudConfig.islandFlipSpeed());
    }

    public static boolean turning() {
        return phase < 1.0f;
    }

    public static float angle() {
        return way * (float) Math.PI * turned();
    }

    public static boolean fresh() {
        return turned() >= 0.5f;
    }

    // WHY: замес живёт только на обратной стороне: держится целиком, пока карточка раскрывается,
    // WHY: и сходит сглаженным спадом раньше конца разворота. Ровно к единице гасить нельзя - на
    // WHY: выходе из шейдера в обычную кисть обложка щёлкала бы из размытой в резкую, а линейный
    // WHY: спад давал бы обратный щелчок в самом конце своей ненулевой производной
    public static float mash() {
        return 1.0f - UiAnim.smoothstep(MASH_FULL, MASH_END, turned());
    }

    public static float seed() {
        return seed + phase * SEED_DRIFT;
    }

    public static void forget() {
        phase = 1.0f;
    }

    private static float turned() {
        return UiAnim.smoothstep(0.0f, 1.0f, phase);
    }
}
