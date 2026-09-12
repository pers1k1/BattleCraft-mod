package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.network.C2SConfigReportPacket;
import com.persiki84.battlecraft.network.C2SConfigSnapshotPacket;
import com.persiki84.battlecraft.network.PacketHandler;
import com.persiki84.battlecraft.rules.ConfigManifest;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class ConfigGuard {
    private static final String ALGORITHM = "SHA-256";
    private static final String OWN_PREFIX = "battlecraft";
    private static final String SELL_FILE = "sellmod.json";
    private static final int CHUNK = 8192;
    private static final long MISSING = 0L;

    private static volatile long[] report;
    private static volatile Snapshot snapshot;

    private ConfigGuard() {}

    private static final class Snapshot {
        private final List<String> paths;
        private final long[] hashes;
        private int sent;

        private Snapshot(List<String> paths, long[] hashes) {
            this.paths = paths;
            this.hashes = hashes;
        }
    }

    public static void forget() {
        report = null;
        snapshot = null;
    }

    public static void check(List<String> paths) {
        List<String> wanted = List.copyOf(paths);
        Util.backgroundExecutor().execute(() -> report = digest(wanted));
    }

    public static void scan() {
        Util.backgroundExecutor().execute(() -> snapshot = collect());
    }

    public static void tick() {
        if (Minecraft.getInstance().getConnection() == null) {
            forget();
            return;
        }

        long[] ready = report;
        if (ready != null) {
            report = null;
            PacketHandler.INSTANCE.sendToServer(new C2SConfigReportPacket(ready));
        }
        pushSnapshot();
    }

    // WHY: снимок уходит пачками по одной за тик: ванильный C2S ограничен 32767 байтами, и список
    // WHY: в пятьсот путей с хешами в один пакет не влезает
    private static void pushSnapshot() {
        Snapshot pending = snapshot;
        if (pending == null) return;

        int total = pending.paths.size();
        int count = Math.min(C2SConfigSnapshotPacket.MAX_BATCH, total - pending.sent);
        long[] hashes = new long[count];
        System.arraycopy(pending.hashes, pending.sent, hashes, 0, count);
        List<String> paths = new ArrayList<>(pending.paths.subList(pending.sent, pending.sent + count));

        pending.sent += count;
        boolean last = pending.sent >= total;
        PacketHandler.INSTANCE.sendToServer(new C2SConfigSnapshotPacket(paths, hashes, last));
        if (last) snapshot = null;
    }

    private static long[] digest(List<String> paths) {
        long[] hashes = new long[paths.size()];
        for (int index = 0; index < paths.size(); index++) {
            Path file = resolve(paths.get(index));
            hashes[index] = file == null ? MISSING : hash(file);
        }
        return hashes;
    }

    // WHY: список путей приходит с сервера, а читает файлы клиент: без проверки на выход за каталог
    // WHY: конфигов сервер попросил бы хеш любого файла на чужой машине
    private static Path resolve(String path) {
        if (path.isEmpty() || path.length() > ConfigManifest.MAX_PATH) return null;
        if (path.indexOf('\\') >= 0 || path.indexOf(':') >= 0 || path.startsWith("/")) return null;

        Path root = root();
        Path target = root.resolve(path).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) return null;

        return target;
    }

    private static Snapshot collect() {
        List<String> paths = new ArrayList<>();
        Path root = root();

        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .map(file -> root.relativize(file).toString().replace('\\', '/'))
                    .filter(ConfigGuard::wanted)
                    .sorted()
                    .limit(ConfigManifest.MAX_FILES)
                    .forEach(paths::add);
        } catch (IOException failure) {
            BattleCraftMod.LOGGER.error("Failed to scan config folder", failure);
            return new Snapshot(List.of(), new long[0]);
        }

        long[] scanned = new long[paths.size()];
        for (int index = 0; index < paths.size(); index++) {
            scanned[index] = hash(root.resolve(paths.get(index)));
        }
        return new Snapshot(paths, scanned);
    }

    // WHY: настройки самой сборки у каждого игрока свои (интерфейс, правила и модули пишутся в
    // WHY: одиночной игре), поэтому наши файлы в эталон не берутся - иначе кик получают все
    private static boolean wanted(String path) {
        if (path.length() > ConfigManifest.MAX_PATH) return false;

        String name = path.substring(path.lastIndexOf('/') + 1);
        return !name.startsWith(OWN_PREFIX) && !name.equals(SELL_FILE);
    }

    private static long hash(Path file) {
        try (InputStream stream = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] buffer = new byte[CHUNK];
            int read;
            while ((read = stream.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return fold(digest.digest());
        } catch (IOException | NoSuchAlgorithmException failure) {
            return MISSING;
        }
    }

    private static long fold(byte[] digest) {
        long value = 0L;
        for (int index = 0; index < Long.BYTES; index++) {
            value = (value << 8) | (digest[index] & 0xFFL);
        }
        return value == MISSING ? 1L : value;
    }

    private static Path root() {
        return FMLPaths.CONFIGDIR.get().toAbsolutePath().normalize();
    }
}
