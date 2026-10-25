package com.persiki84.battlecraft.mode;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

// WHY: запись набора хранит строки как есть: предмет мода, снятого на время, не теряется при
// WHY: сохранении, а при выдаче просто пропускается. Разбор только через tryParse и реестр
public record KitEntry(String itemId, String tag, int count) {

    public KitEntry {
        itemId = itemId == null ? "" : itemId;
        tag = tag == null ? "" : tag;
        count = ModeLimits.kitCount(count);
    }

    public static KitEntry of(ItemStack stack, int count) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        CompoundTag tag = stack.getTag();
        return new KitEntry(id.toString(), tag == null ? "" : tag.toString(), count);
    }

    public KitEntry withCount(int next) {
        return new KitEntry(itemId, tag, next);
    }

    public ItemStack stack() {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;

        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id), 1);
        if (tag.isEmpty()) return stack;
        try {
            stack.setTag(TagParser.parseTag(tag));
        } catch (CommandSyntaxException broken) {
            return ItemStack.EMPTY;
        }
        return stack;
    }
}
