package com.persiki84.zones.client.menu;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.gunsmith.GunSmith;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.opengl.GL11;

// WHY: модель предмета крутится одним кодом в магазине и в редакторе лута: предпросмотр вещи
// WHY: обязан выглядеть одинаково везде, где вещь показывают перед выбором
public final class ItemTurntable {
    private static final float MODEL_SIZE = 44.0f;
    private static final float MODEL_DEPTH = 150.0f;
    private static final float CUSTOM_MODEL_FIT = 0.42f;
    private static final float SPIN_PER_SECOND = 38.0f;
    private static final float SPIN_TILT = 18.0f;
    private static final float SWAY_DEGREES = 26.0f;
    private static final float SWAY_PER_SECOND = 1.1f;
    private static final float DRAG_DEGREES_PER_PIXEL = 1.4f;
    private static final float DRAG_PITCH_LIMIT = 89.0f;
    private static final float ZOOM_STEP = 0.18f;
    private static final float ZOOM_MIN = 0.55f;
    private static final float ZOOM_MAX = 3.2f;
    private static final int LIGHT_FULL = 15728880;

    private float left;
    private float top;
    private float width;
    private float height;
    private float modelYaw;
    private float modelPitch;
    private float zoom = 1.0f;
    private boolean turningByHand;
    private boolean dragging;
    private long openedAt = System.currentTimeMillis();

    public void rest() {
        turningByHand = false;
        dragging = false;
        modelYaw = 0.0f;
        modelPitch = 0.0f;
        zoom = 1.0f;
        openedAt = System.currentTimeMillis();
    }

    public boolean over(double mouseX, double mouseY) {
        return mouseX >= left && mouseX <= left + width && mouseY >= top && mouseY <= top + height;
    }

    public void beginDrag() {
        dragging = true;
    }

    public void endDrag() {
        dragging = false;
    }

    public boolean dragging() {
        return dragging;
    }

    public void drag(double dragX, double dragY) {
        turningByHand = true;
        modelYaw += (float) dragX * DRAG_DEGREES_PER_PIXEL;
        modelPitch = Mth.clamp(modelPitch + (float) dragY * DRAG_DEGREES_PER_PIXEL,
                -DRAG_PITCH_LIMIT, DRAG_PITCH_LIMIT);
    }

    public void magnify(double amount) {
        zoom = Mth.clamp(zoom * (1.0f + (float) amount * ZOOM_STEP), ZOOM_MIN, ZOOM_MAX);
    }

    public void render(GuiGraphics graphics, ItemStack stack, float boxLeft, float boxTop, float boxWidth, float boxHeight) {
        left = boxLeft;
        top = boxTop;
        width = boxWidth;
        height = boxHeight;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || stack.isEmpty()) return;

        UiRender.clip(graphics, left, top, width, height);
        ItemStack held = holdForPreview(client, stack);
        try {
            drawModel(graphics, client, stack, left + width / 2.0f, top + height / 2.0f);
        } finally {
            graphics.flush();
            graphics.disableScissor();
            releasePreview(client, held);
        }
    }

    // WHY: SuperbWarfare показывает обвесы по предмету в главной руке, и без подмены ствол в
    // WHY: предпросмотре был голым или носил обвесы того, что игрок держит. Подмена только на
    // WHY: клиенте и только на время отрисовки, возврат в finally
    private static ItemStack holdForPreview(Minecraft client, ItemStack stack) {
        if (client.player == null || !GunSmith.readsHeldItem(stack)) return null;

        Inventory inventory = client.player.getInventory();
        ItemStack held = inventory.items.get(inventory.selected);
        inventory.items.set(inventory.selected, stack);
        return held;
    }

    private static void releasePreview(Minecraft client, ItemStack held) {
        if (held == null || client.player == null) return;

        Inventory inventory = client.player.getInventory();
        inventory.items.set(inventory.selected, held);
    }

    private void drawModel(GuiGraphics graphics, Minecraft client, ItemStack stack, float centerX, float centerY) {
        if (VehiclePreview.holdsVehicle(stack)) {
            Entity vehicle = VehiclePreview.entityOf(stack);
            if (vehicle != null) {
                VehiclePreview.render(graphics, vehicle, centerX, centerY, height * zoom, yaw(), pitch());
                return;
            }
        }
        renderSpinningItem(graphics, client, stack, centerX, centerY);
    }

    private void renderSpinningItem(GuiGraphics graphics, Minecraft client, ItemStack stack,
                                    float centerX, float centerY) {
        BakedModel model = client.getItemRenderer().getModel(stack, client.level, null, 0);
        float size = MODEL_SIZE * fitting(model, stack) * zoom;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, MODEL_DEPTH);
        pose.scale(size, -size, size);
        turn(pose, model.isGui3d());

        drawSolidModel(graphics, client, stack, !model.usesBlockLight());
        pose.popPose();
    }

    private void turn(PoseStack pose, boolean solid) {
        if (turningByHand) {
            pose.mulPose(Axis.XP.rotationDegrees(modelPitch));
            pose.mulPose(Axis.YP.rotationDegrees(modelYaw));
            return;
        }
        if (solid) {
            pose.mulPose(Axis.XP.rotationDegrees(SPIN_TILT));
            pose.mulPose(Axis.YP.rotationDegrees(seconds() * SPIN_PER_SECOND));
            return;
        }
        pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(seconds() * SWAY_PER_SECOND) * SWAY_DEGREES));
    }

    private float yaw() {
        return turningByHand ? modelYaw : seconds() * SPIN_PER_SECOND;
    }

    private float pitch() {
        return turningByHand ? modelPitch : SPIN_TILT;
    }

    private float seconds() {
        return (System.currentTimeMillis() - openedAt) / 1000.0f;
    }

    private static float fitting(BakedModel model, ItemStack stack) {
        float span = ModelBounds.longestSide(model);
        if (span > 1.0f) return 1.0f / span;
        return GunSmith.isGun(stack) ? CUSTOM_MODEL_FIT : 1.0f;
    }

    private static void drawSolidModel(GuiGraphics graphics, Minecraft client, ItemStack stack, boolean flat) {
        graphics.flush();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        if (flat) {
            Lighting.setupForFlatItems();
        } else {
            Lighting.setupForEntityInInventory();
        }

        MultiBufferSource.BufferSource buffer = graphics.bufferSource();
        client.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, LIGHT_FULL,
                OverlayTexture.NO_OVERLAY, graphics.pose(), buffer, client.level, 0);
        buffer.endBatch();

        Lighting.setupFor3DItems();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.depthMask(true);
    }
}
