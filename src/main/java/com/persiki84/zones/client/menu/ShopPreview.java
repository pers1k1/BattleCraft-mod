package com.persiki84.zones.client.menu;

import com.persiki84.shared.client.menu.ScrollHint;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.gunsmith.GunAmmo;
import com.persiki84.shared.gunsmith.GunSlot;
import com.persiki84.shared.gunsmith.GunSmith;
import com.persiki84.shared.gunsmith.GunStat;
import com.persiki84.shared.gunsmith.VehiclePower;
import com.persiki84.zones.client.ClientModifierData;
import com.persiki84.zones.shop.ShopEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ShopPreview {
    public static final float BOX_HEIGHT = 78.0f;

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

    private static final float LINE_SCALE = 0.75f;
    private static final float NAME_SCALE = 0.85f;
    private static final float HEAD_SCALE = 0.7f;
    private static final int SECONDS_PER_MINUTE = 60;

    private static final float ROUND_INDENT = 8.0f;
    private static final int TOOLTIP_LINE_LIMIT = 24;
    private static final float TEXT_SCROLL_STEP = 14.0f;
    private static final float TEXT_RIDE_SPEED = 19.0f;

    private final List<GunAmmo> ammo = new ArrayList<>();
    private final List<GunSlot> slots = new ArrayList<>();
    private final List<GunStat> stats = new ArrayList<>();
    private final List<Component> notes = new ArrayList<>();
    private final Smooth ride = new Smooth(0.0f, TEXT_RIDE_SPEED);
    private VehiclePower power;
    private String describedEntry = "";
    private boolean describedExpanded;

    private float boxLeft;
    private float boxTop;
    private float boxWidth;
    private float modelYaw;
    private float modelPitch;
    private float zoom = 1.0f;
    private float textTop;
    private float textBottom;
    private float scroll;
    private float overflow;
    private boolean turningByHand;
    private boolean dragging;
    private long openedAt = System.currentTimeMillis();

    public void rest() {
        turningByHand = false;
        dragging = false;
        modelYaw = 0.0f;
        modelPitch = 0.0f;
        zoom = 1.0f;
        scroll = 0.0f;
        overflow = 0.0f;
        ride.snap(0.0f);
        openedAt = System.currentTimeMillis();
    }

    public boolean overText(double mouseX, double mouseY) {
        return mouseX >= boxLeft - UiMetrics.PAD_WIDE && mouseX <= boxLeft + boxWidth + UiMetrics.PAD_WIDE
                && mouseY >= textTop && mouseY <= textBottom;
    }

    public boolean scrollText(double amount) {
        if (overflow <= 0.5f) return false;

        scroll = Mth.clamp(scroll - (float) amount * TEXT_SCROLL_STEP, 0.0f, overflow);
        return true;
    }

    public boolean hiddenAbove() {
        return overflow > 0.5f && scroll > 0.5f;
    }

    public boolean hiddenBelow() {
        return overflow > 0.5f && scroll < overflow - 0.5f;
    }

    public float textTop() {
        return textTop;
    }

    public float textBottom() {
        return textBottom;
    }

    public boolean overModel(double mouseX, double mouseY) {
        return mouseX >= boxLeft && mouseX <= boxLeft + boxWidth
                && mouseY >= boxTop && mouseY <= boxTop + BOX_HEIGHT;
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

    public void render(GuiGraphics graphics, ShopEntry entry, float left, float top,
                       float width, float limit) {
        boxLeft = left + UiMetrics.PAD_WIDE;
        boxTop = top + UiMetrics.PAD_WIDE;
        boxWidth = width - UiMetrics.PAD_WIDE * 2.0f;

        describe(entry);
        renderModel(graphics, entry.stack());

        float headY = boxTop + BOX_HEIGHT + UiMetrics.GAP_WIDE;
        float nameBottom = renderName(graphics, entry, left, width, headY);

        textTop = nameBottom + UiMetrics.GAP + ScrollHint.BAND_TOP;
        textBottom = limit - ScrollHint.BAND_BOTTOM;
        listing(graphics, entry, left, width);
    }

    private float renderName(GuiGraphics graphics, ShopEntry entry, float left, float width, float headY) {
        int wrap = Math.max(1, (int) ((width - UiMetrics.PAD_WIDE * 2.0f) / NAME_SCALE));
        List<FormattedCharSequence> lines = UiRender.split(graphics, font(),
                entry.stack().getHoverName(), NAME_SCALE, wrap);
        float step = font().lineHeight * NAME_SCALE;
        float y = headY;

        for (FormattedCharSequence line : lines) {
            UiRender.emphasisCentered(graphics, font(), Component.literal(UiRender.flatten(line)),
                    left + width / 2.0f, y, NAME_SCALE, UiAccent.text());
            y += step;
        }
        return Math.max(y, headY + step);
    }

    private void listing(GuiGraphics graphics, ShopEntry entry, float left, float width) {
        float offset = ride.to(scroll, UiFrame.delta());
        UiRender.clip(graphics, left, textTop, width, Math.max(0.0f, textBottom - textTop));
        float textY = textTop - offset;
        try {
            textY = renderFacts(graphics, entry, left, width, textY);
            textY = renderPower(graphics, left, textY);
            textY = renderAmmo(graphics, left, width, textY);
            textY = renderAttachments(graphics, left, width, textY);
            textY = renderStats(graphics, left, width, textY);
            textY = renderNotes(graphics, left + width / 2.0f, textY);
        } finally {
            graphics.flush();
            graphics.disableScissor();
        }
        overflow = Math.max(0.0f, textY + offset - textBottom);
        scroll = Math.min(scroll, overflow);
    }

    private boolean visible(float textY, float step) {
        return textY + step > textTop && textY < textBottom;
    }

    private void describe(ShopEntry entry) {
        boolean expanded = Screen.hasShiftDown();
        if (entry.id().equals(describedEntry) && expanded == describedExpanded) return;

        describedEntry = entry.id();
        describedExpanded = expanded;
        ammo.clear();
        power = null;
        slots.clear();
        stats.clear();
        notes.clear();

        if (entry.description() != null && !entry.description().isEmpty()) {
            notes.add(Component.literal(entry.description()));
        }
        notes.addAll(ClientModifierData.describe(entry.stack()));

        if (!GunSmith.isGun(entry.stack())) {
            EntityType<?> vehicle = VehiclePreview.typeOf(entry.stack());
            ammo.addAll(GunSmith.vehicleAmmo(vehicle));
            power = GunSmith.vehiclePower(vehicle);
            collectTooltip(entry.stack());
            return;
        }
        ammo.addAll(GunSmith.ammo(entry.stack()));
        slots.addAll(GunSmith.slots(entry.stack()));
        stats.addAll(GunSmith.stats(entry.stack()));
    }

    private void collectTooltip(ItemStack stack) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || stack.isEmpty()) return;

        List<Component> tooltip = stack.getTooltipLines(client.player, TooltipFlag.Default.NORMAL);
        int taken = 0;
        for (int index = 1; index < tooltip.size() && taken < TOOLTIP_LINE_LIMIT; index++) {
            if (tooltip.get(index).getString().isBlank()) continue;

            notes.add(tooltip.get(index));
            taken++;
        }
    }

    private float renderFacts(GuiGraphics graphics, ShopEntry entry, float left, float width, float textY) {
        float step = font().lineHeight * LINE_SCALE + UiMetrics.GAP;
        float centerX = left + width / 2.0f;

        if (visible(textY, step)) {
            UiRender.labelCentered(graphics, font(), priceLabel(entry), centerX, textY, LINE_SCALE, UiAccent.text());
        }
        textY += step;

        if (entry.bundle() > 1) {
            if (visible(textY, step)) {
                UiRender.labelCentered(graphics, font(), Component.translatable("zones.shop.bundle", entry.bundle()),
                        centerX, textY, LINE_SCALE, UiAccent.text());
            }
            textY += step;
        }

        if (entry.limited()) {
            if (visible(textY, step)) {
                UiRender.labelCentered(graphics, font(), stockLabel(entry), centerX, textY, LINE_SCALE,
                        entry.soldOut() ? UiPalette.alert() : UiAccent.text());
            }
            textY += step;
        }
        return textY;
    }

    private float renderAmmo(GuiGraphics graphics, float left, float width, float textY) {
        if (ammo.isEmpty()) return textY;

        textY = heading(graphics, "zones.shop.ammo", left, textY);
        for (GunAmmo need : ammo) {
            if (need.named()) textY = renderWeapon(graphics, need, left, textY);
            textY = renderRounds(graphics, need, left, width, textY);
        }
        return textY + UiMetrics.GAP;
    }

    private float renderPower(GuiGraphics graphics, float left, float textY) {
        if (power == null) return textY;

        textY = heading(graphics, "zones.shop.power", left, textY);
        textY = line(graphics, capacityLabel(), left, textY, 0.0f, UiAccent.text());
        for (VehiclePower.Cell cell : power.cells()) {
            textY = line(graphics, cellLabel(cell), left, textY, ROUND_INDENT, UiAccent.color());
        }
        return textY + UiMetrics.GAP;
    }

    private Component capacityLabel() {
        return Component.translatable("zones.shop.power.capacity", grouped(power.energy()));
    }

    private static Component cellLabel(VehiclePower.Cell cell) {
        return Component.translatable("zones.shop.power.pack", cell.count(), cell.name());
    }

    private static String grouped(int value) {
        return String.format(Locale.ROOT, "%,d", value).replace(',', ' ');
    }

    private float renderWeapon(GuiGraphics graphics, GunAmmo need, float left, float textY) {
        return line(graphics, Component.literal(need.weapon()), left, textY, 0.0f, UiAccent.text());
    }

    private float line(GuiGraphics graphics, Component text, float left, float textY, float indent, int color) {
        float step = font().lineHeight * LINE_SCALE + UiMetrics.GAP_TIGHT;
        if (visible(textY, step)) {
            UiRender.textTrackedLeft(graphics, font(), text,
                    left + UiMetrics.PAD_WIDE + indent, textY, LINE_SCALE, 0.0f, color);
        }
        return textY + step;
    }

    private float renderRounds(GuiGraphics graphics, GunAmmo need, float left, float width, float textY) {
        float step = font().lineHeight * LINE_SCALE + UiMetrics.GAP_TIGHT;
        float indent = need.named() ? ROUND_INDENT : 0.0f;
        float roundX = left + UiMetrics.PAD_WIDE + indent;
        int wrapWidth = (int) ((width - UiMetrics.PAD_WIDE * 2.0f - indent) / LINE_SCALE);

        for (String round : need.rounds()) {
            for (var part : UiRender.split(graphics, font(), Component.literal(round), LINE_SCALE, wrapWidth)) {
                if (visible(textY, step)) {
                    UiRender.textTrackedLeft(graphics, font(), Component.literal(UiRender.flatten(part)),
                            roundX, textY, LINE_SCALE, 0.0f, UiAccent.color());
                }
                textY += step;
            }
        }
        return textY;
    }

    private float renderAttachments(GuiGraphics graphics, float left, float width, float textY) {
        if (slots.isEmpty()) return textY;

        float step = font().lineHeight * LINE_SCALE + UiMetrics.GAP_TIGHT;
        textY = heading(graphics, "zones.shop.attachments", left, textY);

        for (GunSlot slot : slots) {
            if (!visible(textY, step)) {
                textY += step;
                continue;
            }

            boolean filled = !slot.installed().isEmpty();
            UiRender.textTrackedLeft(graphics, font(), Component.translatable(slot.label()),
                    left + UiMetrics.PAD_WIDE, textY, LINE_SCALE, 0.0f,
                    UiAccent.text());
            UiRender.textRight(graphics, font(), attachmentName(slot),
                    left + width - UiMetrics.PAD_WIDE, textY, LINE_SCALE,
                    filled ? UiAccent.color() : UiAccent.text(), false);
            textY += step;
        }
        return textY + UiMetrics.GAP;
    }

    private float renderStats(GuiGraphics graphics, float left, float width, float textY) {
        if (stats.isEmpty()) return textY;

        float step = font().lineHeight * LINE_SCALE + UiMetrics.GAP_TIGHT;
        textY = heading(graphics, "zones.shop.stats", left, textY);

        for (GunStat stat : stats) {
            if (!visible(textY, step)) {
                textY += step;
                continue;
            }

            UiRender.textTrackedLeft(graphics, font(), Component.translatable(stat.label()),
                    left + UiMetrics.PAD_WIDE, textY, LINE_SCALE, 0.0f, UiAccent.text());
            UiRender.textRight(graphics, font(), Component.literal(stat.value()),
                    left + width - UiMetrics.PAD_WIDE, textY, LINE_SCALE, UiAccent.text(), false);
            textY += step;
        }
        return textY + UiMetrics.GAP;
    }

    private float heading(GuiGraphics graphics, String key, float left, float textY) {
        float step = font().lineHeight * HEAD_SCALE;
        if (visible(textY, step)) {
            UiRender.textTrackedLeft(graphics, font(), Component.translatable(key),
                    left + UiMetrics.PAD_WIDE, textY, HEAD_SCALE, 0.6f, UiAccent.text());
        }
        return textY + step + UiMetrics.GAP_TIGHT;
    }

    private float renderNotes(GuiGraphics graphics, float centerX, float textY) {
        float step = font().lineHeight * LINE_SCALE + UiMetrics.GAP_TIGHT;
        int wrapWidth = (int) (boxWidth / LINE_SCALE);

        for (Component note : notes) {
            for (var part : UiRender.split(graphics, font(), note, LINE_SCALE, wrapWidth)) {
                if (visible(textY, step)) {
                    UiRender.textCentered(graphics, font(), Component.literal(UiRender.flatten(part)),
                            centerX, textY, LINE_SCALE, UiAccent.text(), false);
                }
                textY += step;
            }
        }
        return textY;
    }

    private static Component attachmentName(GunSlot slot) {
        return slot.installed().isEmpty()
                ? Component.translatable("zones.shop.slot_empty")
                : GunSmith.optionName(slot.installed());
    }

    private static Component priceLabel(ShopEntry entry) {
        return Component.translatable("zones.shop.price", entry.price());
    }

    private static Component stockLabel(ShopEntry entry) {
        if (!entry.soldOut()) return Component.translatable("zones.shop.left", entry.availableItems());

        int waiting = entry.remainingSeconds(System.currentTimeMillis());
        if (waiting <= 0) return Component.translatable("zones.shop.sold_out");
        return Component.translatable("zones.shop.restock", clock(waiting));
    }

    private static String clock(int seconds) {
        int rest = seconds % SECONDS_PER_MINUTE;
        return seconds / SECONDS_PER_MINUTE + ":" + (rest < 10 ? "0" : "") + rest;
    }

    private void renderModel(GuiGraphics graphics, ItemStack stack) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        UiRender.clip(graphics, boxLeft, boxTop, boxWidth, BOX_HEIGHT);
        try {
            drawModel(graphics, client, stack, boxLeft + boxWidth / 2.0f, boxTop + BOX_HEIGHT / 2.0f);
        } finally {
            graphics.flush();
            graphics.disableScissor();
        }
    }

    private void drawModel(GuiGraphics graphics, Minecraft client, ItemStack stack, float centerX, float centerY) {
        if (VehiclePreview.holdsVehicle(stack)) {
            Entity vehicle = VehiclePreview.entityOf(stack);
            if (vehicle != null) {
                VehiclePreview.render(graphics, vehicle, centerX, centerY, BOX_HEIGHT * zoom, yaw(), pitch());
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

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}
