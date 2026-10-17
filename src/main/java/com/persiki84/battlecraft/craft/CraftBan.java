package com.persiki84.battlecraft.craft;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

// WHY: запрет без тега закрывает предмет целиком, запрет с тегом - только варианты, в чьём теге
// WHY: этот тег лежит подмножеством: так запрещается одно зелье или один ствол TACZ, а не все разом
public record CraftBan(Item item, @Nullable CompoundTag tag, CraftPhase phase) {

    public CraftBan {
        tag = tag == null || tag.isEmpty() ? null : tag.copy();
    }

    public String itemId() {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    public String key() {
        return tag == null ? itemId() : itemId() + tag;
    }

    public boolean matches(ItemStack stack) {
        if (!stack.is(item)) return false;
        return tag == null || NbtUtils.compareNbt(tag, stack.getTag(), true);
    }

    public boolean activeWhen(boolean matchRunning) {
        return phase.activeWhen(matchRunning);
    }

    public CraftBan withPhase(CraftPhase next) {
        return new CraftBan(item, tag, next);
    }

    public ItemStack stack() {
        ItemStack stack = new ItemStack(item);
        if (tag != null) stack.setTag(tag.copy());
        return stack;
    }
}
