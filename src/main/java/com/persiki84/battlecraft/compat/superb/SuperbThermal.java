package com.persiki84.battlecraft.compat.superb;

import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

public final class SuperbThermal {
    private static final String MOD_ID = "superbwarfare";

    private static boolean probed;
    private static Method active;

    private SuperbThermal() {}

    public static boolean imaging() {
        if (!probed) {
            probed = true;
            active = bind();
        }
        if (active == null) return false;

        try {
            return (boolean) active.invoke(null);
        } catch (Throwable error) {
            active = null;
            return false;
        }
    }

    private static Method bind() {
        if (!ModList.get().isLoaded(MOD_ID)) return null;
        try {
            return Class.forName("com.atsuishio.superbwarfare.client.shader.ThermalShaderHandler")
                    .getMethod("isActive");
        } catch (Throwable error) {
            return null;
        }
    }
}
