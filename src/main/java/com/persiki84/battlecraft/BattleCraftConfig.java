package com.persiki84.battlecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class BattleCraftConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File(FMLPaths.CONFIGDIR.get().toFile(), "battlecraft.json");

    public boolean modEnabled = true;
    public int minPlayersToStart = 1;
    public int joinGraceSeconds = 30;
    public int autoAssignSeconds = 45;
    public int matchJoinChoiceSeconds = 20;
    public int teamSwitchCooldownSeconds = 30;
    public int readyToggleCooldownSeconds = 6;
    public int readyPercentToStart = 50;
    public int lobbyTimeLimit = 120;
    public int lobbyFastStartTime = 5;
    public int surrenderVoteTimeout = 30;
    public int voteCooldown = 180;
    public int surrenderMinTime = 300;
    public int lobbyX = 0;
    public int lobbyY = 80;
    public int lobbyZ = 0;
    public boolean lobbySet = false;
    public int sprintStaminaDashPercent = 85;
    public int armedSprintStaminaDashPercent = 105;
    public int jumpStaminaPermille = 10;
    public int armedJumpStaminaPermille = 7;
    public boolean requireTeams = true;
    public int teamsNeeded = 2;
    public List<String> startCommands = new ArrayList<>();
    public List<String> stopCommands = new ArrayList<>();
    public List<String> surrenderCommands = new ArrayList<>();

    public static BattleCraftConfig load() {
        if (!FILE.exists()) {
            BattleCraftConfig config = new BattleCraftConfig();
            config.save();
            return config;
        }
        try (FileReader reader = new FileReader(FILE)) {
            BattleCraftConfig stored = GSON.fromJson(reader, BattleCraftConfig.class);
            if (stored != null) return stored.withoutMissingLists();
        } catch (Exception ignored) {}

        BattleCraftConfig config = new BattleCraftConfig();
        config.save();
        return config;
    }

    private BattleCraftConfig withoutMissingLists() {
        if (startCommands == null) startCommands = new ArrayList<>();
        if (stopCommands == null) stopCommands = new ArrayList<>();
        if (surrenderCommands == null) surrenderCommands = new ArrayList<>();
        return this;
    }

    public void save() {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(this, writer);
        } catch (Exception ignored) {}
    }

    public BlockPos getLobbySpawn() {
        return new BlockPos(lobbyX, lobbyY, lobbyZ);
    }
}
