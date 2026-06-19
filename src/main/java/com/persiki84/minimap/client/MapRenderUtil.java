package com.persiki84.minimap.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;

public class MapRenderUtil {
    public static int getPlayerTeamColor(String playerName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            var scoreboard = mc.level.getScoreboard();
            var team = scoreboard.getPlayersTeam(playerName);
            if (team != null && team.getColor().getColor() != null) {
                return 0xFF000000 | team.getColor().getColor();
            }
        }
        return 0xFFFF0000;
    }

    private static final int CORNER_SEGMENTS = 16;
    private static final float[] CORNER_X = new float[CORNER_SEGMENTS + 1];
    private static final float[] CORNER_Y = new float[CORNER_SEGMENTS + 1];

    static {
        float n = 3.0f;
        for (int i = 0; i <= CORNER_SEGMENTS; i++) {
            float theta = (float) Math.toRadians(i * (90.0f / CORNER_SEGMENTS));
            float cos = (float) Math.cos(theta);
            float sin = (float) Math.sin(theta);
            CORNER_X[i] = (float) (Math.signum(cos) * Math.pow(Math.abs(cos), 2.0 / n));
            CORNER_Y[i] = (float) (Math.signum(sin) * Math.pow(Math.abs(sin), 2.0 / n));
        }
    }

    public static void fillRoundedRect(GuiGraphics graphics, float x, float y, float width, float height, float radius, int color) {
        fillSquircle(graphics, x, y, width, height, radius, color);
    }

    public static void fillSquircle(GuiGraphics graphics, float x, float y, float width, float height, float radius, int color) {
        float a = (float)(color >> 24 & 255) / 255.0F;
        float r = (float)(color >> 16 & 255) / 255.0F;
        float g = (float)(color >> 8 & 255) / 255.0F;
        float b = (float)(color & 255) / 255.0F;

        Matrix4f matrix = graphics.pose().last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        addRect(builder, matrix, x + radius, y, width - radius * 2, height, r, g, b, a);
        addRect(builder, matrix, x, y + radius, radius, height - radius * 2, r, g, b, a);
        addRect(builder, matrix, x + width - radius, y + radius, radius, height - radius * 2, r, g, b, a);

        addPrecomputedCorner(builder, matrix, x + radius, y + radius, radius, 2, r, g, b, a);
        addPrecomputedCorner(builder, matrix, x + width - radius, y + radius, radius, 3, r, g, b, a);
        addPrecomputedCorner(builder, matrix, x + width - radius, y + height - radius, radius, 0, r, g, b, a);
        addPrecomputedCorner(builder, matrix, x + radius, y + height - radius, radius, 1, r, g, b, a);

        tesselator.end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void addRect(BufferBuilder builder, Matrix4f matrix, float x, float y, float width, float height, float r, float g, float b, float a) {
        builder.vertex(matrix, x, y + height, 0).color(r, g, b, a).endVertex();
        builder.vertex(matrix, x + width, y + height, 0).color(r, g, b, a).endVertex();
        builder.vertex(matrix, x + width, y, 0).color(r, g, b, a).endVertex();

        builder.vertex(matrix, x, y + height, 0).color(r, g, b, a).endVertex();
        builder.vertex(matrix, x + width, y, 0).color(r, g, b, a).endVertex();
        builder.vertex(matrix, x, y, 0).color(r, g, b, a).endVertex();
    }

    private static void addPrecomputedCorner(BufferBuilder builder, Matrix4f matrix, float cx, float cy, float radius, int quadrant, float r, float g, float b, float a) {
        for (int i = 0; i < CORNER_SEGMENTS; i++) {
            float px1 = CORNER_X[i];
            float py1 = CORNER_Y[i];
            float px2 = CORNER_X[i + 1];
            float py2 = CORNER_Y[i + 1];

            float x1 = 0, y1 = 0, x2 = 0, y2 = 0;

            switch (quadrant) {
                case 0:
                    x1 = cx + radius * px1; y1 = cy + radius * py1;
                    x2 = cx + radius * px2; y2 = cy + radius * py2;
                    break;
                case 1:
                    x1 = cx - radius * py1; y1 = cy + radius * px1;
                    x2 = cx - radius * py2; y2 = cy + radius * px2;
                    break;
                case 2:
                    x1 = cx - radius * px1; y1 = cy - radius * py1;
                    x2 = cx - radius * px2; y2 = cy - radius * py2;
                    break;
                case 3:
                    x1 = cx + radius * py1; y1 = cy - radius * px1;
                    x2 = cx + radius * py2; y2 = cy - radius * px2;
                    break;
            }

            builder.vertex(matrix, cx, cy, 0).color(r, g, b, a).endVertex();
            builder.vertex(matrix, x2, y2, 0).color(r, g, b, a).endVertex();
            builder.vertex(matrix, x1, y1, 0).color(r, g, b, a).endVertex();
        }
    }
}
