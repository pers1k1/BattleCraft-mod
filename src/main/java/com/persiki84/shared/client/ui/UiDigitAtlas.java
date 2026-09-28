package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

// WHY: поле расстояний цифр строится один раз в фоновом потоке (около полсекунды на холодной JVM),
// WHY: а в текстуру ложится только на рендер-потоке, при первом кадре после готовности
final class UiDigitAtlas {
    private static final ResourceLocation TYPEFACE = new ResourceLocation("battlecraft", "font/ui_semibold.ttf");
    private static final byte[] NO_TYPEFACE = new byte[0];
    private static final AtomicBoolean REQUESTED = new AtomicBoolean();

    private static volatile UiDigitSheet built;
    private static volatile boolean failed;
    private static UiDigitSheet uploaded;
    private static int texture;

    private UiDigitAtlas() {}

    static void request(ResourceProvider resources) {
        if (!REQUESTED.compareAndSet(false, true)) return;
        byte[] typeface = read(resources);
        Util.backgroundExecutor().execute(() -> build(typeface));
    }

    static UiDigitSheet sheet() {
        if (uploaded == null && built != null) upload(built);
        return uploaded;
    }

    static int texture() {
        return texture;
    }

    static boolean failed() {
        return failed;
    }

    private static byte[] read(ResourceProvider resources) {
        try (InputStream stream = resources.open(TYPEFACE)) {
            return stream.readAllBytes();
        } catch (IOException missing) {
            LogUtils.getLogger().warn("[battlecraft] clock typeface missing, system sans used: {}", missing.toString());
            return NO_TYPEFACE;
        }
    }

    private static void build(byte[] typeface) {
        try {
            built = UiDigitSheet.build(typeface(typeface));
        } catch (ThreadDeath | VirtualMachineError fatal) {
            failed = true;
            throw fatal;
        } catch (Throwable error) {
            failed = true;
            LogUtils.getLogger().warn("[battlecraft] clock digits off, plain text used: {}", error.toString());
        }
    }

    private static Font typeface(byte[] bytes) {
        if (bytes.length > 0) {
            try {
                return Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(bytes));
            } catch (FontFormatException | IOException broken) {
                LogUtils.getLogger().warn("[battlecraft] clock typeface unreadable, system sans used: {}",
                        broken.toString());
            }
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, 1);
    }

    private static void upload(UiDigitSheet sheet) {
        RenderSystem.assertOnRenderThread();
        texture = TextureUtil.generateTextureId();
        GlStateManager._bindTexture(texture);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 2);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R16, sheet.width(), sheet.height(), 0, GL11.GL_RED,
                GL11.GL_UNSIGNED_SHORT, sheet.field());
        GlStateManager._bindTexture(0);
        uploaded = sheet;
    }
}
