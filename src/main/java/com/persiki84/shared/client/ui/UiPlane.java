package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class UiPlane {
    private static final float DISTANCE = 2.0f;
    private static final float NEAR = 0.05f;
    private static final float FAR = 512.0f;
    private static final float FLAT_LIMIT = 1.0E-6f;

    private static boolean allowed = true;
    private static UiPlane sampling;

    private final Matrix4f perspective = new Matrix4f();
    private final Matrix4f basis = new Matrix4f();
    private final Matrix4f model = new Matrix4f();
    private final Matrix4f full = new Matrix4f();
    private final Matrix4f saved = new Matrix4f();
    private final Matrix3f clipToStage = new Matrix3f();
    private final Matrix3f stageToGui = new Matrix3f();
    private final Matrix3f screenMap = new Matrix3f();
    private final Matrix3f warp = new Matrix3f();
    private final Vector3f carried = new Vector3f();

    private final float width;
    private final float height;
    private final float unit;
    private final double anchorX;
    private final double anchorY;
    private final double anchorZ;

    private VertexSorting sorting;

    public static UiPlane capture(float width, float height) {
        if (!UiCamera.fresh() || width <= 0.0f || height <= 0.0f) return null;

        Matrix4f lens = lensFor(width, height);
        float unit = unitFor(lens, height);
        return unit <= 0.0f ? null : new UiPlane(width, height, unit, lens);
    }

    // WHY: своя перспектива на пропорциях интерфейса, а не окна: прямоугольник экрана обязан лечь
    // WHY: в кадр один в один, и это должно держаться построением, а не подгонкой меры
    private static Matrix4f lensFor(float width, float height) {
        float opening = 2.0f * (float) Math.atan(UiCamera.fovTangent());
        return new Matrix4f().setPerspective(opening, width / height, NEAR, FAR);
    }

    public static void allow(boolean value) {
        allowed = value;
    }

    public static boolean allowed() {
        return allowed;
    }

    // WHY: пока идёт перерисовка горящего экрана, стекло обязано брать подложку с той точки кадра,
    // WHY: куда панель проецируется сейчас, а не с той, где её сняли
    public static void sampling(UiPlane value) {
        sampling = value;
    }

    public static UiPlane sampling() {
        return sampling;
    }

    private UiPlane(float width, float height, float unit, Matrix4f lens) {
        this.width = width;
        this.height = height;
        this.unit = unit;
        perspective.set(lens);
        basis.set(UiCamera.view()).invert();
        clipToStage.set(0.5f, 0.0f, 0.0f, 0.0f, 0.5f, 0.0f, 0.5f, 0.5f, 1.0f);
        stageToGui.set(width, 0.0f, 0.0f, 0.0f, -height, 0.0f, 0.0f, height, 1.0f);

        Vector3f ahead = basis.transformDirection(new Vector3f(0.0f, 0.0f, -DISTANCE));
        anchorX = UiCamera.eyeX() + ahead.x;
        anchorY = UiCamera.eyeY() + ahead.y;
        anchorZ = UiCamera.eyeZ() + ahead.z;
    }

    // WHY: единица интерфейса по вертикали это 2/height в NDC, поэтому мера берётся замером самой проекции
    private static float unitFor(Matrix4f projection, float height) {
        Vector4f base = projection.transform(new Vector4f(0.0f, 0.0f, -DISTANCE, 1.0f));
        Vector4f lifted = projection.transform(new Vector4f(0.0f, 1.0f, -DISTANCE, 1.0f));
        if (Math.abs(base.w) < 1.0E-6f || Math.abs(lifted.w) < 1.0E-6f) return 0.0f;

        float span = lifted.y / lifted.w - base.y / base.w;
        return Math.abs(span) < 1.0E-9f ? 0.0f : 2.0f / (height * span);
    }

    // WHY: панели висят на разном удалении, а глубину композит не пишет, поэтому порядок рисования
    // WHY: задаёт удаление от глаза: дальняя ложится первой, ближняя накрывает её
    public float away() {
        double runX = anchorX - UiCamera.eyeX();
        double runY = anchorY - UiCamera.eyeY();
        double runZ = anchorZ - UiCamera.eyeZ();
        return (float) (runX * runX + runY * runY + runZ * runZ);
    }

    public boolean advance() {
        if (!UiCamera.fresh()) return false;

        model.set(UiCamera.view())
                .translate((float) (anchorX - UiCamera.eyeX()),
                        (float) (anchorY - UiCamera.eyeY()),
                        (float) (anchorZ - UiCamera.eyeZ()))
                .mul(basis)
                .scale(unit, -unit, unit)
                .translate(-width / 2.0f, -height / 2.0f, 0.0f);

        full.set(perspective).mul(model);
        remap();
        return true;
    }

    // WHY: столбцы и строки 0, 1, 3 полной матрицы это гомография плоскости z = 0; clipToStage переводит
    // WHY: клип в доли кадра, stageToGui - долю кадра в точку интерфейса, и вместе это перенос сэмпла
    private void remap() {
        screenMap.set(full.m00(), full.m01(), full.m03(),
                full.m10(), full.m11(), full.m13(),
                full.m30(), full.m31(), full.m33());
        screenMap.mulLocal(clipToStage);
        warp.set(screenMap).mul(stageToGui);
    }

    public Matrix3f warp() {
        return warp;
    }

    public boolean sample(float screenX, float screenY, Vector2f dest) {
        screenMap.transform(screenX, screenY, 1.0f, carried);
        if (Math.abs(carried.z) < FLAT_LIMIT) return false;

        dest.set(carried.x / carried.z, carried.y / carried.z);
        return true;
    }

    public boolean bind(GuiGraphics graphics) {
        if (!advance()) return false;

        graphics.flush();
        saved.set(RenderSystem.getProjectionMatrix());
        sorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(perspective, VertexSorting.DISTANCE_TO_ORIGIN);

        PoseStack view = RenderSystem.getModelViewStack();
        view.pushPose();
        view.setIdentity();
        RenderSystem.applyModelViewMatrix();

        graphics.pose().pushPose();
        graphics.pose().setIdentity();
        graphics.pose().mulPoseMatrix(model);
        return true;
    }

    public void release(GuiGraphics graphics) {
        graphics.flush();
        graphics.pose().popPose();

        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(saved, sorting);
    }
}
