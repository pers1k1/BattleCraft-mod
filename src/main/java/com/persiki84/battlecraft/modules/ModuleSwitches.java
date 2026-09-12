package com.persiki84.battlecraft.modules;

import com.persiki84.shared.FlagStore;

public final class ModuleSwitches {
    private static final FlagStore<ModuleId> STORE = new FlagStore<>("battlecraft-modules.json",
            ModuleId.values(), ModuleId::id, module -> Boolean.TRUE);

    private ModuleSwitches() {}

    public static boolean allows(ModuleId module) {
        return STORE.allows(module);
    }

    public static void set(ModuleId module, boolean enabled) {
        STORE.set(module, enabled);
    }

    public static int mask() {
        return STORE.mask();
    }

    public static void load() {
        STORE.load();
    }
}
