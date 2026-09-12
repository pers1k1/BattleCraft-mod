package com.persiki84.zones.client;

import com.persiki84.zones.network.ModifierSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public final class ClientModifierData {
    private static final int ROMAN_LIMIT = 10;
    private static final String[] ROMAN = { "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };

    private static List<String> potionEntries = List.of();
    private static List<String> attributeEntries = List.of();

    private ClientModifierData() {}

    public static void accept(ModifierSyncPacket packet) {
        potionEntries = packet.potionEntries();
        attributeEntries = packet.attributeEntries();
    }

    public static void clear() {
        potionEntries = List.of();
        attributeEntries = List.of();
    }

    public static List<Component> describe(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) return lines;

        String key = itemId.toString();
        for (String entry : potionEntries) {
            Component line = describePotion(entry, key);
            if (line != null) lines.add(line);
        }
        for (String entry : attributeEntries) {
            Component line = describeAttribute(entry, key);
            if (line != null) lines.add(line);
        }
        return lines;
    }

    private static Component describePotion(String entry, String itemId) {
        String[] parts = entry.split("\\|");
        if (parts.length < 4 || !parts[0].equals(itemId)) return null;

        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(ResourceLocation.tryParse(parts[1]));
        if (effect == null) return null;

        boolean debuff = parts[3].equalsIgnoreCase("DEBUFF");
        Component name = Component.translatable(effect.getDescriptionId());
        Component level = Component.literal(roman(parseInt(parts[2]) + 1));

        return Component.translatable("zones.shop.modifier.effect", name, level)
                .withStyle(debuff ? ChatFormatting.RED : ChatFormatting.GREEN);
    }

    private static Component describeAttribute(String entry, String itemId) {
        String[] parts = entry.split("\\|");
        if (parts.length < 5 || !parts[0].equals(itemId)) return null;

        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.tryParse(parts[1]));
        if (attribute == null) return null;

        double amount = parseDouble(parts[2]);
        boolean percent = !"0".equals(parts[3]);
        String formatted = (amount > 0 ? "+" : "") + trim(amount) + (percent ? "%" : "");

        return Component.translatable("zones.shop.modifier.attribute",
                        Component.translatable(attribute.getDescriptionId()), formatted)
                .withStyle(amount < 0 ? ChatFormatting.RED : ChatFormatting.GREEN);
    }

    private static String trim(double value) {
        if (value == Math.floor(value)) return String.valueOf((long) value);
        return String.format("%.2f", value);
    }

    private static String roman(int level) {
        if (level < 1) return "";
        if (level > ROMAN_LIMIT) return String.valueOf(level);
        return ROMAN[level - 1];
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
