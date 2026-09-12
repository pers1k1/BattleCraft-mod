package com.persiki84.battlecraft.client.hud;

import com.persiki84.battlecraft.client.custom.HudSlot;
import com.persiki84.battlecraft.client.custom.HudLayout;
import com.persiki84.battlecraft.client.custom.HudBox;
import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.Spring;
import com.persiki84.shared.client.ui.Toggle;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFont;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiGlassStyle;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiPulse;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiScale;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import com.persiki84.shared.client.ui.UiVital;
import net.minecraft.ChatFormatting;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public final class BottomHud {
    private static final int SLOTS = 9;
    private static final int CELL = 22;
    private static final int CELL_GAP = 3;
    private static final int BOTTOM_MARGIN = 7;
    private static final float TRAY_PADDING = 3.0f;
    private static final float SLOT_LABEL_SCALE = 0.68f;
    private static final float EMPTY_SLOT_NUMBER_SCALE = 1.25f;
    private static final float NAME_GAP = 7.0f;
    private static final float SELECTOR_SHEEN = 0.55f;
    private static final float LIQUID_STRETCH = 0.055f;
    private static final float LIQUID_MAX_STRETCH = 13.0f;
    private static final int BLOCK_WIDTH = CELL * SLOTS + CELL_GAP * (SLOTS - 1);
    private static final int CARD_WIDTH = 128;
    private static final float CARD_PADDING = UiMetrics.PAD;
    private static final float HEALTH_ROW = 19.0f;
    private static int healthValue = Integer.MIN_VALUE;
    private static int maxValue = Integer.MIN_VALUE;
    private static Component healthLabel;
    private static Component maxLabel;
    private static float healthWidth;
    private static float maxWidth;

    private static final float HEALTH_SCALE = 1.9f;
    private static final float UNIT_SCALE = 0.8f;
    private static final float ROW_GAP = 7.0f;
    private static final float DIAL_SIZE = 17.0f;
    private static final float DIAL_RADIUS = 7.0f;
    private static final float DIAL_THICKNESS = 2.3f;
    private static final float DIAL_GAP = 5.0f;
    private static final float DIAL_ICON = 6.6f;
    private static final float DIAL_LABEL_GAP = 3.5f;
    private static final float DIAL_LABEL_SCALE = 0.7f;
    private static final float DIAL_LABEL_ROW = 6.0f;
    private static final float PULSE_CYCLES = 1.9f;
    private static final float PULSE_WIDTH = 1.05f;
    private static final float HEALTH_LIFT = 1.6f;
    private static final float PULSE_GAP = 6.0f;
    private static final float NAME_ROW = 19.0f;
    private static final float NAME_SCALE = 1.0f;
    private static final float NAME_LIFT = 5.0f;
    private static final float WARN_RATIO = 0.3f;
    private static final int WARN_COLOR = 0xFFE8C24A;
    private static final float WARN_BLINK_MS = 900.0f;
    private static final float CRITICAL_BLINK_MS = 560.0f;
    private static final float HIDE_TRAVEL = 30.0f;

    private static final Smooth[] slotAmount = new Smooth[SLOTS];
    private static final Smooth offhandAmount = new Smooth(0.0f, 14.0f);
    private static final Spring visibility = new Spring(0.34f, 0.95f, 1.0f);
    private static final Spring selectorX = new Spring(0.26f, 0.82f);
    private static final Spring selectorWidth = new Spring(0.26f, 0.86f);
    private static final Smooth foodFill = new Smooth(12.0f);
    private static final Smooth armorFill = new Smooth(12.0f);
    private static final Toggle armorToggle = new Toggle(8.0f, 150L);
    private static final Smooth airFill = new Smooth(16.0f);
    private static final Toggle airToggle = new Toggle(8.0f, 150L);
    private static final Smooth staminaFill = new Smooth(16.0f);
    private static final Toggle staminaToggle = new Toggle(8.0f, 150L);
    private static final Smooth mountFill = new Smooth(12.0f);
    private static final Toggle mountToggle = new Toggle(8.0f, 150L);
    private static final Smooth lineFill = new Smooth(12.0f);

    private static final float[] previewAmount = new float[SLOTS];
    private static final Smooth cardBottom = new Smooth(10.0f);
    private static final Smooth liquidStretch = new Smooth(0.0f, 9.0f);

    private static StaminaBridge.Reading lastStamina;
    private static boolean statusVisible;
    private static float noticeTop = Float.MAX_VALUE;

    static {
        for (int slot = 0; slot < SLOTS; slot++) {
            slotAmount[slot] = new Smooth(1.0f, 14.0f);
        }
    }

    private BottomHud() {}

    public static float statusBottom() {
        return statusVisible ? cardBottom.get() : 0.0f;
    }

    public static float noticeTop() {
        return noticeTop;
    }

    public static final IGuiOverlay OVERLAY = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator() || mc.gameMode == null) return;

        float scale = UiScale.push(graphics);
        try {
            render(graphics, partialTick, screenWidth / scale, screenHeight / scale);
        } finally {
            UiScale.pop(graphics);
        }
    };

    private static void render(GuiGraphics graphics, float partialTick, float screenWidth, float screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        float delta = UiFrame.delta();
        float left = (screenWidth - BLOCK_WIDTH) / 2.0f;
        float hotbarTop = screenHeight - BOTTOM_MARGIN - CELL;
        float lineTop = hotbarTop - 6.0f;

        boolean bars = HudConfig.statusBars();
        float contentTop = hotbarTop;

        if (bars && player.jumpableVehicle() != null) {
            renderJumpBar(graphics, player, left, lineTop, delta);
            contentTop = lineTop;
        }

        statusVisible = bars && mc.gameMode.canHurtPlayer() && HudLayout.visible(HudSlot.STATUS);
        if (statusVisible) {
            renderStatus(graphics, mc, player, screenWidth, screenHeight, delta);
        }

        renderHotbar(graphics, mc, player, screenWidth, screenHeight, partialTick, delta);
        float nameBottom = nameBottom(contentTop);
        noticeTop = noticeTop(nameBottom);
        renderItemName(graphics, mc, screenWidth, screenHeight, nameBottom);
    }

    // WHY: место под имя предмета держится постоянно, иначе сообщение прыгает на высоту карточки
    // WHY: при каждом взятии предмета, а зазор стыковки входит в границу: без него расталкивание
    // WHY: каждый кадр добирает недостающие пиксели и карточка дрожит
    private static float noticeTop(float nameBottom) {
        if (!HudLayout.visible(HudSlot.ITEM_NAME) || HudLayout.moved(HudSlot.ITEM_NAME)) return nameBottom;

        return nameBottom - NAME_ROW - HudLayout.DOCK_GAP;
    }

    private static float nameBottom(float contentTop) {
        HudBox tray = HudLayout.box(HudSlot.HOTBAR);
        float anchorTop = tray.drawn() ? tray.y() : contentTop - TRAY_PADDING;
        return anchorTop - NAME_GAP;
    }

    private static void renderHotbar(GuiGraphics graphics, Minecraft mc, LocalPlayer player, float screenWidth,
                                     float screenHeight, float partialTick, float delta) {
        boolean wanted = HudState.hotbarWanted() && HudLayout.visible(HudSlot.HOTBAR);
        float appear = visibility.to(wanted ? 1.0f : 0.0f, delta);
        if (appear <= 0.004f) return;

        Inventory inventory = player.getInventory();
        int selected = inventory.selected;

        float[] amount = new float[SLOTS];
        float cells = measureSlots(inventory, amount, selected, delta);
        if (cells <= 0.01f) return;

        float total = cells * CELL + Math.max(0.0f, cells - 1.0f) * CELL_GAP;
        HudBox tray = HudLayout.place(HudSlot.HOTBAR, total + TRAY_PADDING * 2.0f,
                CELL + TRAY_PADDING * 2.0f, screenWidth, screenHeight);
        float rowStart = tray.x() + TRAY_PADDING;
        float y = tray.y() + TRAY_PADDING + (1.0f - appear) * HIDE_TRAVEL;
        float alpha = UiAnim.clamp01(appear) * tray.alpha();

        HudLayout.push(graphics, tray);
        try {
            paintTray(graphics, mc, player, inventory, amount, rowStart, y, total, selected,
                    alpha, partialTick, delta);
        } finally {
            HudLayout.pop(graphics, tray);
        }
    }

    private static void paintTray(GuiGraphics graphics, Minecraft mc, LocalPlayer player, Inventory inventory,
                                  float[] amount, float rowStart, float y, float total, int selected,
                                  float alpha, float partialTick, float delta) {
        UiVital.card(graphics, rowStart - TRAY_PADDING, y - TRAY_PADDING,
                total + TRAY_PADDING * 2.0f, CELL + TRAY_PADDING * 2.0f,
                UiGlassStyle.radiusCell() + TRAY_PADDING, alpha);

        renderSelector(graphics, amount, rowStart, y, selected, alpha, delta);
        renderCells(graphics, mc, player, inventory, amount, rowStart, y, selected, alpha, partialTick);
        renderOffhand(graphics, mc, player, rowStart, total, y, alpha, partialTick, delta);
        renderAttackCharge(graphics, mc, player, y, alpha);
    }

    private static float measureSlots(Inventory inventory, float[] amount, int selected, float delta) {
        boolean collapse = HudConfig.collapseEmptySlots();
        float cells = 0.0f;
        for (int slot = 0; slot < SLOTS; slot++) {
            boolean shown = !collapse || slot == selected || !inventory.items.get(slot).isEmpty();
            amount[slot] = slotAmount[slot].to(shown ? 1.0f : 0.0f, delta);
            cells += amount[slot];
        }
        return cells;
    }

    private static void renderCells(GuiGraphics graphics, Minecraft mc, LocalPlayer player, Inventory inventory,
                                    float[] amount, float rowStart, float y, int selected, float alpha, float partialTick) {
        float x = rowStart;
        for (int slot = 0; slot < SLOTS; slot++) {
            float presence = amount[slot];
            if (presence <= 0.002f) continue;
            renderCell(graphics, mc, player, inventory.items.get(slot), x, y, CELL * presence, presence,
                    slot == selected, alpha, partialTick, slot + 1);
            x += CELL * presence + CELL_GAP * presence;
        }
    }

    private static void renderSelector(GuiGraphics graphics, float[] amount, float rowStart, float y,
                                       int selected, float alpha, float delta) {
        float x = rowStart;
        float selX = rowStart;
        float selW = CELL;
        for (int slot = 0; slot < SLOTS; slot++) {
            float presence = amount[slot];
            if (presence <= 0.002f) continue;
            if (slot == selected) {
                selX = x;
                selW = CELL * presence;
            }
            x += CELL * presence + CELL_GAP * presence;
        }

        float indicatorX = selectorX.to(selX, delta);
        float indicatorW = selectorWidth.to(selW, delta);
        float glide = Math.abs(selectorX.velocity()) * LIQUID_STRETCH;
        float stretch = liquidStretch.to(Math.min(LIQUID_MAX_STRETCH, glide), delta);
        float pop = HudState.selectionPop();
        if (indicatorW <= 0.5f) return;

        float squash = stretch / LIQUID_MAX_STRETCH;
        float lensY = y - 1.0f + squash * 0.8f - pop * 1.2f;
        float lensH = CELL + 2.0f - squash * 1.6f + pop * 2.4f;
        float lensX = indicatorX - stretch * 0.5f;
        float lensW = indicatorW + stretch;
        float radius = Math.min(UiGlassStyle.radiusCell(), lensH / 2.0f);
        float lift = Math.min(1.0f, 0.5f + 0.5f * pop + UiVital.lift());

        UiGlass.panel(graphics, lensX, lensY, lensW, lensH, radius, alpha, lift);
        UiGlass.inner(graphics, lensX, lensY, lensW, lensH, radius, alpha * SELECTOR_SHEEN, lift);
    }

    private static void renderOffhand(GuiGraphics graphics, Minecraft mc, LocalPlayer player, float rowStart,
                                      float total, float y, float alpha, float partialTick, float delta) {
        ItemStack offhand = player.getOffhandItem();
        float presence = offhandAmount.to(offhand.isEmpty() ? 0.0f : 1.0f, delta);
        if (presence <= 0.002f) return;

        float width = CELL * presence;
        boolean onLeft = player.getMainArm() == HumanoidArm.RIGHT;
        float offhandX = onLeft ? rowStart - 9.0f - width : rowStart + total + 9.0f;
        UiVital.card(graphics, offhandX - TRAY_PADDING, y - TRAY_PADDING,
                width + TRAY_PADDING * 2.0f, CELL + TRAY_PADDING * 2.0f,
                UiGlassStyle.radiusCell() + TRAY_PADDING, alpha * presence);
        renderCell(graphics, mc, player, offhand, offhandX, y, width, presence, false, alpha * 0.9f, partialTick, 0);
    }

    private static void renderAttackCharge(GuiGraphics graphics, Minecraft mc, LocalPlayer player, float y, float alpha) {
        if (mc.options.attackIndicator().get() != AttackIndicatorStatus.HOTBAR) return;

        float charge = player.getAttackStrengthScale(0.0f);
        if (charge >= 0.999f) return;

        float lineY = y + CELL + TRAY_PADDING - 2.6f;
        float barX = selectorX.get() + 2.0f;
        float barWidth = selectorWidth.get() - 4.0f;
        UiRender.panel(graphics, barX, lineY, barWidth, 1.6f, 0.8f, UiTheme.alpha(UiAccent.color(), 0.16f * alpha));
        UiRender.panel(graphics, barX, lineY, barWidth * charge, 1.6f, 0.8f, UiTheme.alpha(UiAccent.color(), 0.85f * alpha));
    }

    private static void renderCell(GuiGraphics graphics, Minecraft mc, LocalPlayer player, ItemStack stack, float x, float y,
                                   float width, float presence, boolean selected, float alpha, float partialTick, int number) {
        float pop = selected ? HudState.selectionPop() : 0.0f;
        float cellY = y - pop * 1.6f;

        if (stack.isEmpty()) {
            if (selected) {
                renderEmptySlotNumber(graphics, mc, x, cellY, width, number, alpha);
            } else {
                UiGlass.sunken(graphics, x, cellY, width, CELL, UiGlassStyle.radiusCell(), alpha * 0.55f * presence);
            }
            return;
        }

        float scale = presence * (1.0f + pop * 0.16f);
        float centerX = x + width / 2.0f;
        float centerY = cellY + CELL / 2.0f;

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().translate(-8.0f, -8.0f, 0.0f);

        float squashTime = stack.getPopTime() - partialTick;
        if (squashTime > 0.0f) {
            float squash = 1.0f + squashTime / 5.0f;
            graphics.pose().pushPose();
            graphics.pose().translate(8.0f, 8.0f, 0.0f);
            graphics.pose().scale(1.0f / squash, (squash + 1.0f) / 2.0f, 1.0f);
            graphics.pose().translate(-8.0f, -8.0f, 0.0f);
        }

        graphics.renderItem(player, stack, 0, 0, number);

        if (squashTime > 0.0f) {
            graphics.pose().popPose();
        }

        graphics.renderItemDecorations(mc.font, stack, 0, 0);
        graphics.pose().popPose();

        if (number > 0 && presence > 0.85f && HudConfig.slotNumbers()) {
            int color = HudInk.text();
            float badge = alpha * (selected ? 1.0f : 0.8f);
            UiRender.textCentered(graphics, mc.font, String.valueOf(number),
                    x + 4.8f, cellY + 1.4f, SLOT_LABEL_SCALE, UiTheme.alpha(color, badge), false);
        }
    }

    private static void renderEmptySlotNumber(GuiGraphics graphics, Minecraft mc, float x, float y,
                                              float width, int number, float alpha) {
        if (number <= 0 || !HudConfig.slotNumbers()) return;

        UiFont.push(UiRender.boldFaceFor(UiRender.pixels(graphics)));
        UiRender.textCentered(graphics, mc.font, String.valueOf(number), x + width / 2.0f,
                UiRender.centerY(y, CELL, EMPTY_SLOT_NUMBER_SCALE), EMPTY_SLOT_NUMBER_SCALE,
                UiTheme.alpha(HudInk.text(), alpha), false);
        UiFont.pop();
    }

    private static void renderStatus(GuiGraphics graphics, Minecraft mc, LocalPlayer player, float screenWidth, float screenHeight, float delta) {
        float max = Math.max(1.0f, player.getMaxHealth());
        float health = Mth.clamp(player.getHealth(), 0.0f, max);
        float absorb = Math.max(0.0f, player.getAbsorptionAmount());
        int armor = player.getArmorValue();
        int food = player.getFoodData().getFoodLevel();
        int maxAir = Math.max(1, player.getMaxAirSupply());
        int air = Mth.clamp(player.getAirSupply(), 0, maxAir);

        float cardHeight = statusHeight();
        HudBox box = HudLayout.place(HudSlot.STATUS, CARD_WIDTH, cardHeight, screenWidth, screenHeight);
        float cardTop = box.y();
        float shown = box.alpha();
        cardBottom.to(anchoredBottom() ? 0.0f : cardTop + cardHeight, delta);

        HudLayout.push(graphics, box);
        try {
            paintStatus(graphics, mc, player, box, shown, delta, cardHeight, health, max, absorb, food, armor, air, maxAir);
        } finally {
            HudLayout.pop(graphics, box);
        }
    }

    public static void previewStatus(GuiGraphics graphics, HudBox box, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        float max = Math.max(1.0f, player.getMaxHealth());
        float health = Mth.clamp(player.getHealth(), 0.0f, max);
        int maxAir = Math.max(1, player.getMaxAirSupply());
        HudLayout.sample(HudSlot.STATUS, CARD_WIDTH, statusHeight());
        paintStatus(graphics, mc, player, box, alpha, UiFrame.delta(), statusHeight(), health, max,
                Math.max(0.0f, player.getAbsorptionAmount()), player.getFoodData().getFoodLevel(),
                player.getArmorValue(), Mth.clamp(player.getAirSupply(), 0, maxAir), maxAir);
    }

    public static void previewTray(GuiGraphics graphics, HudBox box, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        Inventory inventory = player.getInventory();
        float delta = UiFrame.delta();
        float cells = measureSlots(inventory, previewAmount, inventory.selected, delta);
        if (cells <= 0.01f) return;

        float total = cells * CELL + Math.max(0.0f, cells - 1.0f) * CELL_GAP;
        HudLayout.sample(HudSlot.HOTBAR, total + TRAY_PADDING * 2.0f, CELL + TRAY_PADDING * 2.0f);
        paintTray(graphics, mc, player, inventory, previewAmount, box.x() + TRAY_PADDING,
                box.y() + TRAY_PADDING, total, inventory.selected, alpha, 0.0f, delta);
    }

    public static float statusHeight() {
        return CARD_PADDING * 2.0f + HEALTH_ROW + ROW_GAP + DIAL_SIZE + DIAL_LABEL_GAP + DIAL_LABEL_ROW;
    }

    private static void paintStatus(GuiGraphics graphics, Minecraft mc, LocalPlayer player, HudBox box,
                                    float shown, float delta, float cardHeight, float health, float max,
                                    float absorb, int food, int armor, int air, int maxAir) {
        float cardX = box.x();
        float cardTop = box.y();
        UiVital.card(graphics, cardX, cardTop, CARD_WIDTH, cardHeight, UiMetrics.radius(cardHeight), shown);
        renderVitals(graphics, mc, cardX, cardTop + CARD_PADDING, health, max, absorb, shown);
        renderDials(graphics, mc, player, cardX + CARD_WIDTH / 2.0f,
                cardTop + CARD_PADDING + HEALTH_ROW + ROW_GAP, shown, delta, food, armor, air, maxAir);
    }

    private static void renderVitals(GuiGraphics graphics, Minecraft mc, float cardX, float top,
                                     float health, float max, float absorb, float alpha) {
        int lively = UiVital.tone(HudLayout.tint(HudSlot.STATUS, UiAccent.color()));

        int shownHealth = Mth.ceil(health + absorb);
        int shownMax = Mth.ceil(max);
        cacheHealth(mc, shownHealth);
        cacheMax(mc, shownMax);

        float numberX = cardX + CARD_PADDING;
        UiRender.labelScaled(graphics, mc.font, healthLabel, numberX,
                UiRender.centerY(top, HEALTH_ROW, HEALTH_SCALE) - HEALTH_LIFT, HEALTH_SCALE,
                UiTheme.alpha(lively, alpha));

        float unitX = numberX + healthWidth * HEALTH_SCALE + 2.5f;
        UiRender.labelScaled(graphics, mc.font, maxLabel, unitX,
                UiRender.centerY(top, HEALTH_ROW * 0.86f, UNIT_SCALE), UNIT_SCALE,
                UiTheme.alpha(HudInk.textDim(), alpha));

        float traceLeft = unitX + maxWidth * UNIT_SCALE + PULSE_GAP;
        renderPulse(graphics, traceLeft, top, cardX + CARD_WIDTH - CARD_PADDING - traceLeft, lively, alpha);
    }

    private static void cacheHealth(Minecraft mc, int shown) {
        if (shown == healthValue && healthLabel != null) return;

        healthValue = shown;
        String text = String.valueOf(shown);
        healthLabel = Component.literal(text);
        healthWidth = UiRender.widthLabel(mc.font, text);
    }

    private static void cacheMax(Minecraft mc, int shown) {
        if (shown == maxValue && maxLabel != null) return;

        maxValue = shown;
        String text = "/" + shown;
        maxLabel = Component.literal(text);
        maxWidth = UiRender.width(mc.font, text);
    }

    private static void renderPulse(GuiGraphics graphics, float x, float top, float width, int color, float alpha) {
        if (width <= 8.0f) return;

        UiPulse.render(graphics, x, top, width, HEALTH_ROW, UiVital.phase(), PULSE_CYCLES, UiVital.vigor(),
                PULSE_WIDTH, color, alpha * UiVital.traceGain());
    }

    private static void renderDials(GuiGraphics graphics, Minecraft mc, LocalPlayer player, float centerX, float top,
                                    float alpha, float delta, int food, int armor, int air, int maxAir) {
        Entity vehicle = player.getVehicle();
        float mountShown = mountToggle.update(vehicle instanceof LivingEntity && vehicle.showVehicleHealth(), delta);
        float armorShown = armorToggle.update(armor > 0, delta);
        float airShown = airToggle.update(air < maxAir, delta);
        float staminaShown = readStamina(delta);
        clearFaded();

        float step = DIAL_SIZE + DIAL_GAP;
        float span = (1.0f + armorShown + airShown + staminaShown) * step - DIAL_GAP;
        float x = centerX - span / 2.0f;

        vitalDial(graphics, mc, x, top, alpha, delta, food, vehicle, mountShown);
        x += step;
        if (armorShown > 0.01f) {
            float value = armorFill.to(Math.min(1.0f, armor / 20.0f), delta);
            dial(graphics, mc, x, top, value, UiAccent.color(), alpha * armorShown, armorShown,
                    UiRender::iconShield, String.valueOf(armor));
            x += step * armorShown;
        }
        if (airShown > 0.01f) {
            float value = airFill.to(air / (float) maxAir, delta);
            dial(graphics, mc, x, top, value, alarmTint(air / (float) maxAir, air <= 0), alpha * airShown, airShown,
                    BottomHud::bubbleGlyph, Mth.ceil(value * 100.0f) + "%");
            x += step * airShown;
        }
        staminaDial(graphics, mc, x, top, alpha, delta, staminaShown);
    }

    private static void vitalDial(GuiGraphics graphics, Minecraft mc, float x, float top, float alpha, float delta,
                                  int food, Entity vehicle, float mountShown) {
        if (mountShown > 0.01f && vehicle instanceof LivingEntity mount) {
            float value = mountFill.to(mount.getHealth() / Math.max(1.0f, mount.getMaxHealth()), delta);
            dial(graphics, mc, x, top, value, UiAccent.color(), alpha * mountShown, mountShown,
                    UiRender::iconHeart, String.valueOf(Mth.ceil(mount.getHealth())));
        }
        if (mountShown >= 0.99f) return;

        float value = foodFill.to(food / 20.0f, delta);
        dial(graphics, mc, x, top, value, alarmTint(food / 20.0f, food <= 0), alpha * (1.0f - mountShown),
                1.0f - mountShown, UiRender::iconFood, String.valueOf(food));
    }

    private static void staminaDial(GuiGraphics graphics, Minecraft mc, float x, float top, float alpha,
                                    float delta, float shown) {
        if (shown <= 0.01f || lastStamina == null) return;

        float value = staminaFill.to(Mth.clamp(lastStamina.ratio(), 0.0f, 1.0f), delta);
        int tint = lastStamina.exhausted() ? UiTheme.mix(UiAccent.color(), UiPalette.alert(), 0.7f) : UiAccent.color();
        dial(graphics, mc, x, top, value, tint, alpha * shown, shown, UiRender::iconBolt,
                Mth.ceil(value * 100.0f) + "%");
    }

    private static float readStamina(float delta) {
        StaminaBridge.Reading stamina = StaminaBridge.current();
        float shown = staminaToggle.update(stamina != null && (stamina.ratio() < 0.995f || stamina.exhausted()), delta);
        if (stamina != null) lastStamina = stamina;
        return shown;
    }

    private static void clearFaded() {
        if (armorToggle.cleared()) armorFill.snap(0.0f);
        if (airToggle.cleared()) airFill.snap(0.0f);
        if (mountToggle.cleared()) mountFill.snap(0.0f);
        if (staminaToggle.cleared()) {
            lastStamina = null;
            staminaFill.snap(0.0f);
        }
    }

    private static void dial(GuiGraphics graphics, Minecraft mc, float x, float top, float value, int color,
                             float alpha, float presence, Glyph glyph, String label) {
        if (alpha <= 0.01f || presence <= 0.01f) return;

        float radius = DIAL_RADIUS * (0.72f + 0.28f * presence);
        float centerX = x + DIAL_SIZE / 2.0f;
        float centerY = top + DIAL_SIZE / 2.0f;

        UiRender.ring(graphics, centerX, centerY, radius, DIAL_THICKNESS, 1.0f,
                UiTheme.withAlpha(UiTheme.WHITE, 0.13f * alpha));
        UiRender.ring(graphics, centerX, centerY, radius, DIAL_THICKNESS, value, UiTheme.alpha(color, alpha));
        glyph.draw(graphics, centerX, centerY, DIAL_ICON, UiTheme.alpha(color, 0.92f * alpha));
        UiRender.labelCentered(graphics, mc.font, label, centerX, top + DIAL_SIZE + DIAL_LABEL_GAP,
                DIAL_LABEL_SCALE, UiTheme.alpha(HudInk.text(), alpha));
    }

    private static int alarmTint(float ratio, boolean drained) {
        if (drained) {
            return UiTheme.mix(UiAccent.color(), UiPalette.alert(), UiAnim.pulse(CRITICAL_BLINK_MS, 0.45f, 1.0f));
        }
        if (ratio > WARN_RATIO) return UiAccent.color();
        return UiTheme.mix(UiAccent.color(), WARN_COLOR, UiAnim.pulse(WARN_BLINK_MS, 0.30f, 1.0f));
    }

    private static void bubbleGlyph(GuiGraphics graphics, float centerX, float centerY, float size, int color) {
        UiRender.iconBubble(graphics, centerX, centerY, size, color, UiTheme.withAlpha(UiPalette.panel(), (color >>> 24) / 255.0f));
    }

    private static boolean anchoredBottom() {
        return HudLayout.of(HudSlot.STATUS).anchor().bottom();
    }

    private interface Glyph {
        void draw(GuiGraphics graphics, float centerX, float centerY, float size, int color);
    }

    private static void renderJumpBar(GuiGraphics graphics, LocalPlayer player, float left, float top, float delta) {
        float value = lineFill.to(player.getJumpRidingScale(), delta);
        UiGlass.sunken(graphics, left, top, BLOCK_WIDTH, 2.5f, 1.25f, 1.0f);
        UiGlass.progress(graphics, left, top, BLOCK_WIDTH, 2.5f, value, UiAccent.color(), 1.0f);
    }

    private static void renderItemName(GuiGraphics graphics, Minecraft mc, float screenWidth,
                                       float screenHeight, float bottom) {
        float alpha = HudState.highlightAlpha();
        if (alpha <= 0.01f || !HudLayout.visible(HudSlot.ITEM_NAME)) return;

        ItemStack stack = HudState.highlightStack();
        if (stack.isEmpty()) return;

        MutableComponent name = Component.empty().append(stack.getHoverName());
        if (stack.getRarity() != Rarity.COMMON) {
            name.withStyle(stack.getRarity().getStyleModifier());
        }
        if (stack.hasCustomHoverName()) {
            name.withStyle(ChatFormatting.ITALIC);
        }
        Component label = stack.getHighlightTip(name);

        float plateWidth = UiRender.widthLabel(mc.font, label) * NAME_SCALE + CARD_PADDING * 2.0f;
        float area = HudLayout.moved(HudSlot.ITEM_NAME) ? screenHeight : bottom;
        HudBox plate = HudLayout.place(HudSlot.ITEM_NAME, plateWidth, NAME_ROW, screenWidth, area);
        float plateX = plate.x();
        float plateY = plate.y() - (1.0f - UiAnim.easeOut(alpha)) * NAME_LIFT;
        alpha *= plate.alpha();

        HudLayout.push(graphics, plate);
        try {
            paintItemName(graphics, mc, label, plateX, plateY, plateWidth, alpha);
        } finally {
            HudLayout.pop(graphics, plate);
        }
    }

    public static void previewItemName(GuiGraphics graphics, float x, float y, float width, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        Component label = Component.translatable("battlecraft.custom.sample.item_name");
        float plateWidth = UiRender.widthLabel(mc.font, label) * NAME_SCALE + CARD_PADDING * 2.0f;
        HudLayout.sample(HudSlot.ITEM_NAME, plateWidth, NAME_ROW);
        paintItemName(graphics, mc, label, x, y, plateWidth, alpha);
    }

    private static void paintItemName(GuiGraphics graphics, Minecraft mc, Component label,
                                      float plateX, float plateY, float plateWidth, float alpha) {
        UiVital.card(graphics, plateX, plateY, plateWidth, NAME_ROW, alpha);
        UiRender.labelCentered(graphics, mc.font, label, plateX + plateWidth / 2.0f,
                UiRender.centerY(plateY, NAME_ROW, NAME_SCALE), NAME_SCALE,
                UiTheme.alpha(HudLayout.tint(HudSlot.ITEM_NAME, HudInk.text()), alpha));
    }
}
