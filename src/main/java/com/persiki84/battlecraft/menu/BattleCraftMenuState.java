package com.persiki84.battlecraft.menu;

import com.persiki84.battlecraft.BattleCraftConfig;
import com.persiki84.battlecraft.BattleCraftManager;
import com.persiki84.battlecraft.LobbyRoster;
import com.persiki84.battlecraft.MatchRequirements;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import com.persiki84.shared.menu.MenuStates;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public final class BattleCraftMenuState {
    public static final String MENU_ID = "battlecraft";
    public static final String SOFT_DISABLED = "softDisabled";
    public static final String IN_TEAM = "inTeam";
    public static final String REQUIREMENTS = "requirements";
    public static final String RULE_ID = "rule";
    public static final String RULE_MET = "met";
    public static final String RULE_HAVE = "have";
    public static final String RULE_NEED = "need";

    private BattleCraftMenuState() {}

    public static void register() {
        MenuStates.register(MENU_ID, 0, BattleCraftMenuState::snapshot);
    }

    private static CompoundTag snapshot(ServerPlayer player) {
        BattleCraftConfig config = BattleCraftManager.getInstance().getConfig();
        CompoundTag tag = new CompoundTag();

        putLobby(tag, config);
        putRules(tag, config);
        putStatus(tag, config, player);
        putModules(tag);
        tag.putBoolean(SOFT_DISABLED, BattleCraftManager.getInstance().isSoftDisabled());
        return tag;
    }

    private static void putStatus(CompoundTag tag, BattleCraftConfig config, ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        BattleCraftManager manager = BattleCraftManager.getInstance();
        int online = server.getPlayerList().getPlayerCount();
        int ready = manager.readyCount(server);
        List<String> teams = LobbyRoster.pool(server);

        tag.putString("phase", manager.getPhase().name());
        tag.putBoolean(IN_TEAM, player.getTeam() != null);
        tag.putInt("online", online);
        tag.putInt("ready", ready);
        tag.putInt("required", Math.max(1, (int) Math.ceil(online * (config.readyPercentToStart / 100.0))));
        tag.putInt("teams", teams.size());
        tag.putInt("undecided", LobbyRoster.withoutTeam(server, teams).size());
        tag.putInt("grace", manager.graceSecondsLeft());
        tag.put(REQUIREMENTS, requirements(server, manager));
    }

    private static ListTag requirements(MinecraftServer server, BattleCraftManager manager) {
        ListTag list = new ListTag();
        for (MatchRequirements.Status status : manager.requirements(server)) {
            CompoundTag entry = new CompoundTag();
            entry.putString(RULE_ID, status.rule().id());
            entry.putBoolean(RULE_MET, status.met());
            entry.putInt(RULE_HAVE, status.have());
            entry.putInt(RULE_NEED, status.need());
            list.add(entry);
        }
        return list;
    }

    public static List<CompoundTag> requirementsOf(CompoundTag state) {
        List<CompoundTag> entries = new ArrayList<>();
        for (Tag tag : state.getList(REQUIREMENTS, Tag.TAG_COMPOUND)) {
            entries.add((CompoundTag) tag);
        }
        return entries;
    }

    private static void putModules(CompoundTag tag) {
        for (ModuleId module : ModuleId.values()) {
            tag.putBoolean(moduleKey(module), ModuleSwitches.allows(module));
        }
    }

    public static String moduleKey(ModuleId module) {
        return "module." + module.id();
    }

    private static void putLobby(CompoundTag tag, BattleCraftConfig config) {
        tag.putInt("lobbyTimeLimit", config.lobbyTimeLimit);
        tag.putInt("lobbyFastStartTime", config.lobbyFastStartTime);
        tag.putInt("minPlayersToStart", config.minPlayersToStart);
        tag.putInt("readyPercentToStart", config.readyPercentToStart);
        tag.putInt("readyToggleCooldownSeconds", config.readyToggleCooldownSeconds);
        tag.putInt("lobbyX", config.lobbyX);
        tag.putInt("lobbyY", config.lobbyY);
        tag.putInt("lobbyZ", config.lobbyZ);
    }

    private static void putRules(CompoundTag tag, BattleCraftConfig config) {
        tag.putInt("joinGraceSeconds", config.joinGraceSeconds);
        tag.putInt("autoAssignSeconds", config.autoAssignSeconds);
        tag.putInt("matchJoinChoiceSeconds", config.matchJoinChoiceSeconds);
        tag.putInt("teamSwitchCooldownSeconds", config.teamSwitchCooldownSeconds);
        tag.putInt("surrenderVoteTimeout", config.surrenderVoteTimeout);
        tag.putInt("voteCooldown", config.voteCooldown);
        tag.putInt("surrenderMinTime", config.surrenderMinTime);
        tag.putInt("sprintStaminaDashPercent", config.sprintStaminaDashPercent);
        tag.putInt("armedSprintStaminaDashPercent", config.armedSprintStaminaDashPercent);
        tag.putInt("jumpStaminaPermille", config.jumpStaminaPermille);
        tag.putInt("armedJumpStaminaPermille", config.armedJumpStaminaPermille);
        tag.putInt("teamsNeeded", config.teamsNeeded);
        tag.putBoolean("requireTeams", config.requireTeams);
    }
}
