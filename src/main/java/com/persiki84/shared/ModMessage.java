package com.persiki84.shared;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class ModMessage {

    private ModMessage() {}

    public static MutableComponent prefixed(String modId, String key, Object... args) {
        return Component.translatable(modId + ".prefix").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.translatable(modId + "." + key, args));
    }

    public static MutableComponent error(String modId, String key, Object... args) {
        return prefixed(modId, key, args).withStyle(ChatFormatting.RED);
    }

}
