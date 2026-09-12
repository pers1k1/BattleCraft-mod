package com.persiki84.battlecraft.client.hud;

import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

public final class HudState {
    private static final int HOTBAR_SLOTS = 9;
    private static final long NAME_HOLD_MS = 2200L;
    private static final long NAME_FADE_MS = 400L;

    private static final ItemStack[] tracked = new ItemStack[HOTBAR_SLOTS];
    private static ItemStack trackedOffhand = ItemStack.EMPTY;
    private static ItemStack highlight = ItemStack.EMPTY;
    private static int trackedSlot = -1;
    private static long lastActivity;
    private static long highlightSince;
    private static long lastSelectionChange;

    static {
        Arrays.fill(tracked, ItemStack.EMPTY);
    }

    private HudState() {}

    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            reset();
            return;
        }

        Inventory inventory = player.getInventory();

        if (inventory.selected != trackedSlot) {
            boolean known = trackedSlot >= 0;
            trackedSlot = inventory.selected;
            lastSelectionChange = System.currentTimeMillis();
            if (known) UiSound.slot();
            poke();
        }

        for (int slot = 0; slot < HOTBAR_SLOTS; slot++) {
            ItemStack current = inventory.items.get(slot);
            if (!sameStack(tracked[slot], current)) {
                tracked[slot] = current.copy();
                poke();
            }
        }

        ItemStack offhand = player.getOffhandItem();
        if (!sameStack(trackedOffhand, offhand)) {
            trackedOffhand = offhand.copy();
            poke();
        }

        if (mc.screen != null) {
            poke();
        }

        updateHighlight(inventory.getSelected());
    }

    public static void poke() {
        lastActivity = System.currentTimeMillis();
    }

    public static boolean hotbarWanted() {
        if (!HudConfig.hotbarAutoHide()) return true;
        return System.currentTimeMillis() - lastActivity < HudConfig.hotbarHideDelay();
    }

    public static float selectionPop() {
        float elapsed = System.currentTimeMillis() - lastSelectionChange;
        if (elapsed > 220.0f) return 0.0f;
        return 1.0f - UiAnim.easeOut(elapsed / 220.0f);
    }

    public static ItemStack highlightStack() {
        return highlight;
    }

    public static float highlightAlpha() {
        if (highlight.isEmpty()) return 0.0f;
        long elapsed = System.currentTimeMillis() - highlightSince;
        if (elapsed < NAME_HOLD_MS) return 1.0f;
        return UiAnim.clamp01(1.0f - (elapsed - NAME_HOLD_MS) / (float) NAME_FADE_MS);
    }

    private static void updateHighlight(ItemStack selected) {
        if (selected.isEmpty()) {
            highlight = ItemStack.EMPTY;
            return;
        }
        if (highlight.isEmpty() || !selected.is(highlight.getItem()) || !selected.getHoverName().equals(highlight.getHoverName())) {
            highlightSince = System.currentTimeMillis();
        }
        highlight = selected.copy();
    }

    private static void reset() {
        Arrays.fill(tracked, ItemStack.EMPTY);
        trackedOffhand = ItemStack.EMPTY;
        highlight = ItemStack.EMPTY;
        trackedSlot = -1;
    }

    private static boolean sameStack(ItemStack first, ItemStack second) {
        return first.getItem() == second.getItem()
                && first.getCount() == second.getCount()
                && first.getDamageValue() == second.getDamageValue();
    }
}
