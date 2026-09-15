package com.persiki84.itemmodifiers;

import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AttributeHandler {
    private static final int ENTRY_PARTS = 5;
    private static final int OPERATIONS = AttributeModifier.Operation.values().length;

    // WHY: событие атрибутов приходит и с потока сервера, и с потока отрисовки подсказок: общий
    // WHY: изменяемый кеш рвался на перестройке, поэтому снимок собирается целиком и публикуется разом
    private static volatile Map<String, List<AttributeEntry>> cache = Map.of();
    private static volatile boolean dirty = true;

    public static void markDirty() {
        dirty = true;
    }

    private static Map<String, List<AttributeEntry>> entries() {
        if (!dirty) return cache;

        Map<String, List<AttributeEntry>> built = build();
        cache = built;
        dirty = false;
        return built;
    }

    private static Map<String, List<AttributeEntry>> build() {
        Map<String, List<AttributeEntry>> built = new HashMap<>();
        for (String line : ModifierEntries.attributes()) {
            AttributeEntry entry = parse(line);
            if (entry != null) built.computeIfAbsent(entry.item(), key -> new ArrayList<>()).add(entry);
        }
        return built;
    }

    private static AttributeEntry parse(String line) {
        String[] parts = line.split("\\|");
        if (parts.length != ENTRY_PARTS) return null;

        Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.tryParse(parts[1]));
        if (attribute == null) return null;

        try {
            return new AttributeEntry(parts[0], attribute, Double.parseDouble(parts[2]),
                    operationOf(Integer.parseInt(parts[3])), parts[4].toLowerCase());
        } catch (NumberFormatException unreadable) {
            ItemModifiersMod.LOGGER.warn("[itemmodifiers] нечитаемая запись атрибута {}", line);
            return null;
        }
    }

    @SubscribeEvent
    public void onItemAttribute(ItemAttributeModifierEvent event) {
        if (!ModifierConfig.enabled()) return;
        if (!ModuleSwitches.allows(ModuleId.ITEM_MODIFIERS)) return;

        ItemStack stack = event.getItemStack();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) return;

        String key = itemId.toString();
        addConfigured(event, key, addStored(event, stack, key));
    }

    // WHY: запись в стаке и запись в конфиге вида предмета жили независимо и складывались: предмет,
    // WHY: настроенный сначала командой, а потом из меню, получал двойную величину. Своя запись
    // WHY: предмета старше общей, как и должно быть у исключения из правила
    private void addConfigured(ItemAttributeModifierEvent event, String key, Set<Attribute> overridden) {
        List<AttributeEntry> entries = entries().get(key);
        if (entries == null) return;

        for (AttributeEntry entry : entries) {
            if (overridden.contains(entry.attribute()) || !fits(entry.slotName(), event)) continue;

            UUID uuid = named(key + ":" + entry.attribute().getDescriptionId() + ":" + entry.slotName());
            event.addModifier(entry.attribute(),
                    new AttributeModifier(uuid, "ItemMod Modifier", entry.amount(), entry.operation()));
        }
    }

    // WHY: номер записи в списке смещается после снятия соседней, и модификатор менял UUID
    // WHY: на живом предмете: имя берём от атрибута и слота, а не от позиции в списке
    private Set<Attribute> addStored(ItemAttributeModifierEvent event, ItemStack stack, String key) {
        if (!stack.hasTag() || !stack.getTag().contains("ItemModifiersAttributes", Tag.TAG_LIST)) return Set.of();

        Set<Attribute> added = new HashSet<>();
        ListTag list = stack.getTag().getList("ItemModifiersAttributes", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag compound = list.getCompound(index);
            String slotName = compound.getString("Slot").toLowerCase();
            if (!fits(slotName, event)) continue;

            String attrId = compound.getString("Attribute");
            Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.tryParse(attrId));
            if (attr == null) continue;

            UUID uuid = named(key + ":" + attrId + ":" + slotName + ":nbt");
            event.addModifier(attr, new AttributeModifier(uuid, "ItemMod NBT Modifier",
                    compound.getDouble("Amount"), operationOf(compound.getInt("Operation"))));
            added.add(attr);
        }
        return added;
    }

    private static boolean fits(String slotName, ItemAttributeModifierEvent event) {
        return slotName.equals("any") || slotName.equals(event.getSlotType().getName());
    }

    private static AttributeModifier.Operation operationOf(int stored) {
        return AttributeModifier.Operation.values()[Math.min(Math.max(stored, 0), OPERATIONS - 1)];
    }

    private static UUID named(String source) {
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    private record AttributeEntry(String item, Attribute attribute, double amount,
                                  AttributeModifier.Operation operation, String slotName) {}
}
