package com.persiki84.battlecraft.craft;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// WHY: снимок запретов неизменяем и публикуется одной ссылкой: его читают поиск рецептов в тике
// WHY: печей и подсказка в кадре, поэтому проверка обходится поиском по предмету без аллокаций
public final class CraftBanSet {
    public static final CraftBanSet EMPTY = new CraftBanSet(List.of(), Map.of(), Set.of());

    private final List<CraftBan> bans;
    private final Map<Item, CraftBan[]> byItem;
    private final Set<Item> variants;

    private CraftBanSet(List<CraftBan> bans, Map<Item, CraftBan[]> byItem, Set<Item> variants) {
        this.bans = bans;
        this.byItem = byItem;
        this.variants = variants;
    }

    public static CraftBanSet of(Collection<CraftBan> source) {
        if (source.isEmpty()) return EMPTY;

        Map<Item, List<CraftBan>> grouped = new IdentityHashMap<>();
        for (CraftBan ban : source) {
            grouped.computeIfAbsent(ban.item(), unused -> new ArrayList<>()).add(ban);
        }
        Map<Item, CraftBan[]> index = new IdentityHashMap<>();
        for (Map.Entry<Item, List<CraftBan>> entry : grouped.entrySet()) {
            index.put(entry.getKey(), entry.getValue().toArray(new CraftBan[0]));
        }
        return new CraftBanSet(List.copyOf(source), index, variantsOf(source));
    }

    private static Set<Item> variantsOf(Collection<CraftBan> source) {
        Set<Item> items = Collections.newSetFromMap(new IdentityHashMap<>());
        for (CraftBan ban : source) {
            if (ban.tag() != null) items.add(ban.item());
        }
        return items;
    }

    // WHY: образец результата рецепта несёт только то, что записано в рецепте, а вариант (зелье
    // WHY: в стреле, ствол со своим GunId) иногда собирается из сетки. Запрет варианта сверяется
    // WHY: с собранным результатом, а голый запрет предмета обходится образцом без сборки
    public boolean hasVariants(Item item) {
        return variants.contains(item);
    }

    public boolean isEmpty() {
        return bans.isEmpty();
    }

    public List<CraftBan> all() {
        return bans;
    }

    // WHY: на один предмет может лечь и запрет целиком, и запрет варианта с другой фазой: подсказка
    // WHY: обязана назвать тот, что действует сейчас, а не первый по списку
    @Nullable
    public CraftBan find(ItemStack stack, boolean matchRunning) {
        if (stack.isEmpty()) return null;
        CraftBan[] candidates = byItem.get(stack.getItem());
        if (candidates == null) return null;
        CraftBan waiting = null;
        for (CraftBan ban : candidates) {
            if (!ban.matches(stack)) continue;
            if (ban.activeWhen(matchRunning)) return ban;
            if (waiting == null) waiting = ban;
        }
        return waiting;
    }

    public boolean blocks(ItemStack stack, boolean matchRunning) {
        if (stack.isEmpty()) return false;
        CraftBan[] candidates = byItem.get(stack.getItem());
        if (candidates == null) return false;
        for (CraftBan ban : candidates) {
            if (ban.activeWhen(matchRunning) && ban.matches(stack)) return true;
        }
        return false;
    }
}
