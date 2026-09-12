package com.persiki84.battlecraft.client.island;

import com.persiki84.shared.client.ui.UiFlip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class IslandCover {

    private IslandCover() {}

    public static boolean turning(GuiGraphics graphics, float centerX, float centerY, float size, float alpha) {
        if (!IslandFlip.turning() || !IslandArt.carries()) return false;

        boolean fresh = IslandFlip.fresh();
        ResourceLocation face = fresh ? IslandArt.texture() : IslandArt.carriedTexture();
        int source = fresh ? IslandArt.edge() : IslandArt.carriedEdge();
        return UiFlip.card(graphics, face, source, centerX, centerY, size, IslandFlip.angle(),
                fresh ? IslandFlip.mash() : 0.0f, IslandImage.CORNER_SHARE, IslandFlip.seed(), alpha);
    }
}
