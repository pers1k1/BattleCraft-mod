package com.persiki84.airdrop.loot;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

// WHY: бросок один на сервер и на пробный бросок редактора: клиент показывает шансы тем же кодом,
// WHY: которым сервер кладёт лут, иначе цифры в редакторе и содержимое ящика разъедутся
public final class LootRoller {
    private static final Comparator<LootEntry> GUARANTEED_FIRST =
            Comparator.comparingInt(entry -> entry.guaranteed() ? 0 : 1);

    private LootRoller() {}

    public static int fill(Container container, LootTable table, RandomSource random) {
        container.clearContent();
        List<ItemStack> stacks = roll(table, random);
        List<Integer> slots = shuffledSlots(container.getContainerSize(), random);

        int placed = Math.min(stacks.size(), slots.size());
        for (int index = 0; index < placed; index++) {
            container.setItem(slots.get(index), stacks.get(index));
        }
        container.setChanged();
        return placed;
    }

    public static List<ItemStack> roll(LootTable table, RandomSource random) {
        List<ItemStack> stacks = new ArrayList<>();
        for (LootEntry entry : pick(table, random)) {
            addSplit(stacks, entry, count(entry, random));
        }
        return stacks;
    }

    // WHY: записи перемешиваются до броска, иначе потолок позиций срезал бы всегда хвост списка,
    // WHY: а гарантированные встают первыми, чтобы потолок не выбрасывал именно их
    public static List<LootEntry> pick(LootTable table, RandomSource random) {
        List<LootEntry> order = new ArrayList<>(known(table));
        shuffle(order, random);

        List<LootEntry> picked = new ArrayList<>();
        List<LootEntry> missed = new ArrayList<>();
        for (LootEntry entry : order) {
            (random.nextFloat() < entry.chance() ? picked : missed).add(entry);
        }
        topUp(picked, missed, table.minItems(), random);
        picked.sort(GUARANTEED_FIRST);

        int cap = table.maxItems();
        return cap == LootTable.UNCAPPED || picked.size() <= cap ? picked : picked.subList(0, cap);
    }

    private static List<LootEntry> known(LootTable table) {
        List<LootEntry> known = new ArrayList<>();
        for (LootEntry entry : table.entries()) {
            if (entry.known()) known.add(entry);
        }
        return known;
    }

    // WHY: добор до минимума идёт по весу шанса без повторов: редкое остаётся редким и в доборе,
    // WHY: а одна запись не заполняет минимум собой
    private static void topUp(List<LootEntry> picked, List<LootEntry> missed, int wanted, RandomSource random) {
        while (picked.size() < wanted) {
            LootEntry drawn = weighted(missed, random);
            if (drawn == null) return;

            missed.remove(drawn);
            picked.add(drawn);
        }
    }

    private static LootEntry weighted(List<LootEntry> pool, RandomSource random) {
        float total = 0.0f;
        for (LootEntry entry : pool) {
            total += entry.chance();
        }
        if (total <= 0.0f) return null;

        float mark = random.nextFloat() * total;
        for (LootEntry entry : pool) {
            mark -= entry.chance();
            if (mark < 0.0f) return entry;
        }
        return pool.get(pool.size() - 1);
    }

    private static int count(LootEntry entry, RandomSource random) {
        if (entry.max() <= entry.min()) return entry.min();
        return entry.min() + random.nextInt(entry.max() - entry.min() + 1);
    }

    // WHY: количество больше стака клалось одной стопкой на сотни штук, которую игра не держит
    private static void addSplit(List<ItemStack> stacks, LootEntry entry, int count) {
        ItemStack template = entry.template();
        if (template.isEmpty()) return;

        int left = count;
        int limit = Math.max(1, template.getMaxStackSize());
        while (left > 0) {
            ItemStack piece = template.copy();
            piece.setCount(Math.min(limit, left));
            stacks.add(piece);
            left -= piece.getCount();
        }
    }

    private static List<Integer> shuffledSlots(int size, RandomSource random) {
        List<Integer> slots = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) {
            slots.add(slot);
        }
        shuffle(slots, random);
        return slots;
    }

    private static <T> void shuffle(List<T> list, RandomSource random) {
        for (int index = list.size() - 1; index > 0; index--) {
            Collections.swap(list, index, random.nextInt(index + 1));
        }
    }
}
