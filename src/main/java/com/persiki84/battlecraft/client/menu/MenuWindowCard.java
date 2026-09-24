package com.persiki84.battlecraft.client.menu;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.menu.MenuFace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

// WHY: карточка окна печётся один раз на состояние экрана, а не рисуется каждый кадр: соседей
// WHY: в меню бывает несколько, и живой интерфейс в мире стоил бы полного прохода на каждого
public final class MenuWindowCard {
    private static final float PIXELS_PER_UNIT = 2.5f;
    private static final int KEPT = 4;
    private static final int MIP_LEVELS = 4;
    private static final int BUFFER = 1536;
    private static final long REPAINT_FRAMES = 6L;
    private static final float NEAR = 1000.0f;
    private static final float FAR = 3000.0f;
    private static final float GUI_DEPTH = -2000.0f;

    private static final Map<Key, Entry> baked = new LinkedHashMap<>(KEPT, 0.75f, true);
    private static final MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(new BufferBuilder(BUFFER));
    private static boolean failed;
    private static long repainted = -1L;

    private MenuWindowCard() {}

    public static int texture(MenuFace face) {
        if (failed) return 0;

        Key key = new Key(face, Minecraft.getInstance().getLanguageManager().getSelected());
        Entry entry = baked.get(key);
        try {
            if (entry == null) {
                entry = new Entry(surface());
                baked.put(key, entry);
                evict();
            }
            refresh(entry, face);
        } catch (Throwable error) {
            failed = true;
            LogUtils.getLogger().warn("[battlecraft] menu window card off: {}", String.valueOf(error));
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
            return 0;
        }
        return entry.target.getColorTextureId();
    }

    // WHY: акцент переезжает плавно, и перепекание на каждом кадре переезда стоило бы полного прохода
    // WHY: интерфейса на карточку; карточка догоняет цвет не чаще раза в REPAINT_FRAMES кадров
    private static void refresh(Entry entry, MenuFace face) {
        int accent = UiAccent.color();
        if (entry.accent != null && entry.accent == accent) return;
        long frame = UiFrame.frame();
        if (entry.accent != null && frame - repainted < REPAINT_FRAMES) return;

        repainted = frame;
        entry.accent = accent;
        bake(entry.target, face);
    }

    public static void forget() {
        for (Entry entry : baked.values()) entry.target.destroyBuffers();
        baked.clear();
    }

    private static void evict() {
        Iterator<Entry> oldest = baked.values().iterator();
        while (baked.size() > KEPT && oldest.hasNext()) {
            oldest.next().target.destroyBuffers();
            oldest.remove();
        }
    }

    private static void bake(TextureTarget target, MenuFace face) {
        target.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);

        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        try {
            paint(target, face);
        } finally {
            restore(projection, sorting, depth);
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }
        mipmaps(target);
    }

    // WHY: мера интерфейса в карточке та же, что на экране: pixelsPerUnit берёт масштаб позы
    // WHY: на масштаб окна, поэтому поза ужимает карточку до её единиц, а текст печётся резко
    private static void paint(TextureTarget target, MenuFace face) {
        Minecraft client = Minecraft.getInstance();
        double gui = Math.max(1.0, client.getWindow().getGuiScale());
        float spanX = (float) (target.width / gui);
        float spanY = (float) (target.height / gui);

        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0.0f, spanX, spanY, 0.0f, NEAR, FAR),
                VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack view = RenderSystem.getModelViewStack();
        view.pushPose();
        view.setIdentity();
        view.translate(0.0f, 0.0f, GUI_DEPTH);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        GuiGraphics graphics = new GuiGraphics(client, buffers);
        graphics.pose().scale(spanX / MenuWindowLayout.WIDTH, spanY / MenuWindowLayout.HEIGHT, 1.0f);
        MenuWindowLayout.paint(graphics, client.font, face);
        graphics.flush();
    }

    private static void restore(Matrix4f projection, VertexSorting sorting, boolean depth) {
        RenderSystem.getModelViewStack().popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(projection, sorting);
        UiRender.standardBlend();
        if (depth) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
    }

    // WHY: карточку видно и с пятнадцати метров, где она ужата в несколько раз: без мип-уровней
    // WHY: тонкие буквы и полосы рябили бы при каждом шаге наблюдателя
    private static void mipmaps(TextureTarget target) {
        GlStateManager._bindTexture(target.getColorTextureId());
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GlStateManager._bindTexture(0);
    }

    private static TextureTarget surface() {
        int width = Math.round(MenuWindowLayout.WIDTH * PIXELS_PER_UNIT);
        int height = Math.round(MenuWindowLayout.HEIGHT * PIXELS_PER_UNIT);
        TextureTarget target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        target.setFilterMode(GL11.GL_LINEAR);
        GlStateManager._bindTexture(target.getColorTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, MIP_LEVELS);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        deepen(target);
        GlStateManager._bindTexture(0);
        return target;
    }

    // WHY: интерфейс пишет в прозрачную цель квадрат своей альфы, и у бледных полос в восьми битах
    // WHY: он обнуляется; та же причина, что у стадии проявления
    private static void deepen(TextureTarget target) {
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA16, target.width, target.height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_SHORT, (ByteBuffer) null);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.frameBufferId);
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE) return;

        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, target.width, target.height, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
    }

    private record Key(MenuFace face, String language) {}

    private static final class Entry {
        private final TextureTarget target;
        private Integer accent;

        private Entry(TextureTarget target) {
            this.target = target;
        }
    }
}
