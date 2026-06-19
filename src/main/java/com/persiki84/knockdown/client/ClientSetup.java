package com.persiki84.knockdown.client;

import com.persiki84.knockdown.KnockDownMod;
import com.persiki84.knockdown.cap.KnockdownProvider;
import com.persiki84.knockdown.config.KnockdownConfig;
import com.persiki84.knockdown.network.NetworkHandler;
import com.persiki84.knockdown.network.PacketReviveAction;
import com.persiki84.knockdown.network.PacketSelfRevive;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import com.persiki84.knockdown.network.PacketSurrenderAction;

@Mod.EventBusSubscriber(modid = KnockDownMod.MODID, value = Dist.CLIENT)
public class ClientSetup {
    private static float animBleedProgress = -1.0f;
    private static float animActionProgress = 0.0f;
    private static float animTargetActionProgress = 0.0f;
    private static long lastRenderTime = 0;

    private static float lerp(float current, float target, float speed, float delta) {
        float diff = target - current;
        if (Math.abs(diff) < 0.005f) {
            return target;
        }
        float factor = speed * delta;
        if (factor > 1.0f) factor = 1.0f;
        return current + diff * factor;
    }

    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            if (KeyInit.REVIVE_KEY.isDown()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> {
                        if (cap.isKnocked()) {
                            NetworkHandler.CHANNEL.sendToServer(new PacketSelfRevive(true));
                        } else {
                            NetworkHandler.CHANNEL.sendToServer(new PacketReviveAction(true));
                        }
                    });
                }
            }
            if (KeyInit.SURRENDER_KEY.isDown()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> {
                        if (cap.isKnocked()) {
                            NetworkHandler.CHANNEL.sendToServer(new PacketSurrenderAction(true));
                        }
                    });
                }
            } else {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> {
                        if (cap.isKnocked() && cap.getSurrenderProgress() > 0) {
                            NetworkHandler.CHANNEL.sendToServer(new PacketSurrenderAction(false));
                        }
                    });
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (!"hotbar".equals(event.getOverlay().id().getPath())) {
            return;
        }
        
        Minecraft mc = Minecraft.getInstance();
        float rawDelta = mc.getDeltaFrameTime() * 0.05f;
        final float delta = Math.min(rawDelta, 0.1f);

        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();
        GuiGraphics graphics = event.getGuiGraphics();

        if (mc.player != null) {
            mc.player.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> {
                if (cap.isKnocked()) {
                    int timer = cap.getDeathTimer();
                    int maxTimer = KnockdownConfig.BLEED_TIME_SECONDS.get() * 20;
                    if (maxTimer <= 0) maxTimer = 1;
                    float targetDeath = (float) timer / (float) maxTimer;
                    
                    if (animBleedProgress < 0.0f) {
                        animBleedProgress = targetDeath;
                    } else {
                        animBleedProgress = lerp(animBleedProgress, targetDeath, 10f, delta);
                    }

                    int barW = 182;
                    int barH = 8;
                    float radius = 4.0f;
                    int x = width / 2 - barW / 2;
                    int yBleed = height / 2 + 10;

                    fillRoundedRect(graphics, x - 1, yBleed - 1, barW + 2, barH + 2, radius + 1, KnockdownHudColors.OUTLINE_BLACK);
                    fillRoundedRect(graphics, x, yBleed, barW, barH, radius, KnockdownHudColors.BLEED_BG);
                    
                    if (animBleedProgress > 0.01f) {
                        double scale = mc.getWindow().getGuiScale();
                        int scissorX = (int) (x * scale);
                        int scissorY = (int) (mc.getWindow().getScreenHeight() - (yBleed + barH) * scale);
                        int scissorW = (int) (barW * animBleedProgress * scale);
                        int scissorH = (int) (barH * scale);
                        
                        RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);
                        fillRoundedRect(graphics, x, yBleed, barW, barH, radius, KnockdownHudColors.BLEED_FILL);
                        RenderSystem.disableScissor();
                    }

                    Component textDeath = Component.translatable("knockdown.hud.bleeding", timer / 20);
                    int textW = mc.font.width(textDeath);
                    graphics.drawString(mc.font, textDeath, x + (barW - textW) / 2, yBleed + barH + 4, KnockdownHudColors.BLEED_TEXT, true);


                    boolean hasAction = false;
                    float actionTargetProgress = 0.0f;
                    Component actionText = Component.empty();
                    int colorMain = 0;
                    int colorBg = 0;

                    if (cap.getReviveProgress() > 0) {
                        hasAction = true;
                        actionTargetProgress = cap.getReviveProgress() / 100.0f;
                        if (cap.isSelfReviving()) {
                            actionText = Component.translatable("knockdown.hud.injector_using");
                            colorMain = KnockdownHudColors.INJECTOR_MAIN;
                            colorBg = KnockdownHudColors.INJECTOR_BG;
                        } else {
                            actionText = Component.translatable("knockdown.hud.being_revived");
                            colorMain = KnockdownHudColors.REVIVE_MAIN;
                            colorBg = KnockdownHudColors.REVIVE_BG;
                        }
                    } else if (cap.getSurrenderProgress() > 0) {
                        hasAction = true;
                        actionTargetProgress = cap.getSurrenderProgress() / 100.0f;
                        actionText = Component.translatable("knockdown.action.surrender");
                        colorMain = KnockdownHudColors.SURRENDER_MAIN;
                        colorBg = KnockdownHudColors.SURRENDER_BG;
                    }

                    if (hasAction) {
                        animActionProgress = lerp(animActionProgress, actionTargetProgress, 10f, delta);
                        int yAction = height / 2 - 25;
                        
                        fillRoundedRect(graphics, x - 1, yAction - 1, barW + 2, barH + 2, radius + 1, KnockdownHudColors.OUTLINE_BLACK);
                        fillRoundedRect(graphics, x, yAction, barW, barH, radius, colorBg);
                        
                        if (animActionProgress > 0.01f) {
                            double scale = mc.getWindow().getGuiScale();
                            int scissorX = (int) (x * scale);
                            int scissorY = (int) (mc.getWindow().getScreenHeight() - (yAction + barH) * scale);
                            int scissorW = (int) (barW * animActionProgress * scale);
                            int scissorH = (int) (barH * scale);
                            
                            RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);
                            fillRoundedRect(graphics, x, yAction, barW, barH, radius, colorMain);
                            RenderSystem.disableScissor();
                        }
                        
                        int textActionW = mc.font.width(actionText);
                        graphics.drawString(mc.font, actionText, x + (barW - textActionW) / 2, yAction - 12, colorMain | 0xFF000000, true);
                    } else {
                        animActionProgress = 0.0f;

                        String surrenderKey = KeyInit.SURRENDER_KEY.getKey().getDisplayName().getString().toUpperCase();
                        Component surrenderPrompt = Component.translatable("knockdown.prompt.surrender", surrenderKey);
                        int sTextW = mc.font.width(surrenderPrompt);
                        graphics.drawString(mc.font, surrenderPrompt, x + (barW - sTextW) / 2, height / 2 - 25, KnockdownHudColors.PROMPT_GRAY, true);
                        

                        boolean hasInjector = false;
                        for (net.minecraft.world.item.ItemStack stack : mc.player.getInventory().items) {
                            if (!stack.isEmpty() && stack.getItem() == com.persiki84.knockdown.item.ModItems.INJECTOR.get()) {
                                hasInjector = true;
                                break;
                            }
                        }
                        if (!hasInjector && mc.player.getOffhandItem().getItem() == com.persiki84.knockdown.item.ModItems.INJECTOR.get()) {
                            hasInjector = true;
                        }
                        
                        if (hasInjector) {
                            String reviveKey = KeyInit.REVIVE_KEY.getKey().getDisplayName().getString().toUpperCase();
                            Component revivePrompt = Component.translatable("knockdown.prompt.revive_injector", reviveKey);
                            int rTextW = mc.font.width(revivePrompt);
                            graphics.drawString(mc.font, revivePrompt, x + (barW - rTextW) / 2, height / 2 - 37, KnockdownHudColors.INJECTOR_HINT, true);
                        }
                    }
                } else {
                    animBleedProgress = -1.0f;
                    animActionProgress = 0.0f;
                }
            });
        }

        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) mc.hitResult;
            if (entityHit.getEntity() instanceof Player target) {
                target.getCapability(KnockdownProvider.KNOCKDOWN_CAP).ifPresent(cap -> {
                    if (cap.isKnocked()) {
                        float progress = cap.getReviveProgress();
                        
                        int barW = 182;
                        int barH = 8;
                        float radius = 4.0f;
                        int x = width / 2 - barW / 2;
                        int yAction = height / 2 - 25;
                        
                        if (progress > 0 && !cap.isSelfReviving()) {
                            float actionTargetProgress = progress / 100.0f;
                            animTargetActionProgress = lerp(animTargetActionProgress, actionTargetProgress, 10f, delta);
                            
                            fillRoundedRect(graphics, x - 1, yAction - 1, barW + 2, barH + 2, radius + 1, KnockdownHudColors.OUTLINE_BLACK);
                            fillRoundedRect(graphics, x, yAction, barW, barH, radius, KnockdownHudColors.REVIVE_BG);
                            
                            if (animTargetActionProgress > 0.01f) {
                                double scale = mc.getWindow().getGuiScale();
                                int scissorX = (int) (x * scale);
                                int scissorY = (int) (mc.getWindow().getScreenHeight() - (yAction + barH) * scale);
                                int scissorW = (int) (barW * animTargetActionProgress * scale);
                                int scissorH = (int) (barH * scale);
                                
                                RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);
                                fillRoundedRect(graphics, x, yAction, barW, barH, radius, KnockdownHudColors.REVIVE_MAIN);
                                RenderSystem.disableScissor();
                            }
                            
                            Component text = Component.translatable("knockdown.hud.revive_progress", (int)progress);
                            int textW = mc.font.width(text);
                            graphics.drawString(mc.font, text, x + (barW - textW) / 2, yAction - 12, KnockdownHudColors.REVIVE_TEXT, true);
                        } else {
                            animTargetActionProgress = 0.0f;
                            if (progress == 0) {
                                String reviveKey = KeyInit.REVIVE_KEY.getKey().getDisplayName().getString().toUpperCase();
                                Component revivePrompt = Component.translatable("knockdown.prompt.revive", reviveKey);
                                int rTextW = mc.font.width(revivePrompt);
                                graphics.drawString(mc.font, revivePrompt, x + (barW - rTextW) / 2, yAction - 12, KnockdownHudColors.REVIVE_HINT, true);
                            }
                        }
                    } else {
                        animTargetActionProgress = 0.0f;
                    }
                });
            }
        }
    }

    private static void fillRoundedRect(GuiGraphics graphics, float x, float y, float width, float height, float radius, int color) {
        com.persiki84.minimap.client.MapRenderUtil.fillRoundedRect(graphics, x, y, width, height, radius, color);
    }
}
