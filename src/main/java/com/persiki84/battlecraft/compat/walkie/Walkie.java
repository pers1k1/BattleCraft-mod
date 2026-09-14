package com.persiki84.battlecraft.compat.walkie;

import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

// WHY: маршрут голоса рации решает чужой мод, и повторять его условия своими руками значит
// WHY: показывать в худе не тех, кого слышно: мост зовёт те же методы, что и плагин рации
public final class Walkie {
    public static final String MOD_ID = "walkietalkie";

    private static final String NBT_CANAL = "walkietalkie.canal";
    private static final String NBT_MUTE = "walkietalkie.mute";
    private static final String NBT_ACTIVATE = "walkietalkie.activate";

    private static boolean probed;
    private static boolean available;

    private static Class<?> radioItem;
    private static Method inHand;
    private static Method activated;
    private static Method canReach;
    private static Method rangeOf;
    private static Field crossDimensions;
    private static Field dimensionScale;

    private Walkie() {}

    public static boolean available() {
        if (!probed) {
            probed = true;
            available = ModList.get().isLoaded(MOD_ID) && bind();
        }
        return available;
    }

    public static ItemStack held(Player player) {
        if (!available() || player == null) return ItemStack.EMPTY;
        try {
            ItemStack stack = (ItemStack) inHand.invoke(null, player);
            return stack == null ? ItemStack.EMPTY : stack;
        } catch (Throwable error) {
            fail(error);
            return ItemStack.EMPTY;
        }
    }

    public static ItemStack listening(Player player) {
        if (!available() || player == null) return ItemStack.EMPTY;
        try {
            ItemStack stack = (ItemStack) activated.invoke(null, player);
            return stack == null ? ItemStack.EMPTY : stack;
        } catch (Throwable error) {
            fail(error);
            return ItemStack.EMPTY;
        }
    }

    // WHY: чужой плагин шлёт голос только с рации в руке, включённой и не заглушённой:
    // WHY: карточка своей передачи обязана появляться ровно по этому же условию
    public static ItemStack transmitting(Player player) {
        ItemStack stack = held(player);
        if (stack.isEmpty() || !active(stack) || muted(stack)) return ItemStack.EMPTY;
        return stack;
    }

    public static boolean active(ItemStack stack) {
        return stack.hasTag() && stack.getOrCreateTag().getBoolean(NBT_ACTIVATE);
    }

    public static boolean muted(ItemStack stack) {
        return stack.hasTag() && stack.getOrCreateTag().getBoolean(NBT_MUTE);
    }

    public static int canal(ItemStack stack) {
        return stack.hasTag() ? stack.getOrCreateTag().getInt(NBT_CANAL) : -1;
    }

    public static int range(ItemStack stack) {
        if (!available() || stack.isEmpty() || !radioItem.isInstance(stack.getItem())) return 0;
        try {
            return (int) rangeOf.invoke(stack.getItem());
        } catch (Throwable error) {
            fail(error);
            return 0;
        }
    }

    public static boolean reaches(Level from, Level to, Vec3 source, Vec3 target, int range) {
        if (!available()) return false;
        try {
            return (boolean) canReach.invoke(null, from, to, source, target, range);
        } catch (Throwable error) {
            fail(error);
            return false;
        }
    }

    public static boolean crossDimensions() {
        if (!available()) return false;
        try {
            return crossDimensions.getBoolean(null);
        } catch (Throwable error) {
            fail(error);
            return false;
        }
    }

    // WHY: у чужого мода дальность делится на масштаб измерения, поэтому в Незере рация бьёт
    // WHY: втрое ближе по своим координатам: сила сигнала обязана считаться по той же величине,
    // WHY: иначе помехи кончаются там, где связь уже пропала
    public static double effectiveRange(Level from, Level to, int range) {
        if (!available() || from == null || to == null) return range;
        try {
            if (!dimensionScale.getBoolean(null)) return range;
        } catch (Throwable error) {
            fail(error);
            return range;
        }

        double scale = Math.max(from.dimensionType().coordinateScale(), to.dimensionType().coordinateScale());
        return scale <= 0.0 ? range : range / scale;
    }

    private static boolean bind() {
        try {
            Class<?> util = Class.forName("fr.flaton.walkietalkie.Util");
            Class<?> config = Class.forName("fr.flaton.walkietalkie.config.ModConfig");

            radioItem = Class.forName("fr.flaton.walkietalkie.item.WalkieTalkieItem");
            inHand = util.getMethod("getWalkieTalkieInHand", Player.class);
            activated = util.getMethod("getWalkieTalkieActivated", Player.class);
            canReach = util.getMethod("canBroadcastToReceiver", Level.class, Level.class,
                    Vec3.class, Vec3.class, int.class);
            rangeOf = radioItem.getMethod("getRange");
            crossDimensions = config.getField("crossDimensionsEnabled");
            dimensionScale = config.getField("applyDimensionScale");
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    private static void fail(Throwable error) {
        available = false;
        BattleCraftMod.LOGGER.warn("Мост к рации отключён: {}", error.toString());
    }
}
