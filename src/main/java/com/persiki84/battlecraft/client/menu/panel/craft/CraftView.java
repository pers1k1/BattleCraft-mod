package com.persiki84.battlecraft.client.menu.panel.craft;

import com.persiki84.battlecraft.craft.CraftBan;
import com.persiki84.battlecraft.craft.CraftMenuState;
import com.persiki84.battlecraft.craft.CraftPhase;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// WHY: снимок меню разбирается один раз на его приход, а не в кадре: плитки держат готовые стаки
final class CraftView {
    private static final CraftPhase[] PHASES = CraftPhase.values();
    static final CraftView EMPTY = new CraftView(false, false, List.of());

    private final boolean arrived;
    private final boolean running;
    private final List<CraftBan> bans;
    private final Map<CraftPhase, List<CraftTile>> tiles = new EnumMap<>(CraftPhase.class);
    private int active;

    private CraftView(boolean arrived, boolean running, List<CraftBan> bans) {
        this.arrived = arrived;
        this.running = running;
        this.bans = bans;
        for (CraftPhase phase : CraftPhase.values()) {
            tiles.put(phase, new ArrayList<>());
        }
        for (CraftBan ban : bans) {
            CraftTile tile = CraftTile.of(ban, running);
            tiles.get(ban.phase()).add(tile);
            if (tile.active()) active++;
        }
    }

    static CraftView of(CompoundTag state) {
        if (!state.contains(CraftMenuState.BANS)) return EMPTY;

        List<CraftBan> bans = new ArrayList<>();
        ListTag rows = state.getList(CraftMenuState.BANS, Tag.TAG_COMPOUND);
        for (int index = 0; index < rows.size(); index++) {
            CraftBan ban = parse(rows.getCompound(index));
            if (ban != null) bans.add(ban);
        }
        return new CraftView(true, state.getBoolean(CraftMenuState.RUNNING), bans);
    }

    private static CraftBan parse(CompoundTag row) {
        ResourceLocation id = ResourceLocation.tryParse(row.getString(CraftMenuState.ITEM));
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return null;
        CompoundTag tag = row.contains(CraftMenuState.TAG, Tag.TAG_COMPOUND) ? row.getCompound(CraftMenuState.TAG) : null;
        CraftPhase phase = CraftPhase.byId(row.getString(CraftMenuState.PHASE));
        return new CraftBan(BuiltInRegistries.ITEM.get(id), tag, phase == null ? CraftPhase.ALWAYS : phase);
    }

    boolean arrived() {
        return arrived;
    }

    boolean running() {
        return running;
    }

    int total() {
        return bans.size();
    }

    int active() {
        return active;
    }

    List<CraftTile> tiles(CraftPhase phase) {
        return tiles.get(phase);
    }

    int indexOf(CraftPhase phase, String key) {
        List<CraftTile> shown = tiles.get(phase);
        for (int index = 0; index < shown.size(); index++) {
            if (shown.get(index).key().equals(key)) return index;
        }
        return -1;
    }

    // WHY: выбранное спрашивает строка фазы в каждом кадре, поэтому поиск идёт по номерам, без итераторов
    CraftTile find(String key) {
        if (key == null) return null;
        for (CraftPhase phase : PHASES) {
            List<CraftTile> shown = tiles.get(phase);
            for (int index = 0; index < shown.size(); index++) {
                CraftTile tile = shown.get(index);
                if (tile.key().equals(key)) return tile;
            }
        }
        return null;
    }
}
