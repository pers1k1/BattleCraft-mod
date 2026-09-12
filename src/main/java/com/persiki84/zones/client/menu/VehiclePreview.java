package com.persiki84.zones.client.menu;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

public final class VehiclePreview {
    private static final String BLOCK_TAG = "BlockEntityTag";
    private static final String TYPE_KEY = "EntityType";
    private static final int LIGHT_FULL = 0xF000F0;
    private static final float MARGIN = 0.72f;
    private static final float TURN_ROOM = 1.45f;
    private static final float MODEL_DEPTH = 150.0f;

    private static final Map<String, Entity> cache = new HashMap<>();

    private VehiclePreview() {}

    public static boolean holdsVehicle(ItemStack stack) {
        return typeId(stack) != null;
    }

    public static EntityType<?> typeOf(ItemStack stack) {
        String id = typeId(stack);
        return id == null ? null : registered(id);
    }

    public static Entity entityOf(ItemStack stack) {
        String id = typeId(stack);
        if (id == null) return null;

        Entity known = cache.get(id);
        if (known != null) return known;

        Entity made = create(id);
        if (made != null) cache.put(id, made);
        return made;
    }

    public static void forget() {
        cache.clear();
    }

    private static String typeId(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTag()) return null;

        CompoundTag tag = stack.getTag();
        if (!tag.contains(BLOCK_TAG)) return null;

        String id = tag.getCompound(BLOCK_TAG).getString(TYPE_KEY);
        return id.isEmpty() ? null : id;
    }

    private static EntityType<?> registered(String id) {
        ResourceLocation name = ResourceLocation.tryParse(id);
        return name == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(name);
    }

    private static Entity create(String id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;

        EntityType<?> type = registered(id);
        return type == null ? null : type.create(minecraft.level);
    }

    public static void render(GuiGraphics graphics, Entity entity, float centerX, float centerY,
                              float box, float yaw, float pitch) {
        float height = entity.getType().getHeight();
        float scale = box * MARGIN / Math.max(1.0f, span(entity));

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY + height * scale / 2.0f, MODEL_DEPTH);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));

        drawEntity(graphics, entity);
        pose.popPose();
    }

    private static float span(Entity entity) {
        float width = entity.getType().getWidth();
        float height = entity.getType().getHeight();
        return Math.max(width * TURN_ROOM, height);
    }

    private static void drawEntity(GuiGraphics graphics, Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();

        graphics.flush();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        Lighting.setupForEntityInInventory();
        dispatcher.setRenderShadow(false);

        MultiBufferSource.BufferSource buffer = graphics.bufferSource();
        dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0f, 1.0f, graphics.pose(), buffer, LIGHT_FULL);
        buffer.endBatch();

        dispatcher.setRenderShadow(true);
        Lighting.setupFor3DItems();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.depthMask(true);
    }
}
