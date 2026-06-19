package com.persiki84.dmgndctr.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class TextParticle extends Particle {
    private final String text;

    public TextParticle(ClientLevel level, double x, double y, double z, float damage, boolean isCrit) {
        super(level, x, y, z);
        this.gravity = 0.04F;
        this.lifetime = 30;
        this.xd = 0;
        this.yd = 0.1;
        this.zd = 0;

        String color = isCrit ? "\u00A76" : "\u00A7c";
        this.text = color + "\u2764 " + String.format("%.1f", damage);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 camPos = camera.getPosition();
        float x = (float) (Mth.lerp(partialTicks, this.xo, this.x) - camPos.x());
        float y = (float) (Mth.lerp(partialTicks, this.yo, this.y) - camPos.y());
        float z = (float) (Mth.lerp(partialTicks, this.zo, this.z) - camPos.z());

        Quaternionf rotation = camera.rotation();

        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
        Font font = mc.font;

        float scale = 0.04F;
        int alpha = 255;
        if (this.age > this.lifetime - 10) {
            alpha = (int) (((float)(this.lifetime - this.age) / 10.0F) * 255.0F);
        }
        int packedColor = (alpha << 24) | 0xFFFFFF;

        com.mojang.blaze3d.vertex.PoseStack poseStack = new com.mojang.blaze3d.vertex.PoseStack();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(rotation);
        poseStack.scale(-scale, -scale, scale);

        float width = font.width(text);
        font.drawInBatch(text, -width / 2, 0, packedColor, true, poseStack.last().pose(), source, Font.DisplayMode.NORMAL, 0, 15728880);

        poseStack.popPose();
        source.endBatch();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.CUSTOM;
    }
}
