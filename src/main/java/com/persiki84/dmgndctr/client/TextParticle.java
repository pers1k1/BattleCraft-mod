package com.persiki84.dmgndctr.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class TextParticle extends Particle {
    private static final float MIN_VISIBLE = 0.03f;
    private static final float BASE_SCALE = 0.035f;
    private static final float WORLD_BAKE = 4.0f;

    private final String text;
    private final boolean isCrit;

    public TextParticle(ClientLevel level, double x, double y, double z, float damage, boolean isCrit) {
        super(level, x, y, z);
        this.gravity = 0.05F;
        this.lifetime = 26;
        this.xd = 0;
        this.yd = 0.12;
        this.zd = 0;
        this.isCrit = isCrit;
        this.text = format(damage);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 camPos = camera.getPosition();
        float x = (float) (Mth.lerp(partialTicks, this.xo, this.x) - camPos.x());
        float y = (float) (Mth.lerp(partialTicks, this.yo, this.y) - camPos.y());
        float z = (float) (Mth.lerp(partialTicks, this.zo, this.z) - camPos.z());

        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
        Font font = mc.font;

        float life = (this.age + partialTicks) / this.lifetime;
        float pop = UiAnim.easeOutBack(Math.min(1.0f, (this.age + partialTicks) / 4.0f));
        float fade = life > 0.6f ? 1.0f - (life - 0.6f) / 0.4f : 1.0f;
        if (fade <= MIN_VISIBLE) return;

        float scale = BASE_SCALE * (0.55f + 0.45f * pop) * (isCrit ? 1.25f : 1.0f);
        int color = UiTheme.alpha(isCrit ? UiAccent.color() : UiAccent.dim(), UiAnim.clamp01(fade));

        PoseStack poseStack = new PoseStack();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(-scale, -scale, scale);

        Component label = Component.literal(text).withStyle(style -> style.withFont(UiRender.boldFaceFor(WORLD_BAKE)));
        font.drawInBatch(label, -font.getSplitter().stringWidth(label) / 2.0f, 0, color, false,
                poseStack.last().pose(), source, Font.DisplayMode.NORMAL, 0, 15728880);

        poseStack.popPose();
        source.endBatch();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.CUSTOM;
    }

    private static String format(float damage) {
        int rounded = Math.round(damage);
        if (Math.abs(damage - rounded) < 0.05f) {
            return String.valueOf(rounded);
        }
        return String.format("%.1f", damage);
    }
}
