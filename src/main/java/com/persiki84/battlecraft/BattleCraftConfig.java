package com.persiki84.battlecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class BattleCraftConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File(FMLPaths.CONFIGDIR.get().toFile(), "battlecraft.json");

    public boolean modEnabled = true;
    public int minPlayersToStart = 2;
    public int lobbyTimeLimit = 120;
    public int lobbyFastStartTime = 5;
    public int surrenderVoteTimeout = 30;
    public int voteCooldown = 180;
    public int surrenderMinTime = 300;
    public int lobbyX = 0;
    public int lobbyY = 80;
    public int lobbyZ = 0;
    public List<String> teams = new ArrayList<>(List.of("Red", "Blue"));
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
            return GSON.fromJson(reader, BattleCraftConfig.class);
        } catch (Exception e) {
            BattleCraftConfig config = new BattleCraftConfig();
            config.save();
            return config;
        }
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
