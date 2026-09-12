package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = "battlecraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class UiCarry {
    private static final ResourceLocation SHADER = new ResourceLocation("battlecraft", "ui_carry");
    private static final Matrix4f FLAT = new Matrix4f();
    private static final Matrix3f STILL = new Matrix3f();

    private static ShaderInstance carryShader;

    private UiCarry() {}

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.POSITION_TEX),
                shader -> carryShader = shader);
    }

    // WHY: копия кадра в сцене обязана лежать уже перенесённой на мировую плоскость, иначе стекло,
    // WHY: рисуемое поверх пустого места, читает мир по экранной координате и тот едет за камерой
    public static boolean paint(int source, UiPlane carrier, UiShards shards, float phase,
                                float width, float height) {
        if (carryShader == null || source == 0) return false;
        if (carrier == null && shards == null) return false;

        Matrix4f projection = RenderSystem.getProjectionMatrix();
        VertexSorting sorting = RenderSystem.getVertexSorting();
        begin(source, carrier);
        try {
            UiQuad.screen();
            if (shards != null) field(shards, phase, width, height);
        } finally {
            end(projection, sorting);
        }
        return true;
    }

    // WHY: осколок улетает со своего места, и мир за его стеклом обязан приходить оттуда, куда он
    // WHY: улетел. Сместить сэмпл в шейдерах стекла нельзя - смещение своё у каждой ячейки, поэтому
    // WHY: смещается сам снимок: ячейка пишется на своё исходное место, а читается с полётного
    private static void field(UiShards shards, float phase, float width, float height) {
        if (width <= 0.0f || height <= 0.0f) return;

        RenderSystem.disableCull();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_TEX);
        for (int index = 0; index < shards.count(); index++) {
            cell(builder, shards, index, phase, width, height);
        }
        Tesselator.getInstance().end();
        RenderSystem.enableCull();
    }

    private static void cell(BufferBuilder builder, UiShards shards, int index, float phase,
                             float width, float height) {
        int corners = shards.corners(index);
        if (corners < 3) return;

        UiShards.Flight flight = shards.flight(index, phase);
        for (int corner = 1; corner < corners - 1; corner++) {
            vertex(builder, shards, index, 0, flight, width, height);
            vertex(builder, shards, index, corner, flight, width, height);
            vertex(builder, shards, index, corner + 1, flight, width, height);
        }
    }

    private static void vertex(BufferBuilder builder, UiShards shards, int index, int corner,
                               UiShards.Flight flight, float width, float height) {
        float pointX = shards.cornerX(index, corner);
        float pointY = shards.cornerY(index, corner);
        builder.vertex(pointX / width * 2.0f - 1.0f, 1.0f - pointY / height * 2.0f, 0.0f)
                .uv(flight.x(pointX, pointY) / width, 1.0f - flight.y(pointX, pointY) / height)
                .endVertex();
    }

    private static void begin(int source, UiPlane carrier) {
        RenderSystem.setProjectionMatrix(FLAT, VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack stack = RenderSystem.getModelViewStack();
        stack.pushPose();
        stack.setIdentity();
        RenderSystem.applyModelViewMatrix();

        RenderSystem.disableDepthTest();
        UiRender.standardBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        carryShader.safeGetUniform("CarryWarp").set(carrier == null ? STILL : carrier.warp());
        RenderSystem.setShader(() -> carryShader);
        RenderSystem.setShaderTexture(0, source);
    }

    private static void end(Matrix4f projection, VertexSorting sorting) {
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();

        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(projection, sorting);
    }

}
