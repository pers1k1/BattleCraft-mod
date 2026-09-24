package com.persiki84.battlecraft.client.menu;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class MenuFrameClient {
    private static final Map<UUID, Live> frames = new HashMap<>();
    private static final Map<UUID, Long> latest = new HashMap<>();
    private static long tickets;

    private MenuFrameClient() {}

    // WHY: распаковка идёт в фоне, и два кадра подряд могут вернуться не по порядку: ставится
    // WHY: только последний присланный, иначе окно откатывалось бы к прошлому состоянию
    public static void accept(UUID player, byte[] image) {
        long ticket = ++tickets;
        latest.put(player, ticket);
        CompletableFuture.supplyAsync(() -> MenuFrameCodec.decode(image), Util.backgroundExecutor())
                .thenAcceptAsync(decoded -> install(player, ticket, decoded), Minecraft.getInstance());
    }

    public static Live frame(UUID player) {
        return frames.get(player);
    }

    public static void drop(UUID player) {
        latest.remove(player);
        Live gone = frames.remove(player);
        if (gone != null) gone.texture.close();
    }

    public static void forget() {
        for (Live live : frames.values()) live.texture.close();
        frames.clear();
        latest.clear();
    }

    private static void install(UUID player, long ticket, NativeImage decoded) {
        if (decoded == null) return;
        Long wanted = latest.get(player);
        if (wanted == null || wanted != ticket) {
            decoded.close();
            return;
        }

        DynamicTexture texture = new DynamicTexture(decoded);
        texture.setFilter(true, false);
        GlStateManager._bindTexture(texture.getId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._bindTexture(0);

        Live previous = frames.put(player, new Live(texture, decoded.getWidth(), decoded.getHeight()));
        if (previous != null) previous.texture.close();
    }

    public record Live(DynamicTexture texture, int width, int height) {}
}
