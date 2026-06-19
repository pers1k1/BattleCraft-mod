package com.persiki84.itemmodifiers;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.ArrayList;
import java.util.List;

public class ModifierConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue MOD_ENABLED;
    public static final ForgeConfigSpec.IntValue BUFF_WARMUP;
    public static final ForgeConfigSpec.IntValue DEBUFF_LINGER;

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> POTION_EFFECTS;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ATTRIBUTE_MODIFIERS;

    static {
        BUILDER.push("General");
        MOD_ENABLED = BUILDER.define("modEnabled", true);
        BUILDER.pop();

        BUILDER.push("Potion Settings");
        BUFF_WARMUP = BUILDER.defineInRange("buffWarmup", 5, 0, 600);
        DEBUFF_LINGER = BUILDER.defineInRange("debuffLinger", 10, 0, 600);
        POTION_EFFECTS = BUILDER.defineList("potionEffects", ArrayList::new, o -> o instanceof String);
        BUILDER.pop();

        BUILDER.push("Attribute Settings");
        ATTRIBUTE_MODIFIERS = BUILDER.defineList("attributeModifiers", ArrayList::new, o -> o instanceof String);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static List<String> getPotionEffects() { return new ArrayList<>(POTION_EFFECTS.get()); }
    public static List<String> getAttributes() { return new ArrayList<>(ATTRIBUTE_MODIFIERS.get()); }

    public static void addPotionEntry(String entry) {
        String[] parts = entry.split("\\|");
        if (parts.length < 2) return;
        String prefix = parts[0] + "|" + parts[1] + "|";
        List<String> list = getPotionEffects();
        boolean updated = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).startsWith(prefix)) {
                list.set(i, entry);
                updated = true;
                break;
            }
        }
        if (!updated) {
            list.add(entry);
        }
        POTION_EFFECTS.set(list);
        SPEC.save();
    }

    public static void addAttributeEntry(String entry) {
        String[] parts = entry.split("\\|");
        if (parts.length < 5) return;
        String itemId = parts[0];
        String attrId = parts[1];
        String slot = parts[4];
        List<String> list = getAttributes();
        boolean updated = false;
        for (int i = 0; i < list.size(); i++) {
            String[] existingParts = list.get(i).split("\\|");
            if (existingParts.length >= 5 && existingParts[0].equals(itemId) && existingParts[1].equals(attrId) && existingParts[4].equalsIgnoreCase(slot)) {
                list.set(i, entry);
                updated = true;
                break;
            }
        }
        if (!updated) {
            list.add(entry);
        }
        ATTRIBUTE_MODIFIERS.set(list);
        SPEC.save();
    }

    public static void removePotion(String itemId, String effectId) {
        List<String> list = getPotionEffects();
        list.removeIf(s -> s.startsWith(itemId + "|" + effectId + "|"));
        POTION_EFFECTS.set(list);
        SPEC.save();
    }

    public static void clearPotions(String itemId) {
        List<String> list = getPotionEffects();
        list.removeIf(s -> s.startsWith(itemId + "|"));
        POTION_EFFECTS.set(list);
        SPEC.save();
    }

    public static void removeAttribute(String itemId, String attrId) {
        List<String> list = getAttributes();
        list.removeIf(s -> s.startsWith(itemId + "|" + attrId + "|"));
        ATTRIBUTE_MODIFIERS.set(list);
        SPEC.save();
    }

    public static void clearAttributes(String itemId) {
        List<String> list = getAttributes();
        list.removeIf(s -> s.startsWith(itemId + "|"));
        ATTRIBUTE_MODIFIERS.set(list);
        SPEC.save();
    }

    public static void clearAll(String itemId) {
        clearPotions(itemId);
        clearAttributes(itemId);
    }

    public static void save() { SPEC.save(); }
}
