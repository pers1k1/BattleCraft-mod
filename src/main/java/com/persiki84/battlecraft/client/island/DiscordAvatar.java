package com.persiki84.battlecraft.client.island;

import com.mojang.blaze3d.platform.NativeImage;
import com.persiki84.battlecraft.BattleCraftMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DiscordAvatar {
    private static final ResourceLocation TARGET = new ResourceLocation("battlecraft", "island/avatar");
    private static final String ENDPOINT = "https://cdn.discordapp.com/avatars/%s/%s.png?size=128";
    private static final String FOLDER = "battlecraft";
    private static final String CACHE = "discord-avatar-%s.png";
    private static final int TIMEOUT_MS = 6000;
    private static final int SIZE_LIMIT = 1 << 20;

    private static volatile boolean ready;
    private static volatile int edge = 128;
    private static String loadedHash = "";

    private DiscordAvatar() {}

    public static void remember(String userId, String avatarHash) {
        if (userId == null || userId.isEmpty() || avatarHash == null || avatarHash.isEmpty()) {
            BattleCraftMod.LOGGER.info("[battlecraft] discord gave no avatar: id={} hash={}", userId, avatarHash);
            return;
        }
        if (avatarHash.equals(loadedHash)) return;

        loadedHash = avatarHash;
        BattleCraftMod.LOGGER.info("[battlecraft] discord avatar requested for {} hash {}", userId, avatarHash);
        Thread worker = new Thread(() -> fetch(userId, avatarHash), "battlecraft-avatar");
        worker.setDaemon(true);
        worker.start();
    }

    public static boolean ready() {
        return ready;
    }

    public static int edge() {
        return edge;
    }

    public static ResourceLocation texture() {
        return TARGET;
    }

    private static void fetch(String userId, String avatarHash) {
        try {
            Path cache = cacheFile(avatarHash);
            byte[] encoded = Files.isRegularFile(cache) ? Files.readAllBytes(cache) : download(userId, avatarHash);
            if (encoded.length == 0) return;

            if (!Files.isRegularFile(cache)) {
                Files.createDirectories(cache.getParent());
                Files.write(cache, encoded);
            }
            Minecraft.getInstance().execute(() -> upload(encoded));
        } catch (Exception error) {
            BattleCraftMod.LOGGER.warn("[battlecraft] discord avatar unavailable: {}", error.toString());
        }
    }

    private static Path cacheFile(String avatarHash) {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(FOLDER)
                .resolve(String.format(CACHE, avatarHash));
    }

    private static byte[] download(String userId, String avatarHash) throws Exception {
        URI address = URI.create(String.format(ENDPOINT, userId, avatarHash));
        HttpURLConnection connection = (HttpURLConnection) address.toURL().openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "BattleCraft");

        try (InputStream stream = connection.getInputStream()) {
            return stream.readNBytes(SIZE_LIMIT);
        } finally {
            connection.disconnect();
        }
    }

    private static void upload(byte[] encoded) {
        NativeImage image = null;
        try {
            image = NativeImage.read(new ByteArrayInputStream(encoded));
            IslandImage.round(image, IslandImage.CORNER_SHARE);
            edge = Math.min(image.getWidth(), image.getHeight());
            BattleCraftMod.LOGGER.info("[battlecraft] discord avatar ready, {} px", edge);
            Minecraft.getInstance().getTextureManager().register(TARGET, new DynamicTexture(image));
            ready = true;
        } catch (Exception error) {
            if (image != null) image.close();
            BattleCraftMod.LOGGER.warn("[battlecraft] discord avatar rejected: {}", error.toString());
        }
    }

}
