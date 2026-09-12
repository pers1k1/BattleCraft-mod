package com.persiki84.shared.client.ui;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

// WHY: снимок фона, перенос кадра на плоскость и ореол держали три побайтово одинаковых копии
// WHY: полноэкранного квада, а история швов и чёрных экранов запрещает разъезд этого прохода
public final class UiQuad {

    private UiQuad() {}

    public static void screen() {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(-1.0f, -1.0f, 0.0f).uv(0.0f, 0.0f).endVertex();
        builder.vertex(1.0f, -1.0f, 0.0f).uv(1.0f, 0.0f).endVertex();
        builder.vertex(1.0f, 1.0f, 0.0f).uv(1.0f, 1.0f).endVertex();
        builder.vertex(-1.0f, 1.0f, 0.0f).uv(0.0f, 1.0f).endVertex();
        Tesselator.getInstance().end();
    }
}
