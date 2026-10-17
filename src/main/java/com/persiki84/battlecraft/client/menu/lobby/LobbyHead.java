package com.persiki84.battlecraft.client.menu.lobby;

import com.mojang.blaze3d.systems.RenderSystem;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public final class LobbyHead {
    private static final int SHADE = 0x000000;
    private static final float SHADE_ALPHA = 0.34f;
    private static final float FRAME = 1.5f;
    private static final float FRAME_RADIUS = 5.0f;
    private static final float GONE = 0.01f;

    private LobbyHead() {}

    // WHY: голова едет между колонками дробными координатами, а PlayerFaceRenderer берёт только целые:
    // WHY: место ставится матрицей, иначе голова в полёте прыгает по сетке отдельно от своей плашки
    public static void draw(GuiGraphics graphics, UUID uuid, float x, float y, int size, float alpha) {
        if (alpha <= GONE) return;

        UiRender.panel(graphics, x - FRAME, y - FRAME, size + FRAME * 2.0f, size + FRAME * 2.0f, FRAME_RADIUS,
                UiTheme.withAlpha(SHADE, SHADE_ALPHA * alpha));
        ResourceLocation skin = skinOf(uuid);
        if (skin != null) face(graphics, skin, x, y, size, alpha);
    }

    private static void face(GuiGraphics graphics, ResourceLocation skin, float x, float y, int size, float alpha) {
        float[] tone = RenderSystem.getShaderColor();
        float red = tone[0];
        float green = tone[1];
        float blue = tone[2];
        float base = tone[3];

        graphics.flush();
        RenderSystem.setShaderColor(red, green, blue, base * alpha);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0.0f);
            UiRender.standardBlend();
            PlayerFaceRenderer.draw(graphics, skin, 0, 0, size);
            graphics.flush();
        } finally {
            graphics.pose().popPose();
            RenderSystem.setShaderColor(red, green, blue, base);
            UiRender.standardBlend();
        }
    }

    private static ResourceLocation skinOf(UUID uuid) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return null;

        PlayerInfo info = connection.getPlayerInfo(uuid);
        return info == null ? null : info.getSkinLocation();
    }
}
