package com.persiki84.battlecraft.client.menu.panel.mode;

import com.persiki84.battlecraft.mode.KitEntry;
import com.persiki84.battlecraft.mode.ModeId;
import com.persiki84.battlecraft.mode.ModeMenuState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// WHY: снимок разбирается один раз на приход: плитки и цифры строк читают готовое, а не NBT в кадре
public final class ModeView {
    public static final ModeView EMPTY = new ModeView(false, ModeId.MAIN, false, "");

    public record Settings(boolean quarries, int pointScore, int killScore, int vehicleScore, int transportScore,
                           int scoreTarget, int capMinutes, int suddenMargin, int suddenMinutes, int startMoney,
                           String description, List<KitTile> kit) {
        static final Settings NONE = new Settings(true, 0, 0, 0, 0, 0, 0, 0, 0, 0, "", List.of());
    }

    private final boolean arrived;
    private final ModeId active;
    private final boolean locked;
    private final String running;
    private final Map<ModeId, Settings> modes = new EnumMap<>(ModeId.class);

    private ModeView(boolean arrived, ModeId active, boolean locked, String running) {
        this.arrived = arrived;
        this.active = active;
        this.locked = locked;
        this.running = running;
    }

    public static ModeView of(CompoundTag state) {
        if (!state.contains(ModeMenuState.MODES)) return EMPTY;
        ModeId active = ModeId.byId(state.getString(ModeMenuState.ACTIVE));
        ModeView view = new ModeView(true, active == null ? ModeId.MAIN : active,
                state.getBoolean(ModeMenuState.LOCKED), state.getString(ModeMenuState.RUNNING));
        CompoundTag modes = state.getCompound(ModeMenuState.MODES);
        for (ModeId id : ModeId.values()) {
            view.modes.put(id, settings(modes.getCompound(id.id())));
        }
        return view;
    }

    private static Settings settings(CompoundTag tag) {
        return new Settings(tag.getBoolean(ModeMenuState.QUARRIES), tag.getInt(ModeMenuState.POINT_SCORE),
                tag.getInt(ModeMenuState.KILL_SCORE), tag.getInt(ModeMenuState.VEHICLE_SCORE),
                tag.getInt(ModeMenuState.TRANSPORT_SCORE), tag.getInt(ModeMenuState.TARGET), tag.getInt(ModeMenuState.CAP),
                tag.getInt(ModeMenuState.SUDDEN_MARGIN), tag.getInt(ModeMenuState.SUDDEN_MINUTES),
                (int) Math.min(Integer.MAX_VALUE, tag.getLong(ModeMenuState.MONEY)),
                tag.getString(ModeMenuState.DESCRIPTION), kit(tag));
    }

    private static List<KitTile> kit(CompoundTag tag) {
        ListTag rows = tag.getList(ModeMenuState.KIT, Tag.TAG_COMPOUND);
        List<KitTile> tiles = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            CompoundTag row = rows.getCompound(index);
            KitEntry entry = new KitEntry(row.getString(ModeMenuState.ITEM), row.getString(ModeMenuState.TAG),
                    row.getInt(ModeMenuState.COUNT));
            tiles.add(KitTile.of(index, entry));
        }
        return List.copyOf(tiles);
    }

    public boolean arrived() { return arrived; }
    public ModeId active() { return active; }
    public boolean locked() { return locked; }
    public String running() { return running; }

    public Settings settings(ModeId id) {
        return modes.getOrDefault(id, Settings.NONE);
    }
}
