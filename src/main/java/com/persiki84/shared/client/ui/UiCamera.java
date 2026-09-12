package com.persiki84.shared.client.ui;

import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class UiCamera {
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW = new Matrix4f();
    private static final float HALF_TURN = 180.0f;
    private static final float DEFAULT_TANGENT = 0.7002f;
    private static final float THIN_SCALE = 0.05f;

    private static double eyeX;
    private static double eyeY;
    private static double eyeZ;
    private static long stamp = -1L;

    private UiCamera() {}

    // WHY: ванильный вид это XP(pitch) затем YP(yaw + 180), собираем той же парой, а не через Camera.rotation
    public static void observe(Matrix4f projection, Camera camera) {
        PROJECTION.set(projection);
        VIEW.identity()
                .rotate(Axis.XP.rotationDegrees(camera.getXRot()))
                .rotate(Axis.YP.rotationDegrees(camera.getYRot() + HALF_TURN));

        Vec3 eye = camera.getPosition();
        eyeX = eye.x;
        eyeY = eye.y;
        eyeZ = eye.z;
        stamp = UiFrame.frame();
    }

    public static boolean fresh() {
        return stamp == UiFrame.frame();
    }

    // WHY: у матрицы игры кроме перспективы лежат качание головы и правки чужих модов камеры, поэтому
    // WHY: сама она для построения плоскости не годится; из неё берётся только раствор объектива
    public static float fovTangent() {
        float scale = Math.abs(PROJECTION.m11());
        return scale < THIN_SCALE ? DEFAULT_TANGENT : 1.0f / scale;
    }

    public static Matrix4f view() {
        return VIEW;
    }

    public static double eyeX() {
        return eyeX;
    }

    public static double eyeY() {
        return eyeY;
    }

    public static double eyeZ() {
        return eyeZ;
    }
}
