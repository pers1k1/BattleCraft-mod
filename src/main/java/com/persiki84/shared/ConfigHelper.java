package com.persiki84.shared;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ConfigHelper {

    private ConfigHelper() {}

    @SuppressWarnings("unchecked")
    public static void setAndSave(ForgeConfigSpec.ConfigValue<?> configValue, Object newValue, ForgeConfigSpec spec) {
        ((ForgeConfigSpec.ConfigValue<Object>) configValue).set(newValue);
        spec.save();
    }

    public static void save(ForgeConfigSpec spec) {
        spec.save();
    }
}
