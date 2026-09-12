package com.persiki84.itemmodifiers;

import net.minecraft.resources.ResourceLocation;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class AttributeHandler {

    private static final Map<String, List<AttributeEntry>> cache = new HashMap<>();
    private static boolean dirty = true;

    public static void markDirty() {
        dirty = true;
    }

    private void refreshCache() {
        cache.clear();
        List<String> raw = ModifierConfig.getAttributes();
        for (String line : raw) {
            try {
                String[] parts = line.split("\\|");
                if (parts.length != 5) continue;

                String itemId = parts[0];
                Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(parts[1]));
                double amount = Double.parseDouble(parts[2]);
                int opId = Integer.parseInt(parts[3]);
                AttributeModifier.Operation op = AttributeModifier.Operation.values()[Math.min(Math.max(opId, 0), 2)];

                String slotName = parts[4].toLowerCase();

                if (attr != null) {
                    cache.computeIfAbsent(itemId, k -> new ArrayList<>())
                            .add(new AttributeEntry(attr, amount, op, slotName));
                }
            } catch (Exception e) {
                ItemModifiersMod.LOGGER.error("Failed to parse attribute: " + line);
            }
        }
        dirty = false;
    }

    @SubscribeEvent
    public void onItemAttribute(ItemAttributeModifierEvent event) {
        if (!ModifierConfig.MOD_ENABLED.get()) return;
        if (!ModuleSwitches.allows(ModuleId.ITEM_MODIFIERS)) return;

        ItemStack stack = event.getItemStack();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) return;

        if (dirty) refreshCache();
        addConfigured(event, itemId.toString());
        addStored(event, stack, itemId.toString());
    }

    private void addConfigured(ItemAttributeModifierEvent event, String key) {
        List<AttributeEntry> entries = cache.get(key);
        if (entries == null) return;

        for (AttributeEntry entry : entries) {
            if (!fits(entry.slotName, event)) continue;

            UUID uuid = named(key + ":" + entry.attribute.getDescriptionId() + ":" + entry.slotName);
            event.addModifier(entry.attribute, new AttributeModifier(uuid, "ItemMod Modifier", entry.amount, entry.operation));
        }
    }

    // WHY: номер записи в списке смещается после снятия соседней, и модификатор менял UUID
    // WHY: на живом предмете: имя берём от атрибута и слота, а не от позиции в списке
    private void addStored(ItemAttributeModifierEvent event, ItemStack stack, String key) {
        if (!stack.hasTag() || !stack.getTag().contains("ItemModifiersAttributes", 9)) return;

        net.minecraft.nbt.ListTag list = stack.getTag().getList("ItemModifiersAttributes", 10);
        for (int index = 0; index < list.size(); index++) {
            net.minecraft.nbt.CompoundTag compound = list.getCompound(index);
            String slotName = compound.getString("Slot").toLowerCase();
            if (!fits(slotName, event)) continue;

            String attrId = compound.getString("Attribute");
            Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(attrId));
            if (attr == null) continue;

            AttributeModifier.Operation op = operationOf(compound.getInt("Operation"));
            UUID uuid = named(key + ":" + attrId + ":" + slotName + ":nbt");
            event.addModifier(attr, new AttributeModifier(uuid, "ItemMod NBT Modifier",
                    compound.getDouble("Amount"), op));
        }
    }

    private static boolean fits(String slotName, ItemAttributeModifierEvent event) {
        return slotName.equals("any") || slotName.equals(event.getSlotType().getName());
    }

    private static AttributeModifier.Operation operationOf(int stored) {
        return AttributeModifier.Operation.values()[Math.min(Math.max(stored, 0), 2)];
    }

    private static UUID named(String source) {
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    private static class AttributeEntry {
        Attribute attribute;
        double amount;
        AttributeModifier.Operation operation;
        String slotName;

        public AttributeEntry(Attribute attribute, double amount, AttributeModifier.Operation operation, String slotName) {
            this.attribute = attribute;
            this.amount = amount;
            this.operation = operation;
            this.slotName = slotName;
        }
    }
}
