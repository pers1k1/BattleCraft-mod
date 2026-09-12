package com.persiki84.shared.gunsmith;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

public final class SmithLog {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> reported = new HashSet<>();

    private SmithLog() {}

    // WHY: раньше любая осечка ставила available = false и молча выключала мост до перезапуска
    // WHY: игры: меню оружейника пустело без единой строки в логе. Осечка это не несовместимость
    public static void trip(String mod, Throwable error) {
        if (!reported.add(mod)) return;

        LOGGER.warn("[gunsmith] {} call failed, keeping the bridge alive: {}", mod, error.toString());
    }
}
