package com.persiki84.battlecraft.client.loading;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.ForgeHooksClient;
import org.joml.Matrix4f;

// WHY: фон запуска плоский, а не аврора: аврора рисуется непрозрачным полноэкранным квадом без
// WHY: блендинга, и на затухании она закрыла бы уже открытое главное меню вместо того, чтобы
// WHY: растаять. Плоская подложка того же цвета уходит в прозрачность и меню загорается из темноты
public final class BootPaint {
    private static final float GUI_NEAR = 1000.0f;
    private static final float GONE = 0.004f;

    private static final Matrix4f PROJECTION = new Matrix4f();

    private static boolean framed;

    private BootPaint() {}

    public static int width() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    public static int height() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    // WHY: форджевый оверлей запуска оставляет за собой вьюпорт во весь фреймбуфер и свою матрицу
    // WHY: проекции, поэтому кадр интерфейса собирается заново теми же величинами, что у GameRenderer
    public static void open() {
        if (framed) return;

        framed = true;
        Window window = Minecraft.getInstance().getWindow();
        RenderSystem.viewport(0, 0, window.getWidth(), window.getHeight());
        float far = ForgeHooksClient.getGuiFarPlane();
        PROJECTION.identity().setOrtho(0.0f, (float) (window.getWidth() / window.getGuiScale()),
                (float) (window.getHeight() / window.getGuiScale()), 0.0f, GUI_NEAR, far);
        RenderSystem.setProjectionMatrix(PROJECTION, VertexSorting.ORTHOGRAPHIC_Z);

        PoseStack model = RenderSystem.getModelViewStack();
        model.pushPose();
        model.setIdentity();
        model.translate(0.0f, 0.0f, GUI_NEAR - far);
        RenderSystem.applyModelViewMatrix();
        UiRender.standardBlend();
    }

    // WHY: у кадра запуска один путь свечения от первой рамки до последней: core-шейдеры приходят
    // WHY: вместе с концом перезагрузки ресурсов, и подмена ореола посреди экрана видна глазом
    public static boolean booting() {
        return framed;
    }

    public static void close(GuiGraphics graphics) {
        if (!framed) return;

        framed = false;
        graphics.flush();
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
    }

    public static void paint(GuiGraphics graphics, Object owner, float progress, float shown) {
        int width = width();
        int height = height();
        if (shown <= GONE || width <= 0 || height <= 0) return;

        UiRender.standardBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, shown);
        try {
            UiRender.rect(graphics, 0.0f, 0.0f, width, height,
                    UiTheme.withAlpha(UiPalette.backdrop(), 1.0f));
            LoadingArt.paint(graphics, width, height, null, progress, LoadingClock.seconds(),
                    LoadingClock.age(owner), false);
        } finally {
            graphics.flush();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }
}
