package com.persiki84.battlecraft.craft;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Set;

// WHY: стак из инвентаря несёт теги, которые к виду предмета отношения не имеют: износ, имя,
// WHY: патроны в магазине ствола, режим огня. Запрет с ними не совпал бы ни с одним результатом
// WHY: рецепта. Остаются только ключи, которые и есть вид: зелье, зачарование книги, ствол,
// WHY: патрон и обвес TACZ. Предмет, у которого таких ключей нет, запрещается целиком
public final class CraftBanIdentity {
    private static final Set<String> IDENTITY_KEYS = Set.of(
            "Potion", "StoredEnchantments", "GunId", "AmmoId", "AttachmentId", "BlockId");

    private CraftBanIdentity() {}

    @Nullable
    public static CompoundTag of(ItemStack stack) {
        CompoundTag source = stack.getTag();
        if (source == null) return null;

        CompoundTag kept = new CompoundTag();
        for (String key : IDENTITY_KEYS) {
            Tag value = source.get(key);
            if (value != null) kept.put(key, value.copy());
        }
        return kept.isEmpty() ? null : kept;
    }

    // WHY: аргумент предмета строит стак через new ItemStack, а тот сам кладёт в тег служебные
    // WHY: значения вроде Damage:0. Без их снятия «/bc craft ban diamond_sword» становился запретом
    // WHY: варианта с тегом износа, а снять его голым именем было уже нельзя
    @Nullable
    public static CompoundTag typed(ItemStack stack) {
        CompoundTag source = stack.getTag();
        if (source == null) return null;

        CompoundTag kept = source.copy();
        CompoundTag defaults = new ItemStack(stack.getItem()).getTag();
        if (defaults != null) {
            for (String key : defaults.getAllKeys()) {
                if (defaults.get(key).equals(kept.get(key))) kept.remove(key);
            }
        }
        return kept.isEmpty() ? null : kept;
    }

    // WHY: тот же вид, что и ключ запрета, в синтаксисе аргумента предмета: студия шлёт его командой
    // WHY: и по нему же узнаёт свой запрет в пришедшем снимке
    public static String spec(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        CompoundTag identity = of(stack);
        return identity == null ? id : id + identity;
    }
}
