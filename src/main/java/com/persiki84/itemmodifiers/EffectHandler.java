package com.persiki84.itemmodifiers;

import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class EffectHandler {
    private static final int ENTRY_PARTS = 4;
    private static final int PERIOD = 10;
    private static final int DURATION = 45;
    private static final int TICKS_PER_SECOND = 20;
    private static final String DEBUFF = "DEBUFF";

    private static volatile Map<String, List<PotionEntry>> cache = Map.of();
    private static volatile boolean dirty = true;

    private final Map<UUID, Map<String, Integer>> warmupTimers = new HashMap<>();
    private final Map<UUID, Map<MobEffect, Fading>> lingerTimers = new HashMap<>();

    // WHY: таймеры лежат по UUID и не снимались ни на выходе, ни на остановке сервера
    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        warmupTimers.remove(event.getEntity().getUUID());
        lingerTimers.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        warmupTimers.clear();
        lingerTimers.clear();
    }

    public static void markDirty() {
        dirty = true;
    }

    private static Map<String, List<PotionEntry>> entries() {
        if (!dirty) return cache;

        Map<String, List<PotionEntry>> built = new HashMap<>();
        for (String line : ModifierEntries.potions()) {
            PotionEntry entry = parse(line);
            if (entry != null) built.computeIfAbsent(entry.item(), key -> new ArrayList<>()).add(entry);
        }
        cache = built;
        dirty = false;
        return built;
    }

    private static PotionEntry parse(String line) {
        String[] parts = line.split("\\|");
        if (parts.length != ENTRY_PARTS) return null;

        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(ResourceLocation.tryParse(parts[1]));
        if (effect == null) return null;

        try {
            return new PotionEntry(parts[0], effect, Integer.parseInt(parts[2]), DEBUFF.equals(parts[3]));
        } catch (NumberFormatException unreadable) {
            ItemModifiersMod.LOGGER.warn("[itemmodifiers] нечитаемая запись эффекта {}", line);
            return null;
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (!ModifierConfig.enabled()) return;
        if (!ModuleSwitches.allows(ModuleId.ITEM_MODIFIERS)) return;
        if (event.player.tickCount % PERIOD != 0) return;

        handleEffects((ServerPlayer) event.player);
    }

    private void handleEffects(ServerPlayer player) {
        Map<String, ItemStack> worn = worn(player);
        Map<String, Integer> warmup = warmup(player, worn.keySet());
        int ready = ModifierConfig.BUFF_WARMUP.get() * TICKS_PER_SECOND;

        Map<MobEffect, Integer> buffs = new HashMap<>();
        Map<MobEffect, Integer> debuffs = new HashMap<>();
        for (Map.Entry<String, ItemStack> item : worn.entrySet()) {
            boolean warm = warmup.getOrDefault(item.getKey(), 0) >= ready;
            for (PotionEntry entry : configured(item.getKey())) {
                collect(buffs, debuffs, entry.effect(), entry.level(), entry.debuff(), warm);
            }
            collectStored(buffs, debuffs, item.getValue(), warm);
        }

        linger(player, debuffs);
        apply(player, buffs);
        apply(player, debuffs);
    }

    // WHY: предметы собираются по виду, а не по слоту: запись конфига привязана к виду предмета,
    // WHY: и одна и та же вещь в двух слотах складывалась сама с собой
    private static Map<String, ItemStack> worn(ServerPlayer player) {
        Map<String, ItemStack> worn = new HashMap<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;

            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id != null) worn.putIfAbsent(id.toString(), stack);
        }
        return worn;
    }

    // WHY: счётчик разгона рос без предела, пока вещь надета: за долгую сессию он переполнял int
    private Map<String, Integer> warmup(ServerPlayer player, Set<String> worn) {
        Map<String, Integer> warmup = warmupTimers.computeIfAbsent(player.getUUID(), key -> new HashMap<>());
        int ceiling = ModifierConfig.BUFF_WARMUP.get() * TICKS_PER_SECOND + PERIOD;
        for (String item : worn) {
            warmup.merge(item, PERIOD, (held, step) -> Math.min(held + step, ceiling));
        }
        warmup.keySet().retainAll(worn);
        return warmup;
    }

    private static List<PotionEntry> configured(String item) {
        return entries().getOrDefault(item, List.of());
    }

    private static void collectStored(Map<MobEffect, Integer> buffs, Map<MobEffect, Integer> debuffs,
                                      ItemStack stack, boolean warm) {
        if (!stack.hasTag() || !stack.getTag().contains("ItemModifiersEffects", Tag.TAG_LIST)) return;

        ListTag list = stack.getTag().getList("ItemModifiersEffects", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag compound = list.getCompound(index);
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(
                    ResourceLocation.tryParse(compound.getString("Effect")));
            if (effect == null) continue;

            collect(buffs, debuffs, effect, compound.getInt("Level"),
                    DEBUFF.equals(compound.getString("Type")), warm);
        }
    }

    private static void collect(Map<MobEffect, Integer> buffs, Map<MobEffect, Integer> debuffs,
                                MobEffect effect, int level, boolean debuff, boolean warm) {
        if (debuff) {
            debuffs.merge(effect, level, Integer::sum);
        } else if (warm) {
            buffs.merge(effect, level, Integer::sum);
        }
    }

    // WHY: остаток дебафа держал только срок, а уровень терял: снятая броня роняла дебаф третьего
    // WHY: уровня до первого на всё время догорания
    private void linger(ServerPlayer player, Map<MobEffect, Integer> debuffs) {
        Map<MobEffect, Fading> fading = lingerTimers.computeIfAbsent(player.getUUID(), key -> new HashMap<>());
        int span = ModifierConfig.DEBUFF_LINGER.get() * TICKS_PER_SECOND;

        for (Map.Entry<MobEffect, Integer> debuff : debuffs.entrySet()) {
            fading.put(debuff.getKey(), new Fading(span, debuff.getValue()));
        }

        List<MobEffect> spent = new ArrayList<>();
        for (Map.Entry<MobEffect, Fading> entry : fading.entrySet()) {
            if (debuffs.containsKey(entry.getKey())) continue;

            Fading left = entry.getValue().aged(PERIOD);
            entry.setValue(left);
            if (left.ticks() > 0) {
                debuffs.put(entry.getKey(), left.level());
            } else {
                spent.add(entry.getKey());
            }
        }
        spent.forEach(fading::remove);
    }

    // WHY: мгновенный эффект не длится, и выдача каждые полсекунды превращала лечение в бесконечное
    // WHY: здоровье, а урон - в смерть, которую нельзя снять ни снятием вещи, ни молоком
    private static void apply(ServerPlayer player, Map<MobEffect, Integer> effects) {
        effects.forEach((effect, level) -> {
            if (effect.isInstantenous()) return;

            player.addEffect(new MobEffectInstance(effect, DURATION, level, true, false, true));
        });
    }

    private record PotionEntry(String item, MobEffect effect, int level, boolean debuff) {}

    private record Fading(int ticks, int level) {
        private Fading aged(int spent) {
            return new Fading(ticks - spent, level);
        }
    }
}
