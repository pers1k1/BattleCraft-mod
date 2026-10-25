package com.persiki84.battlecraft.mode;

import com.google.gson.JsonObject;
import com.persiki84.capturepoints.capture.CapturePointManager;
import com.persiki84.shared.JsonRead;
import net.minecraft.server.MinecraftServer;

import java.util.EnumMap;
import java.util.function.Consumer;

// WHY: единственный владелец пресетов: любая правка проходит один путь - файл, затем рассылка
// WHY: лобби. Пока мир не открыт, правки молча не принимаются, а не пишутся в чужой мир
public final class ModeBook {
    private static final EnumMap<ModeId, MatchMode> modes = new EnumMap<>(ModeId.class);
    private static JsonObject root = new JsonObject();
    private static MinecraftServer server;
    private static ModeId active = ModeId.MAIN;
    private static boolean locked;
    private static int version;

    private ModeBook() {}

    static void open(MinecraftServer opened) {
        server = opened;
        root = ModeStore.load(opened);
        for (ModeId id : ModeId.values()) {
            modes.put(id, ModeJson.read(id, ModeStore.modeOf(root, id)));
        }
        ModeId saved = ModeId.byId(JsonRead.text(root, "active"));
        active = saved == null ? ModeId.MAIN : saved;
        Boolean savedLock = JsonRead.flag(root, "locked");
        locked = savedLock != null && savedLock;
        version++;
    }

    static void close() {
        server = null;
        modes.clear();
        root = new JsonObject();
        active = ModeId.MAIN;
        locked = false;
        version++;
    }

    public static MatchMode mode(ModeId id) {
        MatchMode mode = modes.get(id);
        return mode == null ? MatchMode.defaults(id) : mode;
    }

    public static ModeId active() {
        return active;
    }

    public static boolean locked() {
        return locked;
    }

    public static int version() {
        return version;
    }

    public static boolean edit(ModeId id, Consumer<MatchMode> change) {
        MatchMode mode = modes.get(id);
        if (server == null || mode == null) return false;
        change.accept(mode);
        changed();
        return true;
    }

    public static boolean active(ModeId next) {
        if (server == null) return false;
        active = next;
        changed();
        return true;
    }

    public static boolean locked(boolean next) {
        if (server == null) return false;
        locked = next;
        changed();
        return true;
    }

    private static void changed() {
        JsonObject listed = JsonRead.open(root, "modes");
        for (ModeId id : ModeId.values()) {
            listed.add(id.id(), ModeJson.write(mode(id), ModeStore.modeOf(root, id)));
        }
        root.addProperty("active", active.id());
        root.addProperty("locked", locked);
        ModeStore.save(server, root);
        version++;
        ModeSync.lobbyToAll(server);
        CapturePointManager.syncPoints();
    }
}
