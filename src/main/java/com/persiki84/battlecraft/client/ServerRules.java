package com.persiki84.battlecraft.client;

import net.minecraft.client.Minecraft;

public final class ServerRules {

    private ServerRules() {}

    public static boolean restricted() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && !mc.hasSingleplayerServer();
    }
}
