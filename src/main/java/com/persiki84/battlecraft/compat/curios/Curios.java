package com.persiki84.battlecraft.compat.curios;

import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

// WHY: Curios не лежит на classpath сборки, а без него не работают ни устройство свой-чужой,
// WHY: ни очистка слотов: мост связывает каждую способность отдельно и молчит, когда мода нет
public final class Curios {
    public static final String MOD_ID = "curios";

    private static boolean probed;
    private static boolean available;

    private static Method inventoryOf;
    private static Method resolveLazy;
    private static Method curiosMap;
    private static Method stacksHandler;
    private static Method stacks;
    private static Method cosmeticStacks;
    private static Method slotCount;
    private static Method stackInSlot;
    private static Method setStackInSlot;
    private static Method setEquipped;
    private static Method findFirst;

    private Curios() {}

    public static boolean available() {
        if (!probed) {
            probed = true;
            available = ModList.get().isLoaded(MOD_ID) && bind();
        }
        return available;
    }

    public static boolean isEquipped(LivingEntity wearer, Item item) {
        Object handler = handlerOf(wearer);
        if (handler == null) return false;
        try {
            return ((Optional<?>) findFirst.invoke(handler, item)).isPresent();
        } catch (Throwable error) {
            return fail(error);
        }
    }

    public static boolean equip(LivingEntity wearer, String slot, ItemStack stack) {
        Object handler = handlerOf(wearer);
        if (handler == null) return false;
        try {
            Object holder = ((Optional<?>) stacksHandler.invoke(handler, slot)).orElse(null);
            if (holder == null) return false;

            Object room = stacks.invoke(holder);
            if ((int) slotCount.invoke(room) <= 0) return false;

            setEquipped.invoke(handler, slot, 0, stack);
            return true;
        } catch (Throwable error) {
            return fail(error);
        }
    }

    // WHY: ванильный /clear ходит только по инвентарю, поэтому надетый в Curios предмет
    // WHY: переживал очистку; счёт возвращается тот же, что у ванили, - число снятых штук
    public static int clearOrCount(LivingEntity wearer, Predicate<ItemStack> wanted, int limit) {
        int[] taken = {0};
        walk(wearer, (holder, index, stack) -> {
            if (limit == 0) {
                if (wanted.test(stack)) taken[0] += stack.getCount();
                return;
            }
            if (limit > 0 && taken[0] >= limit) return;
            if (!wanted.test(stack)) return;

            int portion = limit < 0 ? stack.getCount() : Math.min(stack.getCount(), limit - taken[0]);
            taken[0] += portion;
            put(holder, index, portion >= stack.getCount()
                    ? ItemStack.EMPTY
                    : shrunk(stack, portion));
        });
        return taken[0];
    }

    public static int take(LivingEntity wearer, Predicate<ItemStack> wanted) {
        return clearOrCount(wearer, wanted, -1);
    }

    private static ItemStack shrunk(ItemStack stack, int portion) {
        ItemStack left = stack.copy();
        left.shrink(portion);
        return left;
    }

    private interface SlotVisitor {
        void accept(Object holder, int index, ItemStack stack);
    }

    private static void walk(LivingEntity wearer, SlotVisitor visitor) {
        Object handler = handlerOf(wearer);
        if (handler == null) return;
        try {
            for (Object holder : ((Map<?, ?>) curiosMap.invoke(handler)).values()) {
                walkHolder(stacks.invoke(holder), visitor);
                walkHolder(cosmeticStacks.invoke(holder), visitor);
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    private static void walkHolder(Object room, SlotVisitor visitor) throws Exception {
        int count = (int) slotCount.invoke(room);
        for (int index = 0; index < count; index++) {
            ItemStack stack = (ItemStack) stackInSlot.invoke(room, index);
            if (!stack.isEmpty()) visitor.accept(room, index, stack);
        }
    }

    private static void put(Object room, int index, ItemStack stack) {
        try {
            setStackInSlot.invoke(room, index, stack);
        } catch (Throwable error) {
            fail(error);
        }
    }

    private static Object handlerOf(LivingEntity wearer) {
        if (!available() || wearer == null) return null;
        try {
            return ((Optional<?>) resolveLazy.invoke(inventoryOf.invoke(null, wearer))).orElse(null);
        } catch (Throwable error) {
            fail(error);
            return null;
        }
    }

    private static boolean fail(Throwable error) {
        available = false;
        BattleCraftMod.LOGGER.warn("[battlecraft] curios bridge stopped: {}", error.toString());
        return false;
    }

    private static boolean bind() {
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Class<?> itemHandler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            Class<?> stacksType = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
            Class<?> dynamic = Class.forName("top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler");
            Class<?> lazy = Class.forName("net.minecraftforge.common.util.LazyOptional");

            inventoryOf = api.getMethod("getCuriosInventory", LivingEntity.class);
            resolveLazy = lazy.getMethod("resolve");
            curiosMap = itemHandler.getMethod("getCurios");
            stacksHandler = itemHandler.getMethod("getStacksHandler", String.class);
            setEquipped = itemHandler.getMethod("setEquippedCurio", String.class, int.class, ItemStack.class);
            findFirst = itemHandler.getMethod("findFirstCurio", Item.class);
            stacks = stacksType.getMethod("getStacks");
            cosmeticStacks = stacksType.getMethod("getCosmeticStacks");
            slotCount = dynamic.getMethod("getSlots");
            stackInSlot = dynamic.getMethod("getStackInSlot", int.class);
            setStackInSlot = dynamic.getMethod("setStackInSlot", int.class, ItemStack.class);
            return true;
        } catch (Throwable error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] curios bridge is not bound: {}", error.toString());
            return false;
        }
    }
}
