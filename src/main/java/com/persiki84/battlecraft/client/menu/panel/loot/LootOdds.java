package com.persiki84.battlecraft.client.menu.panel.loot;

import com.persiki84.airdrop.loot.LootEntry;
import com.persiki84.airdrop.loot.LootRoller;
import com.persiki84.airdrop.loot.LootTable;
import net.minecraft.util.RandomSource;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

// WHY: настоящий шанс записи не равен введённому, как только у таблицы есть минимум или потолок
// WHY: позиций: добор и срез меняют его. Считается броском того же кода, что у сервера, а не формулой
public final class LootOdds {
    private static final int TRIALS = 4000;
    private static final long SEED = 0x5EEDL;

    private static LootTable measured;
    private static float[] appears = new float[0];
    private static float positions;
    private static float stacks;

    private LootOdds() {}

    public static float appears(LootTable table, int index) {
        measure(table);
        return index >= 0 && index < appears.length ? appears[index] : 0.0f;
    }

    public static float positions(LootTable table) {
        measure(table);
        return positions;
    }

    public static float items(LootTable table) {
        measure(table);
        return stacks;
    }

    private static void measure(LootTable table) {
        if (table == measured) return;

        measured = table;
        Map<LootEntry, Integer> slots = new IdentityHashMap<>();
        for (int index = 0; index < table.entries().size(); index++) {
            slots.put(table.entries().get(index), index);
        }
        count(table, slots);
    }

    private static void count(LootTable table, Map<LootEntry, Integer> slots) {
        int[] hits = new int[table.entries().size()];
        long pickedTotal = 0;
        long itemTotal = 0;
        RandomSource random = RandomSource.create(SEED);
        for (int trial = 0; trial < TRIALS; trial++) {
            List<LootEntry> picked = LootRoller.pick(table, random);
            pickedTotal += picked.size();
            for (LootEntry entry : picked) {
                hits[slots.get(entry)]++;
                itemTotal += entry.min() + entry.max();
            }
        }
        appears = new float[hits.length];
        for (int index = 0; index < hits.length; index++) {
            appears[index] = hits[index] / (float) TRIALS;
        }
        positions = pickedTotal / (float) TRIALS;
        stacks = itemTotal / 2.0f / TRIALS;
    }
}
