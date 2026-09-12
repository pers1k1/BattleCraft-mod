package com.persiki84.battlecraft.compat.goggles;

import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class Goggles {
    public static final String MOD_ID = "vision_goggles";

    public static final String TAG_ACTIVE = "nvg_active";
    public static final String TAG_MODE = "vision_mode";
    public static final String TAG_BATTERY = "nvg_battery";

    public static final String MODULE_ZOOM = "ZOOM";
    public static final int MODE_NONE = -1;

    private static final String[] SHADERS = {
            "shaders/post/nvg.json",
            "shaders/post/thermal.json",
            "shaders/post/hydro.json",
            "shaders/post/bio.json"
    };

    private static final String[] MODE_KEYS = {
            "battlecraft.goggles.mode.night",
            "battlecraft.goggles.mode.thermal",
            "battlecraft.goggles.mode.hydro",
            "battlecraft.goggles.mode.bio"
    };

    private static boolean probed;
    private static boolean available;

    private static Class<?> gogglesItem;
    private static Class<?> modularItem;
    private static Method utilityModules;
    private static Method batteryCapacity;
    private static Method moduleId;
    private static Method curiosHelper;
    private static Method findFirstCurio;
    private static Method resultStack;

    private Goggles() {}

    public static boolean available() {
        if (!probed) {
            probed = true;
            available = ModList.get().isLoaded(MOD_ID) && ModList.get().isLoaded("curios") && bind();
        }
        return available;
    }

    public static ItemStack equipped(Player player) {
        if (!available() || player == null) return ItemStack.EMPTY;
        try {
            Predicate<ItemStack> wanted = stack -> gogglesItem.isInstance(stack.getItem());
            Object found = ((Optional<?>) findFirstCurio.invoke(curiosHelper.invoke(null), player, wanted))
                    .orElse(null);
            return found == null ? ItemStack.EMPTY : (ItemStack) resultStack.invoke(found);
        } catch (Throwable error) {
            available = false;
            return ItemStack.EMPTY;
        }
    }

    public static boolean isGoggles(ItemStack stack) {
        return available() && !stack.isEmpty() && gogglesItem.isInstance(stack.getItem());
    }

    public static boolean isActive(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_ACTIVE);
    }

    public static int modeOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(TAG_MODE) ? MODE_NONE : tag.getInt(TAG_MODE);
    }

    public static float batteryOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        float capacity = capacityOf(stack);
        if (capacity <= 0.0f) return 0.0f;
        if (tag == null || !tag.contains(TAG_BATTERY)) return 1.0f;
        return Math.max(0.0f, Math.min(1.0f, tag.getFloat(TAG_BATTERY) / capacity));
    }

    public static float capacityOf(ItemStack stack) {
        if (!isGoggles(stack)) return 0.0f;
        try {
            return ((Number) batteryCapacity.invoke(stack.getItem(), stack)).floatValue();
        } catch (Throwable error) {
            return 0.0f;
        }
    }

    public static boolean hasModule(ItemStack stack, String id) {
        if (!isGoggles(stack) || !modularItem.isInstance(stack.getItem())) return false;
        try {
            for (Object module : (List<?>) utilityModules.invoke(stack.getItem(), stack)) {
                if (id.equals(moduleId.invoke(module))) return true;
            }
        } catch (Throwable error) {
            available = false;
        }
        return false;
    }

    public static ResourceLocation shaderOf(int mode, boolean underWater) {
        if (mode < 0 || mode >= SHADERS.length) return null;
        if (mode == 2 && !underWater) return new ResourceLocation(MOD_ID, "shaders/post/hydro_dry.json");
        return new ResourceLocation(MOD_ID, SHADERS[mode]);
    }

    public static String modeKey(int mode) {
        return mode < 0 || mode >= MODE_KEYS.length ? null : MODE_KEYS[mode];
    }

    private static boolean bind() {
        try {
            gogglesItem = Class.forName("dev.itsrealperson.vision_goggles.item.VisionGogglesItem");
            modularItem = Class.forName("dev.itsrealperson.vision_goggles.item.ModularGogglesItem");
            Class<?> moduleType = Class.forName("dev.itsrealperson.vision_goggles.util.ModuleType");
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class<?> helper = Class.forName("top.theillusivec4.curios.api.type.util.ICuriosHelper");
            Class<?> slotResult = Class.forName("top.theillusivec4.curios.api.SlotResult");

            batteryCapacity = gogglesItem.getMethod("getBatteryCapacity", ItemStack.class);
            utilityModules = modularItem.getMethod("getUtilityModules", ItemStack.class);
            moduleId = moduleType.getMethod("getId");
            curiosHelper = api.getMethod("getCuriosHelper");
            findFirstCurio = helper.getMethod("findFirstCurio", LivingEntity.class, Predicate.class);
            resultStack = slotResult.getMethod("stack");
            return true;
        } catch (Throwable error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] vision goggles bridge is not bound: {}", error.toString());
            return false;
        }
    }
}
