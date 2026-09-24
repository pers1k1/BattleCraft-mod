package com.persiki84.battlecraft.client.island;

import com.mojang.blaze3d.platform.NativeImage;
import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

public final class DiscordAvatar {
    private static final ResourceLocation TARGET = new ResourceLocation("battlecraft", "island/avatar");
    private static final String ENDPOINT = "https://cdn.discordapp.com/avatars/%s/%s.png?size=%d";
    private static final String FOLDER = "battlecraft";
    private static final String CACHE_PREFIX = "discord-avatar-";
    private static final String CACHE = CACHE_PREFIX + "%s-%d.png";
    private static final int TIMEOUT_MS = 6000;
    private static final int SIZE_LIMIT = 4 << 20;
    private static final int HTTP_OK = 200;
    private static final Pattern USER_ID = Pattern.compile("\\d{1,24}");
    private static final Pattern AVATAR_HASH = Pattern.compile("(a_)?[0-9a-fA-F]{32}");

    private static volatile boolean ready;
    private static volatile int edge = 1;

    private DiscordAvatar() {}

    public static boolean ready() {
        return ready;
    }

    public static int edge() {
        return edge;
    }

    public static ResourceLocation texture() {
        return TARGET;
    }

    // WHY: хеш и id приходят из именованного канала, который может открыть любой локальный процесс,
    // WHY: а оба ложатся в адрес запроса и в имя файла кэша. Всё, что не похоже на Discord, отбрасывается
    public static boolean acceptable(String userId, String avatarHash) {
        return userId != null && avatarHash != null
                && USER_ID.matcher(userId).matches() && AVATAR_HASH.matcher(avatarHash).matches();
    }

    public static boolean fetch(String userId, String avatarHash) {
        NativeImage[] levels = null;
        try {
            byte[] encoded = encoded(userId, avatarHash);
            levels = shaped(encoded);
            NativeImage[] carriedLevels = levels;
            Minecraft.getInstance().execute(() -> upload(carriedLevels));
            return true;
        } catch (Exception error) {
            IslandPicture.discard(levels);
            BattleCraftMod.LOGGER.warn("[battlecraft] discord avatar unavailable: {}", error.toString());
            return false;
        }
    }

    private static byte[] encoded(String userId, String avatarHash) throws IOException {
        Path cache = cacheFile(avatarHash);
        if (Files.isRegularFile(cache)) {
            byte[] cached = Files.readAllBytes(cache);
            if (decodable(cached)) return cached;
            Files.deleteIfExists(cache);
        }

        byte[] downloaded = download(userId, avatarHash);
        if (!decodable(downloaded)) throw new IOException("avatar is not an image");
        store(cache, downloaded);
        return downloaded;
    }

    private static boolean decodable(byte[] encoded) {
        try (NativeImage probe = NativeImage.read(new ByteArrayInputStream(encoded))) {
            return probe.getWidth() > 0 && probe.getHeight() > 0;
        } catch (Exception broken) {
            return false;
        }
    }

    private static NativeImage[] shaped(byte[] encoded) throws IOException {
        try (NativeImage decoded = NativeImage.read(new ByteArrayInputStream(encoded));
             NativeImage squared = IslandImage.squared(decoded)) {
            return IslandScale.chain(squared, IslandImage.CORNER_SHARE);
        }
    }

    private static Path cacheFile(String avatarHash) {
        return folder().resolve(String.format(CACHE, avatarHash, IslandScale.EDGE_LIMIT));
    }

    private static Path folder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(FOLDER);
    }

    // WHY: кэш пишется через временный файл: оборванная запись оставляла битый png, который потом
    // WHY: читался с диска каждый запуск, и аватар не появлялся уже никогда
    private static void store(Path cache, byte[] encoded) throws IOException {
        Files.createDirectories(cache.getParent());
        Path staging = cache.resolveSibling(cache.getFileName() + ".part");
        Files.write(staging, encoded);
        Files.move(staging, cache, StandardCopyOption.REPLACE_EXISTING);
        sweep(cache);
    }

    private static void sweep(Path kept) {
        try (DirectoryStream<Path> old = Files.newDirectoryStream(kept.getParent(), CACHE_PREFIX + "*.png")) {
            for (Path file : old) {
                if (!file.equals(kept)) Files.deleteIfExists(file);
            }
        } catch (IOException error) {
            BattleCraftMod.LOGGER.debug("[battlecraft] old discord avatars kept: {}", error.toString());
        }
    }

    private static byte[] download(String userId, String avatarHash) throws IOException {
        URI address = URI.create(String.format(ENDPOINT, userId, avatarHash, IslandScale.EDGE_LIMIT));
        HttpURLConnection connection = (HttpURLConnection) address.toURL().openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "BattleCraft");

        try {
            int status = connection.getResponseCode();
            if (status != HTTP_OK) throw new IOException("discord cdn answered " + status);
            try (InputStream stream = connection.getInputStream()) {
                byte[] body = stream.readNBytes(SIZE_LIMIT + 1);
                if (body.length > SIZE_LIMIT) throw new IOException("avatar is larger than " + SIZE_LIMIT);
                return body;
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void upload(NativeImage[] levels) {
        try {
            IslandPicture picture = IslandPicture.upload(levels);
            Minecraft.getInstance().getTextureManager().register(TARGET, picture);
            edge = picture.edge();
            ready = true;
            BattleCraftMod.LOGGER.info("[battlecraft] discord avatar ready, {} px with mipmaps", edge);
        } catch (Exception error) {
            IslandPicture.discard(levels);
            BattleCraftMod.LOGGER.warn("[battlecraft] discord avatar rejected: {}", error.toString());
        }
    }
}
