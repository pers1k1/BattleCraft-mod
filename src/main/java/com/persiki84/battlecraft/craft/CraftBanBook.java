package com.persiki84.battlecraft.craft;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// WHY: единственный владелец запретов на сервере. Каждая правка пишет файл, публикует снимок для
// WHY: поиска рецептов и рассылает его клиентам одним путём, поэтому команда и студия не расходятся
public final class CraftBanBook {
    public enum Outcome { ADDED, CHANGED, SAME }

    private static final Map<String, CraftBan> bans = new LinkedHashMap<>();
    private static final List<JsonObject> attic = new ArrayList<>();
    private static MinecraftServer server;

    private CraftBanBook() {}

    static void open(MinecraftServer opened) {
        server = opened;
        bans.clear();
        attic.clear();
        CraftBanStore.Loaded loaded = CraftBanStore.load(opened);
        for (CraftBan ban : loaded.bans()) {
            bans.put(ban.key(), ban);
        }
        attic.addAll(loaded.attic());
        CraftRules.publishServer(bans.values());
    }

    static void close() {
        server = null;
        bans.clear();
        attic.clear();
        CraftRules.publishServer(List.of());
    }

    public static List<CraftBan> all() {
        return List.copyOf(bans.values());
    }

    public static int count(CraftPhase phase) {
        int count = 0;
        for (CraftBan ban : bans.values()) {
            if (ban.phase() == phase) count++;
        }
        return count;
    }

    public static Outcome ban(CraftBan ban) {
        CraftBan known = bans.get(ban.key());
        if (known != null && known.phase() == ban.phase()) return Outcome.SAME;

        bans.put(ban.key(), ban);
        changed();
        return known == null ? Outcome.ADDED : Outcome.CHANGED;
    }

    public static int unban(Item item, @Nullable CompoundTag tag) {
        List<String> keys = keysOf(item, tag);
        for (String key : keys) {
            bans.remove(key);
        }
        if (!keys.isEmpty()) changed();
        return keys.size();
    }

    public static int rephase(Item item, @Nullable CompoundTag tag, CraftPhase phase) {
        int moved = 0;
        for (String key : keysOf(item, tag)) {
            CraftBan known = bans.get(key);
            if (known.phase() == phase) continue;
            bans.put(key, known.withPhase(phase));
            moved++;
        }
        if (moved > 0) changed();
        return moved;
    }

    public static int clear(@Nullable CraftPhase phase) {
        int before = bans.size();
        bans.values().removeIf(ban -> phase == null || ban.phase() == phase);
        int removed = before - bans.size();
        if (removed > 0) changed();
        return removed;
    }

    // WHY: точный запрет (предмет с тем же тегом) находится по ключу. Голый предмет без своего
    // WHY: запрета значит «все запреты этого предмета»: так снимается и меняет фазу всё, что висит
    // WHY: на нём, без набора тегов руками
    private static List<String> keysOf(Item item, @Nullable CompoundTag tag) {
        CraftBan probe = new CraftBan(item, tag, CraftPhase.ALWAYS);
        if (bans.containsKey(probe.key())) return List.of(probe.key());
        if (probe.tag() != null) return List.of();

        List<String> keys = new ArrayList<>();
        for (CraftBan ban : bans.values()) {
            if (ban.item() == item) keys.add(ban.key());
        }
        return keys;
    }

    private static void changed() {
        MinecraftServer current = Objects.requireNonNull(server, "craft bans are not open");
        CraftBanStore.save(current, List.copyOf(bans.values()), attic);
        CraftRules.publishServer(bans.values());
        CraftBanSync.everyone(current);
    }
}
