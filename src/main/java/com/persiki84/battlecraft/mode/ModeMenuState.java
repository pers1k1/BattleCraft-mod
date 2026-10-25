package com.persiki84.battlecraft.mode;

import com.persiki84.shared.menu.MenuStates;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

public final class ModeMenuState {
    public static final String MENU_ID = "modes";
    public static final String ACTIVE = "active";
    public static final String LOCKED = "locked";
    public static final String RUNNING = "running";
    public static final String MODES = "modes";
    public static final String QUARRIES = "quarries";
    public static final String POINT_SCORE = "pointScore";
    public static final String KILL_SCORE = "killScore";
    public static final String VEHICLE_SCORE = "vehicleScore";
    public static final String TRANSPORT_SCORE = "transportScore";
    public static final String TARGET = "scoreTarget";
    public static final String CAP = "capMinutes";
    public static final String SUDDEN_MARGIN = "suddenMargin";
    public static final String SUDDEN_MINUTES = "suddenMinutes";
    public static final String MONEY = "startMoney";
    public static final String DESCRIPTION = "description";
    public static final String KIT = "kit";
    public static final String ITEM = "item";
    public static final String TAG = "tag";
    public static final String COUNT = "count";

    private ModeMenuState() {}

    public static void register() {
        MenuStates.register(MENU_ID, 2, ModeMenuState::snapshot);
    }

    public static CompoundTag snapshot(ServerPlayer viewer) {
        CompoundTag state = new CompoundTag();
        state.putString(ACTIVE, ModeBook.active().id());
        state.putBoolean(LOCKED, ModeBook.locked());
        MatchMode running = MatchScore.mode();
        state.putString(RUNNING, running == null ? "" : running.id().id());
        CompoundTag modes = new CompoundTag();
        for (ModeId id : ModeId.values()) {
            modes.put(id.id(), describe(ModeBook.mode(id)));
        }
        state.put(MODES, modes);
        return state;
    }

    // WHY: игрок в правилах видит цифры идущего матча: правка пресета посреди матча на матч не влияет,
    // WHY: и книга разошлась бы с HUD. Набор берётся из книги: в снимке матча его нет
    public static CompoundTag livePlayerView(ServerPlayer viewer) {
        CompoundTag state = snapshot(viewer);
        MatchMode running = MatchScore.mode();
        if (running == null) return state;
        CompoundTag modes = state.getCompound(MODES);
        CompoundTag live = describe(running);
        live.put(KIT, modes.getCompound(running.id().id()).getList(KIT, Tag.TAG_COMPOUND));
        modes.put(running.id().id(), live);
        return state;
    }

    private static CompoundTag describe(MatchMode mode) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(QUARRIES, mode.quarries());
        tag.putInt(POINT_SCORE, mode.pointScore());
        tag.putInt(KILL_SCORE, mode.killScore());
        tag.putInt(VEHICLE_SCORE, mode.vehicleScore());
        tag.putInt(TRANSPORT_SCORE, mode.transportScore());
        tag.putInt(TARGET, mode.scoreTarget());
        tag.putInt(CAP, mode.capMinutes());
        tag.putInt(SUDDEN_MARGIN, mode.suddenMargin());
        tag.putInt(SUDDEN_MINUTES, mode.suddenMinutes());
        tag.putLong(MONEY, mode.startMoney());
        tag.putString(DESCRIPTION, mode.description());
        ListTag kit = new ListTag();
        for (KitEntry entry : mode.kit()) {
            CompoundTag row = new CompoundTag();
            row.putString(ITEM, entry.itemId());
            row.putString(TAG, entry.tag());
            row.putInt(COUNT, entry.count());
            kit.add(row);
        }
        tag.put(KIT, kit);
        return tag;
    }
}
