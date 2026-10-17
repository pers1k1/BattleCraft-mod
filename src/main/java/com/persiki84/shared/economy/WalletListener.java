package com.persiki84.shared.economy;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public interface WalletListener {
    WalletListener NONE = new WalletListener() {};

    default void balanceChanged(ServerPlayer player) {}

    default void settingsChanged(MinecraftServer server) {}
}
