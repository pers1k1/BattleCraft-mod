package com.persiki84.itemmodifiers;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
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
    private static final String CONFIG_NAME = "ItemMod Modifier";
    private static final String STORED_NAME = "ItemMod NBT Modifier";
    private static final String STORED_LIST = "ItemModifiersAttributes";
    private static final String ANY_SLOT = "any";

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
        if (!allowed()) return;

        collect(event.getItemStack(), event.getSlotType()).forEach(event::addModifier);
    }

    private static boolean allowed() {
        return ModifierConfig.enabled() && ModuleSwitches.allows(ModuleId.ITEM_MODIFIERS);
    }

    public static Multimap<Attribute, AttributeModifier> collect(ItemStack stack, EquipmentSlot slot) {
        Multimap<Attribute, AttributeModifier> mine = HashMultimap.create();
        ResourceLocation itemId = stack.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) return mine;

        String key = itemId.toString();
        addConfigured(mine, key, slot, addStored(mine, stack, key, slot));
        return mine;
    }

    // WHY: запись в стаке и запись в конфиге вида предмета жили независимо и складывались: предмет,
    // WHY: настроенный сначала командой, а потом из меню, получал двойную величину. Своя запись
    // WHY: предмета старше общей, как и должно быть у исключения из правила
    private static void addConfigured(Multimap<Attribute, AttributeModifier> mine, String key,
                                      EquipmentSlot slot, Set<Attribute> overridden) {
        List<AttributeEntry> entries = entries().get(key);
        if (entries == null) return;

        for (AttributeEntry entry : entries) {
            if (overridden.contains(entry.attribute()) || !fits(entry.slotName(), slot)) continue;

            UUID uuid = named(key, entry.attribute().getDescriptionId(), entry.slotName(), slot, false);
            mine.put(entry.attribute(),
                    new AttributeModifier(uuid, CONFIG_NAME, entry.amount(), entry.operation()));
        }
    }

    // WHY: номер записи в списке смещается после снятия соседней, и модификатор менял UUID
    // WHY: на живом предмете: имя берём от атрибута и слота, а не от позиции в списке
    private static Set<Attribute> addStored(Multimap<Attribute, AttributeModifier> mine, ItemStack stack,
                                            String key, EquipmentSlot slot) {
        if (!stack.hasTag() || !stack.getTag().contains(STORED_LIST, Tag.TAG_LIST)) return Set.of();

        Set<Attribute> added = new HashSet<>();
        ListTag list = stack.getTag().getList(STORED_LIST, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag compound = list.getCompound(index);
            String slotName = compound.getString("Slot").toLowerCase();
            if (!fits(slotName, slot)) continue;

            String attrId = compound.getString("Attribute");
            Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(ResourceLocation.tryParse(attrId));
            if (attr == null) continue;

            mine.put(attr, new AttributeModifier(named(key, attrId, slotName, slot, true), STORED_NAME,
                    compound.getDouble("Amount"), operationOf(compound.getInt("Operation"))));
            added.add(attr);
        }
        return added;
    }

    // WHY: правка конфига не трогает сам предмет, а ваниль сверяет модификаторы только на смене
    // WHY: снаряжения: снятая запись оставалась висеть на игроке до конца сессии, в том числе
    // WHY: после того, как предмет убран из руки. Свои модификаторы снимаются и выдаются заново
    public static void reapplyAll(MinecraftServer server) {
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            reapply(player);
        }
    }

    public static void reapply(ServerPlayer player) {
        AttributeMap attributes = player.getAttributes();
        strip(attributes);
        if (!allowed()) return;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            Multimap<Attribute, AttributeModifier> mine = collect(player.getItemBySlot(slot), slot);
            if (!mine.isEmpty()) attributes.addTransientAttributeModifiers(mine);
        }
    }

    // WHY: снимаем по имени, а не по вычисленному UUID: запись, которой в конфиге уже нет, иначе
    // WHY: не вычисляется вовсе, и именно она и оставалась висеть
    private static void strip(AttributeMap attributes) {
        for (Attribute attribute : ForgeRegistries.ATTRIBUTES) {
            AttributeInstance instance = attributes.getInstance(attribute);
            if (instance == null) continue;

            for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
                if (CONFIG_NAME.equals(modifier.getName()) || STORED_NAME.equals(modifier.getName())) {
                    instance.removeModifier(modifier);
                }
            }
        }
    }

    private static boolean fits(String slotName, EquipmentSlot slot) {
        return slotName.equals(ANY_SLOT) || slotName.equals(slot.getName());
    }

    private static AttributeModifier.Operation operationOf(int stored) {
        return AttributeModifier.Operation.values()[Math.min(Math.max(stored, 0), OPERATIONS - 1)];
    }

    // WHY: слот выдачи входит в имя наравне со слотом записи: запись на «любой» слот у предмета
    // WHY: сразу в двух руках давала один UUID дважды, а второе применение это исключение ванили
    private static UUID named(String item, String attribute, String entrySlot, EquipmentSlot slot,
                             boolean stored) {
        String source = item + ":" + attribute + ":" + entrySlot + ":" + slot.getName() + (stored ? ":nbt" : "");
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    private record AttributeEntry(String item, Attribute attribute, double amount,
                                  AttributeModifier.Operation operation, String slotName) {}
}
