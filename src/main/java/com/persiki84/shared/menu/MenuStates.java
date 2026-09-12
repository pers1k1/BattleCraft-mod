package com.persiki84.shared.menu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class MenuStates {
    public static final String ADMIN_KEY = "admin";

    private static final Map<String, Source> sources = new HashMap<>();

    private MenuStates() {}

    public static void register(String menuId, int permission, Function<ServerPlayer, CompoundTag> snapshot) {
        sources.put(menuId, new Source(permission, snapshot));
    }

    public static CompoundTag snapshot(String menuId, ServerPlayer player) {
        Source source = sources.get(menuId);
        if (source == null || !player.hasPermissions(source.permission())) return null;

        CompoundTag tag = source.snapshot().apply(player);
        tag.putBoolean(ADMIN_KEY, player.hasPermissions(2));
        return tag;
    }

    private record Source(int permission, Function<ServerPlayer, CompoundTag> snapshot) {}
}
