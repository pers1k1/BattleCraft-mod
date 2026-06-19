package com.persiki84.shared;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class ModMessage {

    private ModMessage() {}

    public static MutableComponent prefixed(String modId, String key, Object... args) {
        return Component.translatable(modId + ".prefix").withStyle(ChatFormatting.GOLD)
                .append(Component.translatable(modId + "." + key, args));
    }

    public static MutableComponent success(String modId, String key, Object... args) {
        return prefixed(modId, key, args).withStyle(ChatFormatting.GREEN);
    }

    public static MutableComponent error(String modId, String key, Object... args) {
        return prefixed(modId, key, args).withStyle(ChatFormatting.RED);
    }

    public static MutableComponent warning(String modId, String key, Object... args) {
        return prefixed(modId, key, args).withStyle(ChatFormatting.YELLOW);
    }

    public static MutableComponent info(String modId, String key, Object... args) {
        return prefixed(modId, key, args).withStyle(ChatFormatting.GRAY);
    }

    public static MutableComponent header(String modId, String key, Object... args) {
        return Component.literal("=== ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.translatable(modId + "." + key, args).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" ===").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static MutableComponent withPrefix(Component prefix, String key, Object... args) {
        return prefix.copy().append(Component.translatable(key, args));
    }
}
