package com.persiki84.battlecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.persiki84.shared.WorldFiles;
import net.minecraft.core.BlockPos;
import net.minecraftforge.fml.loading.FMLPaths;


import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    public boolean ipLock = true;
    public List<String> startCommands = new ArrayList<>();
    public List<String> stopCommands = new ArrayList<>();
    public List<String> surrenderCommands = new ArrayList<>();

    // WHY: нечитаемый файл откладывается в сторону, а не затирается заводскими значениями: в нём
    // WHY: лежат координаты лобби и команды старта, и ошибка в одной запятой стирала бы их молча
    public static BattleCraftConfig load() {
        if (FILE.exists()) {
            try {
                BattleCraftConfig stored = GSON.fromJson(readText(FILE.toPath()), BattleCraftConfig.class);
                if (stored != null) return stored.withoutMissingLists();
            } catch (IOException | RuntimeException unreadable) {
                setAside();
            }
        }
        BattleCraftConfig config = new BattleCraftConfig();
        config.save();
        return config;
    }

    // WHY: прежние версии писали файл кодировкой системы, на Windows это cp1251: строгое чтение
    // WHY: UTF-8 откатывается на неё, иначе кириллица в командах старта превратилась бы в мусор
    private static String readText(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException legacy) {
            return new String(bytes, Charset.defaultCharset());
        }
    }

    private static void setAside() {
        try {
            Files.move(FILE.toPath(), FILE.toPath().resolveSibling("battlecraft.json.broken-" + System.currentTimeMillis()));
        } catch (IOException ignored) {
        }
    }

    private BattleCraftConfig withoutMissingLists() {
        if (startCommands == null) startCommands = new ArrayList<>();
        if (stopCommands == null) stopCommands = new ArrayList<>();
        if (surrenderCommands == null) surrenderCommands = new ArrayList<>();
        return this;
    }

    public void save() {
        Path target = FILE.toPath();
        Path temporary = target.resolveSibling("battlecraft.json.tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
            WorldFiles.moveIntoPlace(temporary, target);
        } catch (IOException ignored) {
        }
    }

    public BlockPos getLobbySpawn() {
        return new BlockPos(lobbyX, lobbyY, lobbyZ);
    }
}
