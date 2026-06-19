package com.persiki84.itemmodifiers;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class EffectHandler {

    private static final Map<String, List<PotionEntry>> cache = new HashMap<>();
    private static boolean dirty = true;

    private final Map<UUID, Map<String, Integer>> warmupTimers = new HashMap<>();
    private final Map<UUID, Map<MobEffect, Integer>> lingerTimers = new HashMap<>();

    public static void markDirty() {
        dirty = true;
    }

    private void refreshCache() {
        cache.clear();
        List<String> raw = ModifierConfig.getPotionEffects();
        for (String line : raw) {
            try {
                String[] parts = line.split("\\|");
                if (parts.length != 4) continue;
                MobEffect eff = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(parts[1]));
                if (eff != null) {
                    cache.computeIfAbsent(parts[0], k -> new ArrayList<>())
                            .add(new PotionEntry(eff, Integer.parseInt(parts[2]), parts[3].equals("DEBUFF")));
                }
            } catch (Exception ignored) {}
        }
        dirty = false;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (!ModifierConfig.MOD_ENABLED.get()) return;
        if (event.player.tickCount % 10 != 0) return;

        if (dirty) refreshCache();

        ServerPlayer player = (ServerPlayer) event.player;
        handleEffects(player);
    }

    private void handleEffects(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Set<String> equipped = new HashSet<>();

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (id != null) equipped.add(id.toString());
            }
        }

        warmupTimers.putIfAbsent(uuid, new HashMap<>());
        Map<String, Integer> pWarmup = warmupTimers.get(uuid);
        for (String item : equipped) pWarmup.put(item, pWarmup.getOrDefault(item, 0) + 10);
        pWarmup.keySet().retainAll(equipped);

        Map<MobEffect, Integer> buffs = new HashMap<>();
        Map<MobEffect, Integer> debuffs = new HashMap<>();
        int warmupLimit = ModifierConfig.BUFF_WARMUP.get() * 20;

        for (String item : equipped) {
            if (cache.containsKey(item)) {
                for (PotionEntry entry : cache.get(item)) {
                    if (entry.isDebuff) {
                        debuffs.merge(entry.effect, entry.level, Integer::sum);
                    } else if (pWarmup.getOrDefault(item, 0) >= warmupLimit) {
                        buffs.merge(entry.effect, entry.level, Integer::sum);
                    }
                }
            }
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (id != null) {
                    String itemKey = id.toString();
                    if (stack.hasTag() && stack.getTag().contains("ItemModifiersEffects", 9)) {
                        net.minecraft.nbt.ListTag list = stack.getTag().getList("ItemModifiersEffects", 10);
                        for (int i = 0; i < list.size(); i++) {
                            net.minecraft.nbt.CompoundTag compound = list.getCompound(i);
                            String effectId = compound.getString("Effect");
                            int level = compound.getInt("Level");
                            boolean isDebuff = compound.getString("Type").equals("DEBUFF");
                            MobEffect eff = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(effectId));
                            if (eff != null) {
                                if (isDebuff) {
                                    debuffs.merge(eff, level, Integer::sum);
                                } else if (pWarmup.getOrDefault(itemKey, 0) >= warmupLimit) {
                                    buffs.merge(eff, level, Integer::sum);
                                }
                            }
                        }
                    }
                }
            }
        }

        lingerTimers.putIfAbsent(uuid, new HashMap<>());
        Map<MobEffect, Integer> pLinger = lingerTimers.get(uuid);
        int lingerMax = ModifierConfig.DEBUFF_LINGER.get() * 20;

        for (MobEffect d : debuffs.keySet()) pLinger.put(d, lingerMax);

        List<MobEffect> toRemove = new ArrayList<>();
        for (var entry : pLinger.entrySet()) {
            if (!debuffs.containsKey(entry.getKey())) {
                int time = entry.getValue() - 10;
                if (time > 0) {
                    entry.setValue(time);
                    debuffs.putIfAbsent(entry.getKey(), 0);
                } else {
                    toRemove.add(entry.getKey());
                }
            }
        }
        toRemove.forEach(pLinger::remove);

        buffs.forEach((eff, lvl) -> player.addEffect(new MobEffectInstance(eff, 45, lvl, true, false, true)));
        debuffs.forEach((eff, lvl) -> player.addEffect(new MobEffectInstance(eff, 45, lvl, true, false, true)));
    }

    private static class PotionEntry {
        MobEffect effect;
        int level;
        boolean isDebuff;
        PotionEntry(MobEffect e, int l, boolean d) { effect = e; level = l; isDebuff = d; }
    }
}
