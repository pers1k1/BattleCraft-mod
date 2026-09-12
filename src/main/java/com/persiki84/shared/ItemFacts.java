package com.persiki84.shared;

import com.persiki84.shared.gunsmith.GunSmith;
import com.persiki84.shared.gunsmith.GunStat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class ItemFacts {

    private ItemFacts() {}

    public static List<Component> describe(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        if (stack.isEmpty()) return lines;

        addGun(lines, stack);
        addFood(lines, stack);
        addDurability(lines, stack);
        return lines;
    }

    private static void addGun(List<Component> lines, ItemStack stack) {
        for (GunStat stat : GunSmith.stats(stack)) {
            lines.add(Component.translatable("battlecraft.facts.entry",
                    Component.translatable(stat.label()), stat.value()).withStyle(ChatFormatting.GRAY));
        }
    }

    private static void addFood(List<Component> lines, ItemStack stack) {
        FoodProperties food = stack.getItem().getFoodProperties(stack, null);
        if (food == null) return;

        lines.add(Component.translatable("battlecraft.facts.entry",
                Component.translatable("battlecraft.facts.nutrition"),
                String.valueOf(food.getNutrition())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("battlecraft.facts.entry",
                Component.translatable("battlecraft.facts.saturation"),
                String.format("%.1f", food.getSaturationModifier() * food.getNutrition() * 2.0f))
                .withStyle(ChatFormatting.GRAY));
    }

    private static void addDurability(List<Component> lines, ItemStack stack) {
        if (!stack.isDamageableItem()) return;

        lines.add(Component.translatable("battlecraft.facts.entry",
                Component.translatable("battlecraft.facts.durability"),
                (stack.getMaxDamage() - stack.getDamageValue()) + " / " + stack.getMaxDamage())
                .withStyle(ChatFormatting.GRAY));
    }
}
