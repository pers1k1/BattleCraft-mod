package com.persiki84.shared;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

public final class Names {

    private Names() {}

    public static Component item(String id) {
        ResourceLocation key = parse(id);
        if (key == null) return Component.literal(id);

        Item item = ForgeRegistries.ITEMS.getValue(key);
        return item == null ? Component.literal(id) : item.getDescription();
    }

    public static Component effect(String id) {
        ResourceLocation key = parse(id);
        if (key == null) return Component.literal(id);

        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(key);
        return effect == null ? Component.literal(id) : Component.translatable(effect.getDescriptionId());
    }

    public static Component attribute(String id) {
        ResourceLocation key = parse(id);
        if (key == null) return Component.literal(id);

        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(key);
        return attribute == null ? Component.literal(id) : Component.translatable(attribute.getDescriptionId());
    }

    public static Component block(String id) {
        ResourceLocation key = parse(id);
        if (key == null) return Component.literal(id);

        return BuiltInRegistries.BLOCK.getOptional(key)
                .map(block -> (Component) block.getName())
                .orElse(Component.literal(id));
    }

    public static Component slot(String name) {
        return Component.translatable("battlecraft.slot." + name);
    }

    private static ResourceLocation parse(String id) {
        if (id == null || id.isEmpty()) return null;
        return ResourceLocation.tryParse(id);
    }
}
