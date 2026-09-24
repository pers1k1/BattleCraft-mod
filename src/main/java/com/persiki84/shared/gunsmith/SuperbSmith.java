package com.persiki84.shared.gunsmith;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class SuperbSmith {
    private static final String MOD_ID = "superbwarfare";
    private static final Map<String, String> SLOTS = slots();

    private static boolean probed;
    private static boolean available;

    private static Class<?> gunItem;
    private static Object companion;
    private static Method dataOf;
    private static Method save;
    private static Field attachmentField;
    private static Method setLevel;
    private static Method getLevel;
    private static Object[] slotTypes;
    private static Method defaultGunOf;
    private static Method consumersOf;
    private static Method weaponNameOf;
    private static Method consumerStack;
    private static Method consumerType;
    private static Method defaultVehicleOf;
    private static Method vehicleWeaponsOf;
    private static Method vehicleEnergyOf;
    private static Method batteryEnergyOf;
    private static Map<String, Integer> packs;

    private SuperbSmith() {}

    private static Map<String, String> slots() {
        Map<String, String> named = new LinkedHashMap<>();
        named.put("SCOPE", "getValidScopes");
        named.put("MAGAZINE", "getValidMagazines");
        named.put("BARREL", "getValidBarrels");
        named.put("STOCK", "getValidStocks");
        named.put("GRIP", "getValidGrips");
        return named;
    }

    static boolean available() {
        if (!probed) {
            probed = true;
            available = ModList.get().isLoaded(MOD_ID) && bind();
        }
        return available;
    }

    static boolean isGun(ItemStack stack) {
        return available() && !stack.isEmpty() && gunItem.isInstance(stack.getItem());
    }

    static List<String> options(ItemStack gun) {
        List<String> options = new ArrayList<>();
        if (!isGun(gun)) return options;

        for (Map.Entry<String, String> slot : SLOTS.entrySet()) {
            for (int level : levels(gun, slot.getValue())) {
                options.add(slot.getKey().toLowerCase(Locale.ROOT) + ":" + level);
            }
        }
        return options;
    }

    static List<GunAmmo> ammo(ItemStack gun) {
        if (!isGun(gun)) return List.of();

        try {
            List<String> rounds = roundsOf(defaultGunOf.invoke(companion, gun));
            return rounds.isEmpty() ? List.of() : List.of(new GunAmmo("", rounds));
        } catch (Throwable error) {
            SmithLog.trip(MOD_ID, error);
            return List.of();
        }
    }

    static List<GunAmmo> vehicleAmmo(EntityType<?> vehicle) {
        if (!available() || vehicle == null) return List.of();

        try {
            Map<?, ?> weapons = (Map<?, ?>) vehicleWeaponsOf.invoke(defaultVehicleOf.invoke(null, vehicle));
            List<GunAmmo> ammo = new ArrayList<>();
            for (Object weapon : weapons.values()) {
                addNamed(weapon, ammo);
            }
            List<String> loose = new ArrayList<>();
            for (Object weapon : weapons.values()) {
                addLoose(weapon, ammo, loose);
            }
            if (!loose.isEmpty()) ammo.add(new GunAmmo("", loose));
            return ammo;
        } catch (Throwable error) {
            SmithLog.trip(MOD_ID, error);
            return List.of();
        }
    }

    static VehiclePower vehiclePower(EntityType<?> vehicle) {
        if (!available() || vehicle == null) return null;

        try {
            int energy = (int) vehicleEnergyOf.invoke(defaultVehicleOf.invoke(null, vehicle));
            return energy <= 0 ? null : new VehiclePower(energy, cells(energy));
        } catch (Throwable error) {
            SmithLog.trip(MOD_ID, error);
            return null;
        }
    }

    private static List<VehiclePower.Cell> cells(int energy) {
        List<VehiclePower.Cell> cells = new ArrayList<>();
        int left = energy;

        for (Map.Entry<String, Integer> pack : packs().entrySet()) {
            int count = left / pack.getValue();
            if (count <= 0) continue;

            cells.add(new VehiclePower.Cell(pack.getKey(), count));
            left -= count * pack.getValue();
        }
        if (left > 0) topUp(cells);
        return cells;
    }

    // WHY: остаток меньше самой мелкой банки всё равно требует ещё одну, иначе бак не полон
    private static void topUp(List<VehiclePower.Cell> cells) {
        String smallest = null;
        for (String name : packs().keySet()) {
            smallest = name;
        }
        if (smallest == null) return;

        for (int index = 0; index < cells.size(); index++) {
            if (!cells.get(index).name().equals(smallest)) continue;

            cells.set(index, new VehiclePower.Cell(smallest, cells.get(index).count() + 1));
            return;
        }
        cells.add(new VehiclePower.Cell(smallest, 1));
    }

    // WHY: аккумуляторы ищутся отдельно от остального моста, чтобы их пропажа не гасила оружейку целиком
    private static Map<String, Integer> packs() {
        if (packs != null) return packs;

        try {
            Class<?> battery = Class.forName("com.atsuishio.superbwarfare.item.material.BatteryItem");
            batteryEnergyOf = battery.getMethod("getMaxEnergy");
            packs = sortedPacks(found(battery));
        } catch (Throwable error) {
            SmithLog.trip(MOD_ID, error);
            packs = Map.of();
        }
        return packs;
    }

    private static List<ItemStack> found(Class<?> battery) {
        List<ItemStack> found = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (battery.isInstance(item)) found.add(new ItemStack(item));
        }
        return found;
    }

    private static Map<String, Integer> sortedPacks(List<ItemStack> found) {
        Map<String, Integer> sorted = new LinkedHashMap<>();
        found.sort((first, second) -> Integer.compare(capacity(second), capacity(first)));

        for (ItemStack pack : found) {
            int capacity = capacity(pack);
            if (capacity > 0) sorted.put(pack.getHoverName().getString(), capacity);
        }
        return sorted;
    }

    private static int capacity(ItemStack pack) {
        try {
            return (int) batteryEnergyOf.invoke(pack.getItem());
        } catch (Throwable error) {
            SmithLog.trip(MOD_ID, error);
            return 0;
        }
    }

    private static void addNamed(Object weapon, List<GunAmmo> ammo) throws Exception {
        String name = weaponName(weapon);
        if (name.isEmpty()) return;

        List<String> rounds = roundsOf(weapon);
        if (rounds.isEmpty()) return;

        GunAmmo need = new GunAmmo(name, rounds);
        if (!ammo.contains(need)) ammo.add(need);
    }

    // WHY: пулемётные точки пассажиров идут в данных без названия и по шесть штук на машину
    private static void addLoose(Object weapon, List<GunAmmo> ammo, List<String> loose) throws Exception {
        if (!weaponName(weapon).isEmpty()) return;

        for (String round : roundsOf(weapon)) {
            if (!listed(ammo, round) && !loose.contains(round)) loose.add(round);
        }
    }

    private static boolean listed(List<GunAmmo> ammo, String round) {
        for (GunAmmo need : ammo) {
            if (need.rounds().contains(round)) return true;
        }
        return false;
    }

    private static String weaponName(Object weapon) throws Exception {
        String key = (String) weaponNameOf.invoke(weapon);
        if (key == null || key.isBlank()) return "";

        return Component.translatable(key, "").getString().trim();
    }

    private static List<String> roundsOf(Object gunDefaults) throws Exception {
        List<String> rounds = new ArrayList<>();
        for (Object consumer : (List<?>) consumersOf.invoke(gunDefaults)) {
            String round = roundName(consumer);
            if (!round.isEmpty() && !rounds.contains(round)) rounds.add(round);
        }
        return rounds;
    }

    private static String roundName(Object consumer) throws Exception {
        ItemStack round = (ItemStack) consumerStack.invoke(consumer);
        if (!round.isEmpty()) return round.getHoverName().getString();

        return switch (name(consumerType.invoke(consumer))) {
            case "ENERGY" -> Component.translatable("zones.gun.ammo.energy").getString();
            case "INFINITE" -> Component.translatable("zones.gun.ammo.infinite").getString();
            default -> "";
        };
    }

    static List<GunSlot> slots(ItemStack gun) {
        List<GunSlot> slots = new ArrayList<>();
        if (!isGun(gun)) return slots;

        for (Map.Entry<String, String> slot : SLOTS.entrySet()) {
            int[] levels = levels(gun, slot.getValue());
            if (levels.length == 0) continue;
            slots.add(describe(gun, slot.getKey(), levels));
        }
        return slots;
    }

    private static GunSlot describe(ItemStack gun, String slot, int[] levels) {
        String slotId = slot.toLowerCase(Locale.ROOT);
        List<GunSlot.GunOption> options = new ArrayList<>();
        for (int level : levels) {
            if (level > 0) options.add(new GunSlot.GunOption(slotId + ":" + level, levelLabel(level)));
        }

        int current = levelOf(gun, slot);
        return new GunSlot(slotId, "zones.attachment.slot." + slotId,
                current > 0 ? slotId + ":" + current : "", options);
    }

    private static String levelLabel(int level) {
        return Component.translatable("zones.attachment.level", level).getString();
    }

    private static int levelOf(ItemStack gun, String slot) {
        try {
            Object attachment = attachmentField.get(dataOf.invoke(companion, gun));
            Object type = typeOf(slot);
            return type == null ? 0 : (int) getLevel.invoke(attachment, type);
        } catch (Throwable error) {
            SmithLog.trip(MOD_ID, error);
            return 0;
        }
    }

    static Outcome detach(ItemStack gun, String slotId) {
        if (!isGun(gun)) return Outcome.NOT_A_GUN;
        return write(gun, slotId.toUpperCase(Locale.ROOT), 0);
    }

    static Outcome attach(ItemStack gun, String option) {
        if (!isGun(gun)) return Outcome.NOT_A_GUN;

        int split = option.indexOf(':');
        if (split <= 0) return Outcome.UNKNOWN;

        String slot = option.substring(0, split).toUpperCase(Locale.ROOT);
        String getter = SLOTS.get(slot);
        if (getter == null) return Outcome.UNKNOWN;

        int level = level(option.substring(split + 1));
        if (level < 0) return Outcome.UNKNOWN;
        if (!contains(levels(gun, getter), level)) return Outcome.INCOMPATIBLE;
        return write(gun, slot, level);
    }

    static List<String> installed(ItemStack gun) {
        List<String> installed = new ArrayList<>();
        if (!isGun(gun)) return installed;

        try {
            Object attachment = attachmentField.get(dataOf.invoke(companion, gun));
            for (Object type : slotTypes) {
                int level = (int) getLevel.invoke(attachment, type);
                if (level > 0) installed.add(name(type).toLowerCase(Locale.ROOT) + ":" + level);
            }
        } catch (Throwable error) {
            SmithLog.trip(MOD_ID, error);
        }
        return installed;
    }

    private static Outcome write(ItemStack gun, String slot, int level) {
        try {
            Object data = dataOf.invoke(companion, gun);
            Object attachment = attachmentField.get(data);
            Object type = typeOf(slot);
            if (type == null) return Outcome.UNKNOWN;

            setLevel.invoke(attachment, type, level);
            save.invoke(data);
            return Outcome.DONE;
        } catch (Throwable error) {
            SmithLog.trip(MOD_ID, error);
            return Outcome.UNKNOWN;
        }
    }

    private static int[] levels(ItemStack gun, String getter) {
        try {
            return (int[]) gunItem.getMethod(getter).invoke(gun.getItem());
        } catch (Throwable error) {
            return new int[0];
        }
    }

    private static Object typeOf(String slot) {
        for (Object type : slotTypes) {
            if (name(type).equals(slot)) return type;
        }
        return null;
    }

    private static String name(Object type) {
        return ((Enum<?>) type).name();
    }

    private static boolean contains(int[] levels, int wanted) {
        for (int level : levels) {
            if (level == wanted) return true;
        }
        return false;
    }

    private static int level(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException error) {
            return -1;
        }
    }

    private static boolean bind() {
        try {
            bindGun();
            bindAmmo();
            bindVehicle();
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    private static void bindGun() throws Exception {
        gunItem = Class.forName("com.atsuishio.superbwarfare.item.gun.GunItem");
        Class<?> gunData = Class.forName("com.atsuishio.superbwarfare.data.gun.GunData");
        Class<?> companionType = Class.forName("com.atsuishio.superbwarfare.data.gun.GunData$Companion");
        Class<?> attachment = Class.forName("com.atsuishio.superbwarfare.data.gun.subdata.Attachment");
        Class<?> type = Class.forName("com.atsuishio.superbwarfare.data.gun.value.AttachmentType");

        companion = gunData.getField("Companion").get(null);
        dataOf = companionType.getMethod("from", ItemStack.class);
        save = gunData.getMethod("save");
        attachmentField = gunData.getField("attachment");
        setLevel = attachment.getMethod("set", type, int.class);
        getLevel = attachment.getMethod("get", type);
        slotTypes = (Object[]) type.getMethod("values").invoke(null);
    }

    private static void bindAmmo() throws Exception {
        Class<?> companionType = Class.forName("com.atsuishio.superbwarfare.data.gun.GunData$Companion");
        Class<?> defaults = Class.forName("com.atsuishio.superbwarfare.data.gun.DefaultGunData");
        Class<?> consumer = Class.forName("com.atsuishio.superbwarfare.data.gun.AmmoConsumer");

        defaultGunOf = companionType.getMethod("getDefault", ItemStack.class);
        consumersOf = defaults.getMethod("getProcessedAmmoConsumers");
        weaponNameOf = defaults.getMethod("getName");
        consumerStack = consumer.getMethod("stack");
        consumerType = consumer.getMethod("getType");
    }

    private static void bindVehicle() throws Exception {
        Class<?> data = Class.forName("com.atsuishio.superbwarfare.data.vehicle.VehicleData");
        Class<?> defaults = Class.forName("com.atsuishio.superbwarfare.data.vehicle.DefaultVehicleData");

        defaultVehicleOf = data.getMethod("getDefault", EntityType.class);
        vehicleWeaponsOf = defaults.getMethod("weapons");
        vehicleEnergyOf = defaults.getMethod("getMaxEnergy");
    }
}
