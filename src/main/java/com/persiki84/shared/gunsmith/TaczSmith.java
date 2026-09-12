package com.persiki84.shared.gunsmith;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class TaczSmith {
    private static final String MOD_ID = "tacz";
    private static final String NO_SLOT = "NONE";

    private static boolean probed;
    private static boolean available;

    private static Method gunOf;
    private static Method allow;
    private static Method allowType;
    private static Method install;
    private static Method unload;
    private static Method attachmentIdOf;
    private static Method catalog;
    private static Method indexType;
    private static Method indexOf;
    private static Method dataOf;
    private static Method modifiersOf;
    private static Method componentsOf;
    private static Method initComponents;
    private static Method builder;
    private static Method builderId;
    private static Method builderBuild;
    private static Method gunIdOf;
    private static Method gunIndexOf;
    private static Method gunDataOf;
    private static Method bulletDataOf;
    private static Method ammoIdOf;
    private static Method ammoBuilder;
    private static Method ammoBuilderId;
    private static Method ammoBuilderBuild;
    private static Method ammoAmount;
    private static Method rpmOf;
    private static Method aimTimeOf;
    private static Method weightOf;
    private static Method damageOf;
    private static Method speedOf;
    private static Method pierceOf;
    private static Method knockbackOf;
    private static Method sprintTimeOf;
    private static Method inaccuracyOf;
    private static Method extraDamageOf;
    private static Method armorIgnoreOf;
    private static Method headshotOf;
    private static Method emptyAttachment;
    private static Object[] inaccuracyTypes;
    private static Object[] attachmentTypes;

    private TaczSmith() {}

    static boolean available() {
        if (!probed) {
            probed = true;
            available = ModList.get().isLoaded(MOD_ID) && bind();
        }
        return available;
    }

    static boolean isGun(ItemStack stack) {
        if (!available() || stack.isEmpty()) return false;
        try {
            return gunOf.invoke(null, stack) != null;
        } catch (Throwable error) {
            SmithLog.trip(MOD_ID, error);
            return false;
        }
    }

    static List<GunSlot> slots(ItemStack gun) {
        List<GunSlot> slots = new ArrayList<>();
        if (!isGun(gun)) return slots;

        try {
            Object weapon = gunOf.invoke(null, gun);
            for (Object type : attachmentTypes) {
                if (name(type).equals(NO_SLOT)) continue;
                if (!(boolean) allowType.invoke(weapon, gun, type)) continue;
                slots.add(slot(weapon, gun, type));
            }
        } catch (Throwable error) {
            SmithLog.trip(MOD_ID, error);
        }
        return slots;
    }

    private static GunSlot slot(Object weapon, ItemStack gun, Object type) throws Exception {
        ResourceLocation current = (ResourceLocation) attachmentIdOf.invoke(weapon, gun, type);
        String slotId = name(type).toLowerCase(Locale.ROOT);
        return new GunSlot(slotId, "zones.attachment.slot." + slotId,
                filled(current) ? current.toString() : "", fitting(weapon, gun, type));
    }

    private static boolean filled(ResourceLocation id) throws Exception {
        return id != null && !id.getPath().isEmpty() && !(boolean) emptyAttachment.invoke(null, id);
    }

    private static List<GunSlot.GunOption> fitting(Object weapon, ItemStack gun, Object type) throws Exception {
        List<GunSlot.GunOption> options = new ArrayList<>();
        for (Object entry : (Set<?>) catalog.invoke(null)) {
            ResourceLocation id = (ResourceLocation) ((Map.Entry<?, ?>) entry).getKey();
            Object index = ((Map.Entry<?, ?>) entry).getValue();
            if (indexType.invoke(index) != type) continue;

            ItemStack attachment = attachmentStack(id);
            if (attachment.isEmpty() || !(boolean) allow.invoke(weapon, gun, attachment)) continue;
            options.add(new GunSlot.GunOption(id.toString(), attachment.getHoverName().getString()));
        }
        options.sort((left, right) -> left.label().compareToIgnoreCase(right.label()));
        return options;
    }

    static Outcome attach(ItemStack gun, String option) {
        if (!isGun(gun)) return Outcome.NOT_A_GUN;

        ResourceLocation id = ResourceLocation.tryParse(option);
        if (id == null) return Outcome.UNKNOWN;

        try {
            ItemStack attachment = attachmentStack(id);
            if (attachment.isEmpty()) return Outcome.UNKNOWN;

            Object weapon = gunOf.invoke(null, gun);
            if (!(boolean) allow.invoke(weapon, gun, attachment)) return Outcome.INCOMPATIBLE;

            install.invoke(weapon, gun, attachment);
            return Outcome.DONE;
        } catch (Throwable error) {
            SmithLog.trip(MOD_ID, error);
            return Outcome.UNKNOWN;
        }
    }

    static Outcome detach(ItemStack gun, String slotId) {
        if (!isGun(gun)) return Outcome.NOT_A_GUN;

        try {
            Object type = typeOf(slotId);
            if (type == null) return Outcome.UNKNOWN;

            unload.invoke(gunOf.invoke(null, gun), gun, type);
            return Outcome.DONE;
        } catch (Throwable error) {
            SmithLog.trip(MOD_ID, error);
            return Outcome.UNKNOWN;
        }
    }

    static ItemStack preview(String option) {
        ResourceLocation id = attachmentId(option);
        if (id == null) return ItemStack.EMPTY;

        try {
            return attachmentStack(id);
        } catch (Throwable error) {
            SmithLog.trip(MOD_ID, error);
            return ItemStack.EMPTY;
        }
    }

    private static ResourceLocation attachmentId(String option) {
        if (!available() || option == null || option.isBlank()) return null;

        ResourceLocation id = ResourceLocation.tryParse(option);
        return id == null || id.getPath().isEmpty() ? null : id;
    }

    static List<String> notes(String option) {
        ResourceLocation id = attachmentId(option);
        if (id == null) return List.of();

        try {
            Object index = ((Optional<?>) indexOf.invoke(null, id)).orElse(null);
            return index == null ? List.of() : modifierNotes(dataOf.invoke(index));
        } catch (Throwable error) {
            SmithLog.trip(MOD_ID, error);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> modifierNotes(Object data) throws Exception {
        List<String> notes = new ArrayList<>();
        Map<String, Object> modifiers = (Map<String, Object>) modifiersOf.invoke(data);
        for (Object property : modifiers.values()) {
            notes.addAll(propertyNotes(property));
        }
        return notes;
    }

    @SuppressWarnings("unchecked")
    private static List<String> propertyNotes(Object property) throws Exception {
        List<Component> lines = (List<Component>) componentsOf.invoke(property);
        if (lines == null || lines.isEmpty()) {
            initComponents.invoke(property);
            lines = (List<Component>) componentsOf.invoke(property);
        }

        List<String> notes = new ArrayList<>();
        for (Component line : lines == null ? List.<Component>of() : lines) {
            String text = line.getString().trim();
            if (!text.isEmpty()) notes.add(text);
        }
        return notes;
    }

    static List<GunStat> stats(ItemStack gun) {
        if (!isGun(gun)) return List.of();

        try {
            Object index = gunIndex(gun);
            if (index == null) return List.of();

            return describe(gunDataOf.invoke(index), bulletDataOf.invoke(index));
        } catch (Throwable error) {
            SmithLog.trip(MOD_ID, error);
            return List.of();
        }
    }

    static List<GunAmmo> ammo(ItemStack gun) {
        if (!isGun(gun)) return List.of();

        try {
            Object index = gunIndex(gun);
            if (index == null) return List.of();

            String round = ammoName((ResourceLocation) ammoIdOf.invoke(gunDataOf.invoke(index)));
            return round.isEmpty() ? List.of() : List.of(new GunAmmo("", List.of(round)));
        } catch (Throwable error) {
            SmithLog.trip(MOD_ID, error);
            return List.of();
        }
    }

    private static Object gunIndex(ItemStack gun) throws Exception {
        ResourceLocation id = (ResourceLocation) gunIdOf.invoke(gunOf.invoke(null, gun), gun);
        return id == null ? null : ((Optional<?>) gunIndexOf.invoke(null, id)).orElse(null);
    }

    private static String ammoName(ResourceLocation id) throws Exception {
        if (id == null || id.getPath().isEmpty()) return "";

        Object made = ammoBuilder.invoke(null);
        ammoBuilderId.invoke(made, id);
        ItemStack round = (ItemStack) ammoBuilderBuild.invoke(made);
        return round.isEmpty() ? "" : round.getHoverName().getString();
    }

    private static List<GunStat> describe(Object gunData, Object bullet) throws Exception {
        List<GunStat> stats = new ArrayList<>();
        stats.add(stat("ammo", number((int) ammoAmount.invoke(gunData))));
        stats.add(stat("sprint_time", number((float) sprintTimeOf.invoke(gunData)) + "s"));
        stats.add(stat("aim_time", number((float) aimTimeOf.invoke(gunData)) + "s"));
        stats.add(stat("speed", number((float) speedOf.invoke(bullet)) + " m/s"));
        addDamage(stats, bullet);
        addSpread(stats, gunData);
        stats.add(stat("knockback", number((float) knockbackOf.invoke(bullet))));
        stats.add(stat("pierce", number((int) pierceOf.invoke(bullet))));
        stats.add(stat("rpm", number((int) rpmOf.invoke(gunData)) + " rpm"));
        stats.add(stat("weight", number((float) weightOf.invoke(gunData)) + " kg"));
        return stats;
    }

    private static void addDamage(List<GunStat> stats, Object bullet) throws Exception {
        Object extra = extraDamageOf.invoke(bullet);
        stats.add(stat("armor_ignore", percent((float) armorIgnoreOf.invoke(extra))));
        stats.add(stat("damage", number((float) damageOf.invoke(bullet))));
        stats.add(stat("headshot", "x" + number((float) headshotOf.invoke(extra))));
    }

    private static void addSpread(List<GunStat> stats, Object gunData) throws Exception {
        stats.add(stat("spread_hip", number(inaccuracy(gunData, "STAND"))));
        stats.add(stat("spread_sneak", number(inaccuracy(gunData, "SNEAK"))));
        stats.add(stat("spread_lie", number(inaccuracy(gunData, "LIE"))));
        stats.add(stat("spread_aim", number(inaccuracy(gunData, "AIM"))));
    }

    private static float inaccuracy(Object gunData, String type) throws Exception {
        for (Object value : inaccuracyTypes) {
            if (name(value).equals(type)) return (float) inaccuracyOf.invoke(gunData, value);
        }
        return 0.0f;
    }

    private static String percent(float value) {
        return number(value * 100.0f) + "%";
    }

    private static GunStat stat(String name, String value) {
        return new GunStat("zones.gun.stat." + name, value);
    }

    private static String number(float value) {
        return value == Math.round(value) ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%.2f", value);
    }

    private static String number(int value) {
        return String.valueOf(value);
    }

    static List<String> options(ItemStack gun) {
        List<String> ids = new ArrayList<>();
        for (GunSlot slot : slots(gun)) {
            for (GunSlot.GunOption option : slot.options()) {
                ids.add(option.value());
            }
        }
        return ids;
    }

    static List<String> installed(ItemStack gun) {
        List<String> ids = new ArrayList<>();
        for (GunSlot slot : slots(gun)) {
            if (!slot.installed().isEmpty()) ids.add(slot.installed());
        }
        return ids;
    }

    private static Object typeOf(String slotId) {
        for (Object type : attachmentTypes) {
            if (name(type).equalsIgnoreCase(slotId)) return type;
        }
        return null;
    }

    private static String name(Object type) {
        return ((Enum<?>) type).name();
    }

    private static ItemStack attachmentStack(ResourceLocation id) throws Exception {
        Object made = builder.invoke(null);
        builderId.invoke(made, id);
        return (ItemStack) builderBuild.invoke(made);
    }

    private static boolean bind() {
        try {
            bindGun();
            bindCatalog();
            bindStats();
            bindBuilder();
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    private static void bindGun() throws Exception {
        Class<?> iGun = Class.forName("com.tacz.guns.api.item.IGun");
        Class<?> type = Class.forName("com.tacz.guns.api.item.attachment.AttachmentType");

        gunOf = iGun.getMethod("getIGunOrNull", ItemStack.class);
        allow = iGun.getMethod("allowAttachment", ItemStack.class, ItemStack.class);
        allowType = iGun.getMethod("allowAttachmentType", ItemStack.class, type);
        install = iGun.getMethod("installAttachment", ItemStack.class, ItemStack.class);
        unload = iGun.getMethod("unloadAttachment", ItemStack.class, type);
        attachmentIdOf = iGun.getMethod("getAttachmentId", ItemStack.class, type);
        attachmentTypes = (Object[]) type.getMethod("values").invoke(null);
    }

    private static void bindStats() throws Exception {
        Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
        Class<?> index = Class.forName("com.tacz.guns.resource.index.CommonGunIndex");
        Class<?> data = Class.forName("com.tacz.guns.resource.pojo.data.gun.GunData");
        Class<?> bullet = Class.forName("com.tacz.guns.resource.pojo.data.gun.BulletData");

        gunIdOf = Class.forName("com.tacz.guns.api.item.IGun").getMethod("getGunId", ItemStack.class);
        gunIndexOf = api.getMethod("getCommonGunIndex", ResourceLocation.class);
        gunDataOf = index.getMethod("getGunData");
        bulletDataOf = index.getMethod("getBulletData");
        Class<?> extra = Class.forName("com.tacz.guns.resource.pojo.data.gun.ExtraDamage");
        Class<?> spread = Class.forName("com.tacz.guns.resource.pojo.data.gun.InaccuracyType");

        ammoIdOf = data.getMethod("getAmmoId");
        ammoAmount = data.getMethod("getAmmoAmount");
        rpmOf = data.getMethod("getRoundsPerMinute");
        aimTimeOf = data.getMethod("getAimTime");
        sprintTimeOf = data.getMethod("getSprintTime");
        weightOf = data.getMethod("getWeight");
        inaccuracyOf = data.getMethod("getInaccuracy", spread);
        inaccuracyTypes = (Object[]) spread.getMethod("values").invoke(null);
        damageOf = bullet.getMethod("getDamageAmount");
        speedOf = bullet.getMethod("getSpeed");
        pierceOf = bullet.getMethod("getPierce");
        knockbackOf = bullet.getMethod("getKnockback");
        extraDamageOf = bullet.getMethod("getExtraDamage");
        armorIgnoreOf = extra.getMethod("getArmorIgnore");
        headshotOf = extra.getMethod("getHeadShotMultiplier");
    }

    private static void bindCatalog() throws Exception {
        Class<?> api = Class.forName("com.tacz.guns.api.TimelessAPI");
        Class<?> index = Class.forName("com.tacz.guns.resource.index.CommonAttachmentIndex");
        Class<?> property = Class.forName("com.tacz.guns.api.modifier.JsonProperty");

        catalog = api.getMethod("getAllCommonAttachmentIndex");
        indexOf = api.getMethod("getCommonAttachmentIndex", ResourceLocation.class);
        indexType = index.getMethod("getType");
        dataOf = index.getMethod("getData");
        modifiersOf = Class.forName("com.tacz.guns.resource.pojo.data.attachment.AttachmentData")
                .getMethod("getModifier");
        componentsOf = property.getMethod("getComponents");
        initComponents = property.getMethod("initComponents");
    }

    private static void bindBuilder() throws Exception {
        Class<?> factory = Class.forName("com.tacz.guns.api.item.builder.AttachmentItemBuilder");
        builder = factory.getMethod("create");
        builderId = factory.getMethod("setId", ResourceLocation.class);
        builderBuild = factory.getMethod("build");
        emptyAttachment = Class.forName("com.tacz.guns.api.DefaultAssets")
                .getMethod("isEmptyAttachmentId", ResourceLocation.class);

        Class<?> rounds = Class.forName("com.tacz.guns.api.item.builder.AmmoItemBuilder");
        ammoBuilder = rounds.getMethod("create");
        ammoBuilderId = rounds.getMethod("setId", ResourceLocation.class);
        ammoBuilderBuild = rounds.getMethod("build");
    }
}
