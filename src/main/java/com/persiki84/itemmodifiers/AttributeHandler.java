package com.persiki84.itemmodifiers;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EquipmentSlot;
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

        ItemStack stack = event.getItemStack();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) return;
        String key = itemId.toString();

        if (dirty) refreshCache();
        if (cache.containsKey(key)) {
            List<AttributeEntry> entries = cache.get(key);
            for (AttributeEntry entry : entries) {
                if (!entry.slotName.equals("any") && !entry.slotName.equals(event.getSlotType().getName())) {
                    continue;
                }
                UUID uuid = UUID.nameUUIDFromBytes((key + ":" + entry.attribute.getDescriptionId() + ":" + entry.slotName).getBytes(StandardCharsets.UTF_8));
                AttributeModifier modifier = new AttributeModifier(uuid, "ItemMod Modifier", entry.amount, entry.operation);
                event.addModifier(entry.attribute, modifier);
            }
        }

        if (stack.hasTag() && stack.getTag().contains("ItemModifiersAttributes", 9)) {
            net.minecraft.nbt.ListTag list = stack.getTag().getList("ItemModifiersAttributes", 10);
            for (int i = 0; i < list.size(); i++) {
                net.minecraft.nbt.CompoundTag compound = list.getCompound(i);
                String attrId = compound.getString("Attribute");
                double amount = compound.getDouble("Amount");
                int opId = compound.getInt("Operation");
                AttributeModifier.Operation op = AttributeModifier.Operation.values()[Math.min(Math.max(opId, 0), 2)];
                String slotName = compound.getString("Slot").toLowerCase();

                if (!slotName.equals("any") && !slotName.equals(event.getSlotType().getName())) {
                    continue;
                }

                Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(attrId));
                if (attr != null) {
                    UUID uuid = UUID.nameUUIDFromBytes((key + ":" + attrId + ":" + slotName + ":nbt:" + i).getBytes(StandardCharsets.UTF_8));
                    AttributeModifier modifier = new AttributeModifier(uuid, "ItemMod NBT Modifier", amount, op);
                    event.addModifier(attr, modifier);
                }
            }
        }
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
