package com.persiki84.battlecraft.modules;

import com.persiki84.shared.FlagStore;

public final class ModuleSwitches {
    private static final FlagStore<ModuleId> STORE = new FlagStore<>("battlecraft-modules.json",
            ModuleId.values(), ModuleId::id, module -> Boolean.TRUE);

    private ModuleSwitches() {}

    // WHY: режим матча гасит модуль поверх выбора оператора, а не переписывает его: быстрый матч
    // WHY: без рудников не имеет права выключить карьер навсегда
    public static boolean allows(ModuleId module) {
        return STORE.allows(module) && !com.persiki84.battlecraft.mode.MatchScore.suppresses(module);
    }

    public static boolean stored(ModuleId module) {
        return STORE.allows(module);
    }

    public static void set(ModuleId module, boolean enabled) {
        STORE.set(module, enabled);
    }

    public static int mask() {
        int mask = STORE.mask();
        for (ModuleId module : ModuleId.values()) {
            if (com.persiki84.battlecraft.mode.MatchScore.suppresses(module)) mask &= ~(1 << module.ordinal());
        }
        return mask;
    }

    public static void load() {
        STORE.load();
    }
}
