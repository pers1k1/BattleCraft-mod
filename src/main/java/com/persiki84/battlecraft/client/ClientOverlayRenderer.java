package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.BattleCraftManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.joml.Matrix4f;

public class ClientOverlayRenderer {
    private static final float RADIUS = 8.0f;
    private static float overlayAlpha = 0.0f;
    private static float voteOffset = 0.0f;
    private static float animatedFillWidth = -1f;
    private static long lastTime = 0;

    public static final IGuiOverlay HUD_OVERLAY = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        long now = System.currentTimeMillis();
        float delta = lastTime == 0 ? 0 : (now - lastTime) / 1000f;
        lastTime = now;
        if (delta > 0.1f) delta = 0.1f;

        boolean needsTeam = mc.player.getTeam() == null && !ClientGameData.isSoftDisabled() && ClientGameData.getCurrentPhase() == BattleCraftManager.GamePhase.LOBBY;
        boolean needsReady = mc.player.getTeam() != null && !ClientGameData.isReady() && !ClientGameData.isSoftDisabled() && ClientGameData.getCurrentPhase() == BattleCraftManager.GamePhase.LOBBY;

        float targetAlpha = (needsTeam || needsReady) ? 1.0f : 0.0f;
        overlayAlpha = lerp(overlayAlpha, targetAlpha, 2.0f, delta);

        if (overlayAlpha > 0.01f) {
            renderWarning(guiGraphics, mc, screenWidth, overlayAlpha, needsTeam);
        }

        if (ClientGameData.hasActiveVote()) {
            voteOffset = lerp(voteOffset, 1.0f, 2.0f, delta);
        } else {
            voteOffset = lerp(voteOffset, 0.0f, 2.0f, delta);
        }

        if (voteOffset > 0.01f) {
            renderVoteOverlay(guiGraphics, mc, screenWidth, screenHeight, voteOffset);
        }

        if (ClientGameData.getCurrentPhase() == BattleCraftManager.GamePhase.LOBBY) {
            float timer = ClientGameData.getInterpolatedLobbyTimer();
            int maxTimer = ClientGameData.getLobbyMaxTimer();
            if (timer > 0) {
                renderLobbyOverlay(guiGraphics, mc, screenWidth, timer, maxTimer, ClientGameData.getMissingPlayers());
            }
        }
    };

    private static void renderLobbyOverlay(GuiGraphics guiGraphics, Minecraft mc, int screenWidth, float timer, int maxTimer, String missing) {
        int secs = (int) Math.ceil(timer / 20f);
        Component text = Component.translatable("battlecraft.lobby.countdown", secs).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
        int textWidth = mc.font.width(text);
        
        float progressWidth = 180f;
        float progressHeight = 6f;
        float x = (screenWidth - progressWidth) / 2f;
        float y = 20f;
        
        float progress = maxTimer > 0 ? Math.min(1.0f, Math.max(0.0f, timer / (float)maxTimer)) : 0f;
        float targetFillWidth = progressWidth * progress;
        
        float delta = mc.getDeltaFrameTime() * 0.05f;
        if (delta > 0.1f) delta = 0.1f;
        if (animatedFillWidth < 0 || Math.abs(animatedFillWidth - targetFillWidth) > progressWidth / 2f) {
            animatedFillWidth = targetFillWidth;
        }
        animatedFillWidth = lerp(animatedFillWidth, targetFillWidth, 10f, delta);
        
        float textX = (screenWidth - textWidth) / 2f;
        guiGraphics.drawString(mc.font, text, (int)textX, (int)y, 0xFFFFFF, true);
        
        y += mc.font.lineHeight + 4f;
        
        fillRoundedRect(guiGraphics, x, y, progressWidth, progressHeight, progressHeight / 2f, 0x88000000);
        
        if (animatedFillWidth > progressHeight) {
            fillRoundedRect(guiGraphics, x, y, animatedFillWidth, progressHeight, progressHeight / 2f, 0xFFFFAA00);
        }

        if (missing != null && !missing.isEmpty()) {
            Component missingText = Component.translatable("battlecraft.overlay.waiting", missing).withStyle(ChatFormatting.GRAY);
            int mw = mc.font.width(missingText);
            int mwidth = mw + 16;
            int mheight = mc.font.lineHeight + 8;
            float mx = (screenWidth - mwidth) / 2f;
            float my = y + progressHeight + 6f;
            fillRoundedRect(guiGraphics, mx, my, mwidth, mheight, RADIUS, 0xAA1E1E1E);
            guiGraphics.drawString(mc.font, missingText, (int)(mx + 8), (int)(my + 4), 0xFFFFFF, false);
        }
    }

    private static void renderWarning(GuiGraphics guiGraphics, Minecraft mc, int screenWidth, float alpha, boolean needsTeam) {
        if (alpha < 0.05f) return;
        Component text = needsTeam 
            ? Component.translatable("battlecraft.warning.select_team").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
            : Component.translatable("battlecraft.warning.not_ready").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        int textWidth = mc.font.width(text);
        int padX = 12;
        int padY = 6;
        int width = textWidth + padX * 2;
        int height = mc.font.lineHeight + padY * 2;
        float x = (screenWidth - width) / 2f;
        float y = 15f;

        int color = ((int)(alpha * 180) << 24) | 0x1E1E1E;
        fillRoundedRect(guiGraphics, x, y, width, height, RADIUS, color);

        int baseColor = needsTeam ? 0xFF5555 : 0xFFAA00;
        int textColor = ((int)(alpha * 255) << 24) | baseColor;
        guiGraphics.drawString(mc.font, text, (int)(x + padX), (int)(y + padY), textColor, false);
    }

    private static void renderVoteOverlay(GuiGraphics guiGraphics, Minecraft mc, int screenWidth, int screenHeight, float animOffset) {
        if (animOffset < 0.05f) return;
        long remainingMs = ClientGameData.getVoteEndTime() - System.currentTimeMillis();
        int remainingSecs = Math.max(0, (int)(remainingMs / 1000));

        Component header = Component.translatable("battlecraft.vote.overlay.header", ClientGameData.getVoteTeam()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component details = Component.translatable("battlecraft.vote.overlay.details", ClientGameData.getYesCount(), ClientGameData.getTotalRequired(), remainingSecs).withStyle(ChatFormatting.WHITE);
        Component keys = Component.translatable("battlecraft.vote.overlay.keys").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);

        int w1 = mc.font.width(header);
        int w2 = mc.font.width(details);
        int w3 = mc.font.width(keys);
        int width = Math.max(w1, Math.max(w2, w3)) + 24;
        int height = mc.font.lineHeight * 3 + 18;

        float targetX = screenWidth - width - 15f;
        float startX = screenWidth + 10f;
        float x = startX + (targetX - startX) * animOffset;
        float y = (screenHeight - height) / 2f;

        fillRoundedRect(guiGraphics, x, y, width, height, RADIUS, 0xAA1E1E1E);

        guiGraphics.drawString(mc.font, header, (int)(x + 12), (int)(y + 8), 0xFFFFFF, true);
        guiGraphics.drawString(mc.font, details, (int)(x + 12), (int)(y + 8 + mc.font.lineHeight + 4), 0xFFFFFF, true);
        guiGraphics.drawString(mc.font, keys, (int)(x + 12), (int)(y + 8 + (mc.font.lineHeight + 4) * 2), 0xFFFFFF, true);
    }

    private static float lerp(float current, float target, float speed, float delta) {
        float diff = target - current;
        if (Math.abs(diff) < 0.005f) {
            return target;
        }
        float factor = speed * delta;
        if (factor > 1.0f) factor = 1.0f;
        return current + diff * factor;
    }

    private static void fillRoundedRect(GuiGraphics graphics, float x, float y, float width, float height, float radius, int color) {
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

        addCorner(builder, matrix, x + radius, y + radius, radius, 180, 270, r, g, b, a);
        addCorner(builder, matrix, x + width - radius, y + radius, radius, 270, 360, r, g, b, a);
        addCorner(builder, matrix, x + width - radius, y + height - radius, radius, 0, 90, r, g, b, a);
        addCorner(builder, matrix, x + radius, y + height - radius, radius, 90, 180, r, g, b, a);

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

    private static void addCorner(BufferBuilder builder, Matrix4f matrix, float cx, float cy, float radius, int startAngle, int endAngle, float r, float g, float b, float a) {
        int segments = 16;
        float angleStep = (endAngle - startAngle) / (float) segments;
        float n = 3.0f;
        for (int i = 0; i < segments; i++) {
            float theta1 = (float) Math.toRadians(startAngle + i * angleStep);
            float theta2 = (float) Math.toRadians(startAngle + (i + 1) * angleStep);

            float cos1 = (float) Math.cos(theta1);
            float sin1 = (float) Math.sin(theta1);
            float cos2 = (float) Math.cos(theta2);
            float sin2 = (float) Math.sin(theta2);

            float x1 = cx + radius * (float) (Math.signum(cos1) * Math.pow(Math.abs(cos1), 2.0 / n));
            float y1 = cy + radius * (float) (Math.signum(sin1) * Math.pow(Math.abs(sin1), 2.0 / n));
            float x2 = cx + radius * (float) (Math.signum(cos2) * Math.pow(Math.abs(cos2), 2.0 / n));
            float y2 = cy + radius * (float) (Math.signum(sin2) * Math.pow(Math.abs(sin2), 2.0 / n));

            builder.vertex(matrix, cx, cy, 0).color(r, g, b, a).endVertex();
            builder.vertex(matrix, x2, y2, 0).color(r, g, b, a).endVertex();
            builder.vertex(matrix, x1, y1, 0).color(r, g, b, a).endVertex();
        }
    }
}
