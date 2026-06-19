package com.persiki84.capturepoints.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.HashMap;
import java.util.Map;

public class CaptureHudOverlay {
    private static final int PILL_HEIGHT = 28;
    private static final int PILL_Y = 35;
    private static final int FINAL_PILL_Y = 70;
    private static final int PILL_SPACING = 8;
    private static final float RADIUS = 10.0f;

    private static final Map<String, Float> animatedProgress = new HashMap<>();
    private static final Map<String, Float> animatedWidth = new HashMap<>();
    private static final Map<String, Float> animatedPillAlpha = new HashMap<>();
    private static float centralAlpha = 0.0f;
    private static String centralPointName = null;
    private static boolean centralIsFinal = false;

    public static final IGuiOverlay HUD_CAPTURE = (gui, guiGraphics, partialTick, screenWidth, screenHeight) -> {
        if (com.persiki84.battlecraft.client.ClientGameData.isSoftDisabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.keyPlayerList.isDown()) return;

        renderProjectedMarkers(guiGraphics, mc, partialTick);

        if (com.persiki84.battlecraft.client.ClientGameData.getCurrentPhase() != com.persiki84.battlecraft.BattleCraftManager.GamePhase.ACTIVE) return;
        if (mc.player.getTeam() == null) return;

        float delta = mc.getDeltaFrameTime() * 0.05f;
        if (delta > 0.1f) delta = 0.1f;

        renderPoints(guiGraphics, screenWidth, ClientCaptureData.getAllPointOwners(), PILL_Y, false, delta);

        if (ClientCaptureData.areAllPointsCapturedBySameTeam()) {
            renderPoints(guiGraphics, screenWidth, ClientCaptureData.getAllFinalPointOwners(), FINAL_PILL_Y, true, delta);
        }

        boolean capturing = ClientCaptureData.isLocalPlayerCapturing();
        if (capturing) {
            centralAlpha = lerp(centralAlpha, 1.0f, 5f, delta);
            centralPointName = ClientCaptureData.getLocalCapturingPoint();
            centralIsFinal = ClientCaptureData.isLocalCapturingFinal();
        } else {
            centralAlpha = lerp(centralAlpha, 0.0f, 5f, delta);
        }

        if (centralAlpha > 0.0f && centralPointName != null) {
            renderCentralProgress(guiGraphics, screenWidth, screenHeight, centralIsFinal, delta, centralAlpha, centralPointName);
        }
    };

    private static void renderPoints(GuiGraphics guiGraphics, int screenWidth, Map<String, String> points, int baseY, boolean isFinal, float delta) {
        if (points.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        float totalWidth = 0;
        Map<String, Float> targetWidths = new HashMap<>();

        for (String pointName : points.keySet()) {
            float textWidth = mc.font.width(pointName);
            float targetW = Math.max(textWidth + 16, PILL_HEIGHT);
            targetWidths.put(pointName, targetW);
            float currentW = animatedWidth.getOrDefault(pointName, targetW);
            currentW = lerp(currentW, targetW, 10f, delta);
            animatedWidth.put(pointName, currentW);
            totalWidth += currentW;
        }
        totalWidth += PILL_SPACING * (points.size() - 1);

        float currentX = (screenWidth - totalWidth) / 2f;

        for (Map.Entry<String, String> entry : points.entrySet()) {
            String pointName = entry.getKey();
            String owner = entry.getValue();
            float width = animatedWidth.get(pointName);

            renderPill(guiGraphics, pointName, owner, currentX, baseY, width, isFinal, delta);
            currentX += width + PILL_SPACING;
        }
    }

    private static void renderPill(GuiGraphics guiGraphics, String pointName, String owner, float x, float y, float width, boolean isFinal, float delta) {
        Minecraft mc = Minecraft.getInstance();
        
        float pillAlpha = animatedPillAlpha.getOrDefault(pointName, 0f);
        pillAlpha = lerp(pillAlpha, 1.0f, 2f, delta);
        animatedPillAlpha.put(pointName, pillAlpha);

        if (pillAlpha < 0.05f) return;

        float targetProgress = ClientCaptureData.getProgress(pointName);
        float currentProgress = animatedProgress.getOrDefault(pointName, 0f);
        currentProgress = lerp(currentProgress, targetProgress, 10f, delta);
        animatedProgress.put(pointName, currentProgress);

        boolean isBeingCaptured = currentProgress > 0.01f;
        float blinkAlpha = 1.0f;
        if (isBeingCaptured && (System.currentTimeMillis() - ClientCaptureData.getLastUpdateTime(pointName)) > 500) {
            blinkAlpha = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 300.0);
        }

        if (isFinal) {
            fillRoundedRect(guiGraphics, x - 1.5f, y - 1.5f, width + 3.0f, PILL_HEIGHT + 3.0f, RADIUS + 1.5f, applyAlpha(CaptureColors.ACCENT_GOLD_BORDER, pillAlpha));
        } else {
            int borderColor = CaptureColors.BORDER_DEFAULT;
            if (owner != null && !isBeingCaptured) {
                borderColor = blendColors(toPastel(getColorFromChatFormatting(getTeamColor(owner)), 0.8f), CaptureColors.OUTLINE_BLACK, 0.5f);
            }
            fillRoundedRect(guiGraphics, x - 1.0f, y - 1.0f, width + 2.0f, PILL_HEIGHT + 2.0f, RADIUS + 1.0f, applyAlpha(borderColor, pillAlpha));
        }

        int bgColor = isFinal ? CaptureColors.PILL_BG_FINAL : CaptureColors.PILL_BG;
        if (owner != null && !isBeingCaptured) {
            bgColor = toPastel(getColorFromChatFormatting(getTeamColor(owner)), 1.0f);
        }
        
        fillRoundedRect(guiGraphics, x, y, width, PILL_HEIGHT, RADIUS, applyAlpha(bgColor, pillAlpha));

        if (isBeingCaptured) {
            int captureColor = toPastel(getColorFromChatFormatting(getCapturingTeamColor(pointName)), 0.9f);
            if (isFinal) {
                captureColor = blendColors(captureColor, CaptureColors.ACCENT_GOLD, 0.4f);
            }
            captureColor = applyAlpha(captureColor, blinkAlpha * pillAlpha);
            
            double scale = mc.getWindow().getGuiScale();
            int scissorX = (int) (x * scale);
            int scissorY = (int) (mc.getWindow().getScreenHeight() - (y + PILL_HEIGHT) * scale);
            int scissorW = (int) (width * currentProgress * scale);
            int scissorH = (int) (PILL_HEIGHT * scale);
            
            RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);
            fillRoundedRect(guiGraphics, x, y, width, PILL_HEIGHT, RADIUS, captureColor);
            RenderSystem.disableScissor();
        }

        int textWidth = mc.font.width(pointName);
        float textX = x + (width - textWidth) / 2f;
        float textY = y + (PILL_HEIGHT - mc.font.lineHeight) / 2f + 1;

        int textColor = isFinal ? CaptureColors.ACCENT_GOLD : CaptureColors.TEXT_WHITE;
        if (isBeingCaptured) textColor = applyAlpha(textColor, 0.5f + 0.5f * blinkAlpha);
        textColor = applyAlpha(textColor, pillAlpha);
        guiGraphics.drawString(mc.font, pointName, (int) textX, (int) textY, textColor, true);
    }

    private static void renderCentralProgress(GuiGraphics guiGraphics, int screenWidth, int screenHeight, boolean isFinal, float delta, float alphaMod, String pointName) {
        if (alphaMod < 0.05f) return;

        Minecraft mc = Minecraft.getInstance();
        
        float targetProgress = ClientCaptureData.getProgress(pointName);
        float currentProgress = animatedProgress.getOrDefault("central_" + pointName, 0f);
        currentProgress = lerp(currentProgress, targetProgress, 10f, delta);
        animatedProgress.put("central_" + pointName, currentProgress);

        float blinkAlpha = 1.0f;
        if (targetProgress > 0 && (System.currentTimeMillis() - ClientCaptureData.getLastUpdateTime(pointName)) > 500) {
            blinkAlpha = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 300.0);
        }
        float overallAlpha = alphaMod * blinkAlpha;

        float barWidth = 200f;
        float barHeight = 12f;
        float x = (screenWidth - barWidth) / 2f;
        float y = screenHeight / 2f + 70f;

        if (isFinal) {
            fillRoundedRect(guiGraphics, x - 1.5f, y - 1.5f, barWidth + 3.0f, barHeight + 3.0f, (barHeight + 3.0f) / 2f, applyAlpha(CaptureColors.ACCENT_GOLD_BORDER, alphaMod));
        }

        fillRoundedRect(guiGraphics, x, y, barWidth, barHeight, barHeight / 2f, applyAlpha(CaptureColors.CENTRAL_BG, alphaMod));

        if (currentProgress > 0.01f) {
            int captureColor = toPastel(getColorFromChatFormatting(getCapturingTeamColor(pointName)), 0.9f);
            if (isFinal) {
                captureColor = blendColors(captureColor, CaptureColors.ACCENT_GOLD, 0.4f);
            }
            captureColor = applyAlpha(captureColor, overallAlpha);
            
            double scale = mc.getWindow().getGuiScale();
            int scissorX = (int) (x * scale);
            int scissorY = (int) (mc.getWindow().getScreenHeight() - (y + barHeight) * scale);
            int scissorW = (int) (barWidth * currentProgress * scale);
            int scissorH = (int) (barHeight * scale);
            
            RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);
            fillRoundedRect(guiGraphics, x, y, barWidth, barHeight, barHeight / 2f, captureColor);
            RenderSystem.disableScissor();
        }

        String langKey = isFinal ? "capturepoints.hud.final_progress" : "capturepoints.hud.capture_progress";
        String text = Component.translatable(langKey, pointName, (int)(currentProgress * 100)).getString();
        int textWidth = mc.font.width(text);
        int textColor = isFinal ? CaptureColors.ACCENT_GOLD : CaptureColors.TEXT_WHITE;
        textColor = applyAlpha(textColor, overallAlpha);
        
        guiGraphics.drawString(mc.font, text, (int)(x + (barWidth - textWidth) / 2f), (int)(y - 14), textColor, true);

        RenderSystem.disableBlend();
    }

    private static void renderProjectedMarkers(GuiGraphics guiGraphics, Minecraft mc, float partialTick) {
        for (ProjectedMarker marker : MarkerRenderer.markersToRender) {
            int markerColor = CaptureColors.MARKER_DEFAULT;
            if (marker.isFinal) {
                markerColor = CaptureColors.ACCENT_GOLD;
            } else if (marker.explicitColor != null) {
                markerColor = marker.explicitColor;
            } else if (marker.owner != null) {
                markerColor = getColorFromChatFormatting(getTeamColor(marker.owner));
            }

            drawWorldMarker(guiGraphics, marker.screenX, marker.screenY, markerColor);

            if (marker.name != null) {
                String displayText = marker.name + " (" + (int) marker.distance + "m)";
                int textWidth = mc.font.width(displayText);
                float x = marker.screenX - textWidth / 2f;
                float y = marker.screenY - mc.font.lineHeight - 6;

                int outlineColor = CaptureColors.OUTLINE_BLACK;
                guiGraphics.drawString(mc.font, displayText, (int) x + 1, (int) y, outlineColor, false);
                guiGraphics.drawString(mc.font, displayText, (int) x - 1, (int) y, outlineColor, false);
                guiGraphics.drawString(mc.font, displayText, (int) x, (int) y + 1, outlineColor, false);
                guiGraphics.drawString(mc.font, displayText, (int) x, (int) y - 1, outlineColor, false);
                guiGraphics.drawString(mc.font, displayText, (int) x, (int) y, CaptureColors.TEXT_WHITE, false);
            }
        }
    }

    private static void drawWorldMarker(GuiGraphics guiGraphics, float cx, float cy, int color) {
        drawRhombus(guiGraphics, cx, cy, 4, CaptureColors.OUTLINE_BLACK);
        drawRhombus(guiGraphics, cx, cy, 3, color);
    }

    private static void drawRhombus(GuiGraphics guiGraphics, float cx, float cy, int halfSize, int color) {
        for (int dy = -halfSize; dy <= halfSize; dy++) {
            int width = halfSize - Math.abs(dy);
            guiGraphics.fill((int)(cx - width), (int)(cy + dy), (int)(cx + width + 1), (int)(cy + dy + 1), color);
        }
    }

    private static ChatFormatting getCapturingTeamColor(String pointName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            var uuid = ClientCaptureData.getCapturingPlayer(pointName);
            if (uuid != null && uuid.equals(mc.player.getUUID())) {
                var scoreboard = mc.level.getScoreboard();
                var team = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
                if (team != null) return team.getColor();
            } else if (uuid != null) {
                var player = mc.level.getPlayerByUUID(uuid);
                if (player != null) {
                    var team = player.getTeam();
                    if (team != null) return team.getColor();
                }
            }
        }
        return ChatFormatting.WHITE;
    }

    public static ChatFormatting getTeamColor(String teamName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            var scoreboard = mc.level.getScoreboard();
            var team = scoreboard.getPlayerTeam(teamName);
            if (team != null) {
                return team.getColor();
            }
        }
        return ChatFormatting.GRAY;
    }

    public static int getColorFromChatFormatting(ChatFormatting formatting) {
        Integer color = formatting.getColor();
        if (color != null) {
            return 0xFF000000 | color;
        }
        return 0xFF888888;
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

    private static int toPastel(int color, float alphaMod) {
        int a = (int) (((color >> 24) & 0xFF) * alphaMod);
        if (a == 0) a = (int) (255 * alphaMod);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        
        r = (r + 255) / 2;
        g = (g + 255) / 2;
        b = (b + 255) / 2;
        
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int applyAlpha(int color, float alphaMod) {
        int a = (int) (((color >> 24) & 0xFF) * alphaMod);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static int blendColors(int color1, int color2, float ratio) {
        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * ratio);
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void fillRoundedRect(GuiGraphics graphics, float x, float y, float width, float height, float radius, int color) {
        com.persiki84.minimap.client.MapRenderUtil.fillRoundedRect(graphics, x, y, width, height, radius, color);
    }
}
