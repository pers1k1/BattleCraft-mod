package com.persiki84.battlecraft.client.menu.custom;

import com.persiki84.battlecraft.client.custom.PaletteKey;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;

// WHY: цвет правится там, где он виден: образец в предпросмотре отмечает места своих цветов, и щелчок
// WHY: по такому месту открывает палитру этого цвета. Места копятся в массивах заново каждый кадр,
// WHY: поэтому отметка всегда совпадает с тем, что нарисовано, и кадр ничего не аллоцирует
final class PreviewHits {
    private static final int LIMIT = 16;
    private static final float HOVER_SPEED = 18.0f;
    private static final float PAD = 2.0f;

    private final float[] left = new float[LIMIT];
    private final float[] top = new float[LIMIT];
    private final float[] right = new float[LIMIT];
    private final float[] bottom = new float[LIMIT];
    private final PaletteKey[] keys = new PaletteKey[LIMIT];
    private final Smooth[] lit = new Smooth[LIMIT];
    private int count;

    public PreviewHits() {
        for (int index = 0; index < LIMIT; index++) {
            lit[index] = new Smooth(0.0f, HOVER_SPEED);
        }
    }

    void clear() {
        count = 0;
    }

    void add(float x, float y, float width, float height, PaletteKey key) {
        if (count >= LIMIT) return;
        left[count] = x - PAD;
        top[count] = y - PAD;
        right[count] = x + width + PAD;
        bottom[count] = y + height + PAD;
        keys[count] = key;
        count++;
    }

    PaletteKey at(double mouseX, double mouseY) {
        for (int index = count - 1; index >= 0; index--) {
            if (mouseX >= left[index] && mouseX < right[index] && mouseY >= top[index] && mouseY < bottom[index]) {
                return keys[index];
            }
        }
        return null;
    }

    void paint(GuiGraphics graphics, int mouseX, int mouseY, float alpha) {
        PaletteKey pointed = at(mouseX, mouseY);
        float delta = UiFrame.delta();
        for (int index = 0; index < count; index++) {
            float glow = lit[index].to(keys[index] == pointed ? 1.0f : 0.0f, delta);
            if (glow <= 0.01f) continue;
            float width = right[index] - left[index];
            float height = bottom[index] - top[index];
            float radius = Math.min(6.0f, height / 2.0f);
            UiRender.rim(graphics, left[index], top[index], width, height, radius, 1.0f,
                    UiTheme.alpha(UiAccent.color(), 0.75f * glow * alpha));
        }
    }
}
