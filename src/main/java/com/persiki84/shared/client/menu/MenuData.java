package com.persiki84.shared.client.menu;

import com.persiki84.shared.menu.MenuNetwork;
import com.persiki84.shared.menu.MenuStates;
import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;

public final class MenuData {
    private static final CompoundTag EMPTY = new CompoundTag();
    private static final long ASK_INTERVAL_MS = 200L;

    private static final Map<String, CompoundTag> states = new HashMap<>();
    private static final Map<String, Long> asked = new HashMap<>();

    private MenuData() {}

    public static void accept(String menuId, CompoundTag state, boolean opening) {
        states.put(menuId, state == null ? new CompoundTag() : state);
        if (opening) MenuScreens.open(menuId);
    }

    public static CompoundTag state(String menuId) {
        return states.getOrDefault(menuId, EMPTY);
    }

    public static boolean admin(String menuId) {
        return state(menuId).getBoolean(MenuStates.ADMIN_KEY);
    }

    public static void request(String menuId) {
        long now = System.currentTimeMillis();
        Long last = asked.get(menuId);
        if (last != null && now - last < ASK_INTERVAL_MS) return;

        asked.put(menuId, now);
        MenuNetwork.request(menuId);
    }

    public static void invalidate(String menuId) {
        asked.remove(menuId);
    }

    public static void forget() {
        states.clear();
        asked.clear();
    }
}
