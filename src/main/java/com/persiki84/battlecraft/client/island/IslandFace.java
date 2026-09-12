package com.persiki84.battlecraft.client.island;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;

public final class IslandFace {
    private static final int SHADE = 0x000000;
    private static final float SHADE_ALPHA = 0.28f;

    private IslandFace() {}

    public static void draw(GuiGraphics graphics, float centerX, float centerY, float size, float alpha,
                            boolean allowArt, boolean allowFace) {
        if (alpha <= 0.01f || size <= 1.0f) return;

        if (paint(graphics, centerX, centerY, size, alpha, allowArt, allowFace)) return;
        if (!allowFace && !awaited()) return;

        UiRender.panel(graphics, centerX - size / 2.0f, centerY - size / 2.0f, size, size,
                size * IslandImage.CORNER_SHARE, UiTheme.withAlpha(SHADE, SHADE_ALPHA * alpha));
    }

    private static boolean paint(GuiGraphics graphics, float centerX, float centerY, float size, float alpha,
                                 boolean allowArt, boolean allowFace) {
        if (allowArt && IslandArt.ready()) {
            if (!IslandCover.turning(graphics, centerX, centerY, size, alpha)) {
                stretch(graphics, IslandArt.texture(), IslandArt.edge(), centerX, centerY, size, alpha);
            }
            return true;
        }
        if (!allowFace || awaited()) return false;
        if (DiscordAvatar.ready()) {
            stretch(graphics, DiscordAvatar.texture(), DiscordAvatar.edge(), centerX, centerY, size, alpha);
            return true;
        }

        ResourceLocation skin = ownSkin();
        return skin != null && head(graphics, skin, centerX, centerY, size, alpha);
    }

    private static void stretch(GuiGraphics graphics, ResourceLocation texture, int source,
                                float centerX, float centerY, float size, float alpha) {
        int edge = Math.max(1, Math.round(size));
        int left = Math.round(centerX - size / 2.0f);
        int top = Math.round(centerY - size / 2.0f);

        graphics.setColor(1.0f, 1.0f, 1.0f, alpha);
        graphics.blit(texture, left, top, edge, edge, 0.0f, 0.0f, source, source, source, source);
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static boolean head(GuiGraphics graphics, ResourceLocation skin, float centerX, float centerY,
                                float size, float alpha) {
        float pixels = UiRender.pixels(graphics);
        if (pixels <= 0.01f) return false;

        int edge = IslandHead.fit(size * pixels);
        if (!IslandHead.prepare(skin, edge)) return false;

        float drawn = edge / pixels;
        UiRender.image(graphics, IslandHead.texture(), centerX - drawn / 2.0f, centerY - drawn / 2.0f,
                drawn, drawn, alpha);
        return true;
    }

    // WHY: обложка догружается мостом, и подставлять на это время аватар Discord нельзя: в карточке
    // WHY: трека игрок видит чужую картинку. Место держится пустой площадкой, пока обложка не пришла
    private static boolean awaited() {
        return HudConfig.islandCover() && IslandModel.media() > 0.02f && IslandModel.blind() < 0.5f
                && !IslandArt.ready();
    }

    private static ResourceLocation ownSkin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return null;

        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? null : info.getSkinLocation();
    }
}
