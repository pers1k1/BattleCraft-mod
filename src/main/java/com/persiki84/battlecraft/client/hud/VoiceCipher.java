package com.persiki84.battlecraft.client.hud;

import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.network.chat.Component;

// WHY: соперник на нашей частоте слышен, но не назван: вместо ника идёт дрожащий набор знаков.
// WHY: длина у него постоянная, иначе карточка дёргалась бы шириной на каждой смене символа
public final class VoiceCipher implements UiRender.GlyphTone {
    private static final char[] ALPHABET = "#?%&*@!$~+=".toCharArray();
    private static final int LENGTH = 7;
    private static final long CHURN_MS = 110L;
    private static final float DANCE = 0.55f;
    private static final float FLICKER = 0.34f;

    private final char[] glyphs = new char[LENGTH];
    private final StringBuilder builder = new StringBuilder(LENGTH);

    private Component shown;
    private long churnedAt;
    private long stamp;
    private int noise = 0x2545F491;

    // WHY: строка и её Component пересобираются раз в сто миллисекунд, а не в кадре: перебор
    // WHY: знаков это анимация, а аллокация на кадр в худе запрещена
    public Component text(long now) {
        stamp = now;
        if (shown != null && now - churnedAt < CHURN_MS) return shown;

        boolean fresh = shown == null;
        churnedAt = now;
        if (fresh) {
            for (int index = 0; index < LENGTH; index++) {
                glyphs[index] = ALPHABET[next(ALPHABET.length)];
            }
        } else {
            glyphs[next(LENGTH)] = ALPHABET[next(ALPHABET.length)];
            glyphs[next(LENGTH)] = ALPHABET[next(ALPHABET.length)];
        }

        builder.setLength(0);
        builder.append(glyphs, 0, LENGTH);
        shown = Component.literal(builder.toString());
        return shown;
    }

    public void forget() {
        shown = null;
        churnedAt = 0L;
    }

    // WHY: фаза считается от остатка времени, а не от самой метки: миллисекунды эпохи не лезут
    // WHY: в мантиссу float, и анимация встаёт ступеньками по секунде
    @Override
    public int tint(int index, int count, int base) {
        float phase = (float) (stamp % 100000L) / 190.0f + index * 1.7f;
        float shade = 1.0f - FLICKER * (0.5f + 0.5f * (float) Math.sin(phase));
        return UiTheme.alpha(base, shade);
    }

    @Override
    public float rise(int index, int count) {
        return DANCE * (float) Math.sin((float) (stamp % 100000L) / 120.0f + index * 2.3f);
    }

    private int next(int bound) {
        noise ^= noise << 13;
        noise ^= noise >>> 17;
        noise ^= noise << 5;
        return (noise >>> 8) % bound;
    }
}
