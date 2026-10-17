package com.persiki84.battlecraft.craft;

import com.persiki84.battlecraft.BattleCraftManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

public final class CraftMenuState {
    public static final String MENU_ID = "craft";
    public static final String BANS = "bans";
    public static final String ITEM = "item";
    public static final String TAG = "tag";
    public static final String PHASE = "phase";
    public static final String RUNNING = "running";

    private CraftMenuState() {}

    public static CompoundTag snapshot(ServerPlayer viewer) {
        CompoundTag state = new CompoundTag();
        ListTag list = new ListTag();
        for (CraftBan ban : CraftBanBook.all()) {
            list.add(entry(ban));
        }
        state.put(BANS, list);
        state.putBoolean(RUNNING, BattleCraftManager.getInstance().matchRunning());
        return state;
    }

    private static CompoundTag entry(CraftBan ban) {
        CompoundTag entry = new CompoundTag();
        entry.putString(ITEM, ban.itemId());
        if (ban.tag() != null) entry.put(TAG, ban.tag().copy());
        entry.putString(PHASE, ban.phase().id());
        return entry;
    }
}
