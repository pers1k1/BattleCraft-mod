package com.persiki84.zones.client;

import com.persiki84.itemmodifiers.ModifierEntries;
import com.persiki84.shared.AmountText;
import com.persiki84.zones.network.ModifierSyncPacket;
import net.minecraft.client.Minecraft;
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

    // WHY: на выделенном сервере конфиг модификаторов лежит только там, и подсказка предмета
    // WHY: у игрока была пустой. Хозяину встроенного сервера подменять источник нельзя: у него
    // WHY: конфиг и есть истина, а снимок отстал бы от правки файла
    public static void accept(ModifierSyncPacket packet) {
        potionEntries = packet.potionEntries();
        attributeEntries = packet.attributeEntries();
        if (!Minecraft.getInstance().hasSingleplayerServer()) {
            ModifierEntries.adopt(potionEntries, attributeEntries);
        }
    }

    public static void clear() {
        potionEntries = List.of();
        attributeEntries = List.of();
        ModifierEntries.forget();
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
        boolean percent = !"0".equals(parts[3].trim());

        return Component.translatable("zones.shop.modifier.attribute",
                        Component.translatable(attribute.getDescriptionId()), AmountText.signed(amount, percent))
                .withStyle(amount < 0 ? ChatFormatting.RED : ChatFormatting.GREEN);
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
