package com.persiki84.battlecraft.mode;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.modules.ModuleId;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// WHY: счёт идущего матча и снимок его режима. Живёт в match.dat рядом с фазой и временем: после
// WHY: краша матч продолжается с тем же счётом и теми же цифрами режима, а не с пресетом из файла
public final class MatchScore {
    public static final long NO_SUDDEN = -1L;

    private static final Map<String, Integer> scores = new HashMap<>();
    private static final Set<UUID> wrecked = new HashSet<>();
    @Nullable
    private static MatchMode mode;
    private static long scoredMinute;
    private static long suddenEndMillis = NO_SUDDEN;
    private static int version;

    private MatchScore() {}

    static void begin(MatchMode snapshot) {
        mode = snapshot;
        scores.clear();
        wrecked.clear();
        scoredMinute = 0L;
        suddenEndMillis = NO_SUDDEN;
        version++;
    }

    static void end() {
        mode = null;
        scores.clear();
        wrecked.clear();
        scoredMinute = 0L;
        suddenEndMillis = NO_SUDDEN;
        version++;
    }

    @Nullable
    public static MatchMode mode() {
        return mode;
    }

    public static boolean suppresses(ModuleId module) {
        return module == ModuleId.QUARRY && mode != null && !mode.quarries();
    }

    static void add(String team, int points) {
        if (mode == null || points <= 0 || team == null) return;
        scores.merge(team, points, Integer::sum);
        version++;
    }

    static int scoreOf(String team) {
        return scores.getOrDefault(team, 0);
    }

    static Map<String, Integer> scores() {
        return scores;
    }

    // WHY: одна машина приносит очки один раз: destroy у SuperbWarfare может прийти повторно
    static boolean firstWreck(UUID vehicle) {
        return wrecked.add(vehicle);
    }

    static long scoredMinute() { return scoredMinute; }
    static void scoredMinute(long minute) { scoredMinute = minute; }
    static long suddenEndMillis() { return suddenEndMillis; }

    static void suddenEndMillis(long value) {
        suddenEndMillis = value;
        version++;
    }

    static int version() {
        return version;
    }

    public static CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (mode == null) return tag;

        tag.putString("mode", mode.id().id());
        tag.putString("settings", snapshotOf(mode));
        CompoundTag teams = new CompoundTag();
        scores.forEach(teams::putInt);
        tag.put("scores", teams);
        tag.putLong("minute", scoredMinute);
        tag.putLong("sudden", suddenEndMillis);
        return tag;
    }

    // WHY: набор нужен только на старте, а строка NBT длиннее 65535 байт пишется пустой: с набором
    // WHY: стволов снимок после краша превращался в заводские цифры
    private static String snapshotOf(MatchMode running) {
        JsonObject settings = ModeJson.write(running, null);
        settings.remove("kit");
        return settings.toString();
    }

    public static void restore(CompoundTag tag, boolean running) {
        end();
        ModeId id = ModeId.byId(tag.getString("mode"));
        if (!running || id == null) return;

        begin(ModeJson.read(id, settingsOf(tag.getString("settings"))));
        CompoundTag teams = tag.getCompound("scores");
        for (String team : teams.getAllKeys()) {
            scores.put(team, Math.max(0, teams.getInt(team)));
        }
        scoredMinute = Math.max(0L, tag.getLong("minute"));
        suddenEndMillis = tag.contains("sudden") ? tag.getLong("sudden") : NO_SUDDEN;
    }

    private static JsonObject settingsOf(String text) {
        try {
            return JsonParser.parseString(text).getAsJsonObject();
        } catch (Exception broken) {
            BattleCraftMod.LOGGER.warn("Match mode snapshot is unreadable, preset defaults are used");
            return new JsonObject();
        }
    }
}
