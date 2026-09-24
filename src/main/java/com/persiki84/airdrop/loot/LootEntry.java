package com.persiki84.airdrop.loot;

import com.google.gson.JsonObject;
import com.persiki84.airdrop.AirDropMod;
import com.persiki84.shared.JsonRead;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record LootEntry(ResourceLocation itemId, int min, int max, float chance, String nbt) {
    public static final int MAX_COUNT = 256;
    public static final float MIN_CHANCE = 0.0001f;

    public LootEntry {
        min = Math.max(1, Math.min(MAX_COUNT, min));
        max = Math.max(min, Math.min(MAX_COUNT, max));
        chance = Float.isNaN(chance) ? 1.0f : Math.max(MIN_CHANCE, Math.min(1.0f, chance));
        nbt = nbt == null || nbt.isBlank() ? null : nbt;
    }

    public static LootEntry of(ItemStack stack, int min, int max, float chance) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String tag = stack.hasTag() ? stack.getTag().toString() : null;
        return new LootEntry(id, min, max, chance, tag);
    }

    public boolean known() {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        return item != Items.AIR;
    }

    public boolean guaranteed() {
        return chance >= 1.0f;
    }

    public ItemStack template() {
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == Items.AIR) return ItemStack.EMPTY;

        ItemStack stack = new ItemStack(item);
        if (nbt != null) stack.setTag(parsedTag());
        return stack;
    }

    private CompoundTag parsedTag() {
        try {
            return TagParser.parseTag(nbt);
        } catch (Exception malformed) {
            AirDropMod.LOGGER.warn("[airdrop] bad nbt for {}: {}", itemId, malformed.toString());
            return null;
        }
    }

    public LootEntry counted(int newMin, int newMax) {
        return new LootEntry(itemId, newMin, newMax, chance, nbt);
    }

    public LootEntry chanced(float newChance) {
        return new LootEntry(itemId, min, max, newChance, nbt);
    }

    public LootEntry tagged(String newNbt) {
        return new LootEntry(itemId, min, max, chance, newNbt);
    }

    public LootEntry describing(ItemStack stack) {
        return of(stack, min, max, chance);
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("item", itemId.toString());
        object.addProperty("min", min);
        object.addProperty("max", max);
        object.addProperty("chance", chance);
        if (nbt != null) object.addProperty("nbt", nbt);
        return object;
    }

    public static LootEntry fromJson(JsonObject object) {
        String item = JsonRead.text(object, "item");
        ResourceLocation id = item == null ? null : ResourceLocation.tryParse(item);
        if (id == null) return null;

        Float min = JsonRead.number(object, "min");
        Float max = JsonRead.number(object, "max");
        Float chance = JsonRead.number(object, "chance");
        int low = min == null ? 1 : min.intValue();
        return new LootEntry(id, low, max == null ? low : max.intValue(),
                chance == null ? 1.0f : chance, JsonRead.text(object, "nbt"));
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("item", itemId.toString());
        tag.putInt("min", min);
        tag.putInt("max", max);
        tag.putFloat("chance", chance);
        if (nbt != null) tag.putString("nbt", nbt);
        return tag;
    }

    public static LootEntry fromTag(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("item"));
        if (id == null) return null;

        String written = tag.contains("nbt") ? tag.getString("nbt") : null;
        return new LootEntry(id, tag.getInt("min"), tag.getInt("max"), tag.getFloat("chance"), written);
    }
}
