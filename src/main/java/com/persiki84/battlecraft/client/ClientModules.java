package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.modules.ModuleId;

public final class ClientModules {
    private static int enabled = all();

    private ClientModules() {}

    public static void accept(int mask) {
        enabled = mask;
    }

    public static void forget() {
        enabled = all();
    }

    public static boolean allows(ModuleId module) {
        return (enabled & 1 << module.ordinal()) != 0;
    }

    private static int all() {
        int mask = 0;
        for (ModuleId module : ModuleId.values()) {
            mask |= 1 << module.ordinal();
        }
        return mask;
    }
}
