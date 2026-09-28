package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.persiki84.shared.client.font.MsdfShaders;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;

// WHY: текст кладётся на пиксельную сетку покоя, а не текущего кадра. Ход, заявленный здесь,
// WHY: едет поверх привязки непрерывно, как геометрия, и в конце приходит ровно в свою клетку:
// WHY: привязка к сетке кадра вела строку ступенями по пикселю и дёргала её в начале и конце хода
public final class UiRestFrame {
    private static final int DEPTH = 64;
    private static final int SLOT = 4;
    private static final float[] SAVED = new float[DEPTH * SLOT];
    private static final float FLAT = 1.0E-4f;

    private static int depth;
    private static float stretchX = 1.0f;
    private static float stretchY = 1.0f;
    private static float shiftX;
    private static float shiftY;

    private UiRestFrame() {}

    public static void shift(GuiGraphics graphics, float moveX, float moveY) {
        push(graphics, 0.0f, 0.0f, 1.0f, 1.0f, moveX, moveY);
    }

    public static void push(GuiGraphics graphics, float pivotX, float pivotY, float scaleX, float scaleY,
                            float moveX, float moveY) {
        if (save()) compose(graphics.pose().last().pose(), pivotX, pivotY, scaleX, scaleY, moveX, moveY);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(pivotX + moveX, pivotY + moveY, 0.0f);
        pose.scale(scaleX, scaleY, 1.0f);
        pose.translate(-pivotX, -pivotY, 0.0f);
    }

    public static void pop(GuiGraphics graphics) {
        graphics.pose().popPose();
        restore();
    }

    // WHY: ход без сдвига позы: строку уже нарисуют в живой точке, а заявка только говорит,
    // WHY: где её место покоя. Так бегущая строка стоит на сетке покоя и едет без ступеней
    public static void declare(GuiGraphics graphics, float moveX, float moveY) {
        if (save()) compose(graphics.pose().last().pose(), 0.0f, 0.0f, 1.0f, 1.0f, moveX, moveY);
    }

    public static void retract() {
        restore();
    }

    public static float stretchX() {
        return Math.abs(stretchX) > FLAT ? Math.abs(stretchX) : 1.0f;
    }

    static void lend(double gui) {
        if (still() || Math.abs(stretchX) <= FLAT || Math.abs(stretchY) <= FLAT) return;
        MsdfShaders.move(stretchX, stretchY, shiftX, shiftY, (float) gui);
    }

    static void reclaim() {
        MsdfShaders.settle();
    }

    private static boolean still() {
        return stretchX == 1.0f && stretchY == 1.0f && shiftX == 0.0f && shiftY == 0.0f;
    }

    static float settleX(float value, Matrix4f matrix, double gui) {
        return settle(value, matrix.m00(), matrix.m30(), stretchX, shiftX, gui);
    }

    static float settleY(float value, Matrix4f matrix, double gui) {
        return settle(value, matrix.m11(), matrix.m31(), stretchY, shiftY, gui);
    }

    // WHY: ход в экранных координатах это растяжение и сдвиг по каждой оси, поэтому место покоя
    // WHY: снимается обратным ходом, ставится на целый пиксель и возвращается тем же ходом
    private static float settle(float value, float unit, float offset, float stretch, float shift, double gui) {
        if (Math.abs(unit) <= FLAT || Math.abs(stretch) <= FLAT) return value;

        double live = (double) value * unit + offset;
        double rest = (live - shift) / stretch;
        double snapped = Math.round(rest * gui) / gui;
        return (float) ((snapped * stretch + shift - offset) / unit);
    }

    // WHY: вложенный ход считается от уже сдвинутой позы, поэтому в экранных координатах он
    // WHY: применяется поверх накопленного: растяжения перемножаются, сдвиги растягиваются
    private static void compose(Matrix4f matrix, float pivotX, float pivotY, float scaleX, float scaleY,
                                float moveX, float moveY) {
        float anchorX = matrix.m00() * pivotX + matrix.m30();
        float anchorY = matrix.m11() * pivotY + matrix.m31();
        shiftX = scaleX * shiftX + (1.0f - scaleX) * anchorX + matrix.m00() * moveX;
        shiftY = scaleY * shiftY + (1.0f - scaleY) * anchorY + matrix.m11() * moveY;
        stretchX *= scaleX;
        stretchY *= scaleY;
    }

    private static boolean save() {
        depth++;
        if (depth > DEPTH) return false;

        int at = (depth - 1) * SLOT;
        SAVED[at] = stretchX;
        SAVED[at + 1] = stretchY;
        SAVED[at + 2] = shiftX;
        SAVED[at + 3] = shiftY;
        return true;
    }

    private static void restore() {
        if (depth == 0) return;

        depth--;
        if (depth >= DEPTH) return;

        int at = depth * SLOT;
        stretchX = SAVED[at];
        stretchY = SAVED[at + 1];
        shiftX = SAVED[at + 2];
        shiftY = SAVED[at + 3];
    }
}
