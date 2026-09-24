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
                stretch(graphics, IslandArt.texture(), centerX, centerY, size, alpha);
            }
            return true;
        }
        if (!allowFace || awaited()) return false;
        if (DiscordAvatar.ready()) {
            stretch(graphics, DiscordAvatar.texture(), centerX, centerY, size, alpha);
            return true;
        }

        ResourceLocation skin = ownSkin();
        return skin != null && head(graphics, skin, centerX, centerY, size, alpha);
    }

    // WHY: картинка кладётся дробными координатами на физическую сетку, а не целыми единицами
    // WHY: интерфейса: ванильный blit на морфинге пилюля-карточка дёргал её шагом в целую единицу,
    // WHY: то есть в два-четыре экранных пикселя, отдельно от плавно едущей пилюли
    private static void stretch(GuiGraphics graphics, ResourceLocation texture,
                                float centerX, float centerY, float size, float alpha) {
        float half = size / 2.0f;
        UiRender.image(graphics, texture, centerX - half, centerY - half, size, size, alpha);
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
    // WHY: трека игрок видит чужую картинку. Место держится пустой площадкой, пока обложка не пришла,
    // WHY: но только пока она действительно ожидается: у трека без обложки площадка висела пустой вечно
    private static boolean awaited() {
        return HudConfig.islandCover() && IslandModel.media() > 0.02f && IslandModel.blind() < 0.5f
                && !IslandArt.ready() && IslandArt.pending();
    }

    private static ResourceLocation ownSkin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return null;

        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? null : info.getSkinLocation();
    }
}
