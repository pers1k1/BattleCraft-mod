package com.persiki84.battlecraft.menu;

import com.persiki84.battlecraft.rules.ConfigManifest;
import com.persiki84.battlecraft.rules.GameRule;
import com.persiki84.battlecraft.rules.GameRules;
import com.persiki84.shared.menu.MenuStates;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public final class ConfigMenuState {
    public static final String MENU_ID = "configs";
    public static final String PATHS = "paths";
    public static final String WATCHED = "watched";

    private ConfigMenuState() {}

    public static void register() {
        MenuStates.register(MENU_ID, 2, ConfigMenuState::snapshot);
    }

    private static CompoundTag snapshot(ServerPlayer viewer) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (String path : ConfigManifest.paths()) {
            list.add(StringTag.valueOf(path));
        }

        tag.put(PATHS, list);
        tag.putBoolean(WATCHED, GameRules.allows(GameRule.CHECK_CONFIGS));
        return tag;
    }

    public static List<String> pathsOf(CompoundTag state) {
        List<String> paths = new ArrayList<>();
        for (Tag tag : state.getList(PATHS, Tag.TAG_STRING)) {
            paths.add(tag.getAsString());
        }
        return paths;
    }
}
