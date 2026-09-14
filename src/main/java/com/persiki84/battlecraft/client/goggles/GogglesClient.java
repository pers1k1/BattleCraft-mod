package com.persiki84.battlecraft.client.goggles;

import com.mojang.blaze3d.platform.InputConstants;
import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.client.KeyInputHandler;
import com.persiki84.battlecraft.compat.goggles.Goggles;
import com.persiki84.battlecraft.compat.superb.SuperbThermal;
import com.persiki84.battlecraft.mixin.GameRendererAccessor;
import com.persiki84.battlecraft.mixin.PostChainAccessor;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class GogglesClient {
    public static final KeyMapping TOGGLE = new KeyMapping(
            "key." + BattleCraftMod.MOD_ID + ".goggles_toggle",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, KeyInputHandler.CATEGORY);

    public static final KeyMapping SWITCH_MODE = new KeyMapping(
            "key." + BattleCraftMod.MOD_ID + ".goggles_mode",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, KeyInputHandler.CATEGORY);

    public static final KeyMapping ZOOM = new KeyMapping(
            "key." + BattleCraftMod.MOD_ID + ".goggles_zoom",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, KeyInputHandler.CATEGORY);

    private static final float ZOOM_STEP = 0.15f;
    private static final float ZOOM_DEPTH = 0.67f;
    private static final float LOW_BATTERY = 0.15f;
    private static final int RETRY_TICKS = 40;

    private static boolean active;
    private static int mode = Goggles.MODE_NONE;
    private static float battery;
    private static boolean zoomModule;

    private static boolean underWater;
    private static ResourceLocation applied;
    private static PostChain owned;
    private static float zoomAmount;
    private static int retryDelay;

    private GogglesClient() {}

    public static void accept(boolean on, int visionMode, float charge, boolean zoom) {
        active = on;
        mode = visionMode;
        battery = charge;
        zoomModule = zoom;
    }

    public static boolean visorOn() {
        return active;
    }

    public static boolean ownsEffect() {
        PostChain effect = Minecraft.getInstance().gameRenderer.currentEffect();
        return effect != null && effect.getName().startsWith(Goggles.MOD_ID + ":");
    }

    public static int mode() {
        return mode;
    }

    public static float battery() {
        return battery;
    }

    public static boolean lowBattery() {
        return battery < LOW_BATTERY;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !GogglesForeign.available()) return;

        GogglesForeign.silenceOwnKeys();
        GogglesForeign.silenceOwnHud();
        pollKeys();

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            forget();
            return;
        }

        underWater = player.isUnderWater();
        driveZoom();
        driveShader(minecraft);
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (zoomAmount <= 0.0f || !visorOn()) return;
        event.setFOV(event.getFOV() * (1.0f - zoomAmount * ZOOM_DEPTH));
    }

    private static void pollKeys() {
        boolean toggled = false;
        boolean switched = false;
        while (TOGGLE.consumeClick()) toggled = true;
        while (SWITCH_MODE.consumeClick()) switched = true;

        if (toggled) GogglesForeign.requestToggle(false);
        if (switched) GogglesForeign.requestToggle(true);
    }

    private static void driveZoom() {
        boolean wanted = visorOn() && zoomModule && ZOOM.isDown();
        zoomAmount = wanted
                ? Math.min(1.0f, zoomAmount + ZOOM_STEP)
                : Math.max(0.0f, zoomAmount - ZOOM_STEP);
    }

    private static void driveShader(Minecraft minecraft) {
        if (SuperbThermal.imaging()) {
            forget();
            return;
        }

        ResourceLocation wanted = visorOn() ? Goggles.shaderOf(mode, underWater) : null;
        if (wanted == null) {
            release(minecraft);
            return;
        }

        if (!wanted.equals(applied) || minecraft.gameRenderer.currentEffect() != owned) {
            if (retryDelay > 0) {
                retryDelay--;
                return;
            }
            minecraft.gameRenderer.loadEffect(wanted);
            owned = minecraft.gameRenderer.currentEffect();
            applied = owned == null ? null : wanted;
            if (owned == null) retryDelay = RETRY_TICKS;
        }
        if (owned != null) ((GameRendererAccessor) minecraft.gameRenderer).battlecraft$effectActive(true);
    }

    private static void release(Minecraft minecraft) {
        if (applied == null) return;
        if (minecraft.gameRenderer.currentEffect() == owned) minecraft.gameRenderer.shutdownEffect();
        forget();
    }

    private static void forget() {
        applied = null;
        owned = null;
        zoomAmount = 0.0f;
        retryDelay = 0;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || owned == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.gameRenderer.currentEffect() != owned) return;

        feed(minecraft, minecraft.getFrameTime());
    }

    private static void feed(Minecraft minecraft, float partialTick) {
        float time = minecraft.level.getGameTime() + partialTick;
        float[] tint = GogglesForeign.colorFilter();
        float aspect = (float) minecraft.getWindow().getScreenWidth()
                / Math.max(1, minecraft.getWindow().getScreenHeight());

        for (PostPass pass : ((PostChainAccessor) owned).battlecraft$passes()) {
            set(pass.getEffect().getUniform("time"), time);
            set(pass.getEffect().getUniform("battery"), battery);
            set(pass.getEffect().getUniform("focus"), zoomAmount);
            set(pass.getEffect().getUniform("AspectRatio"), aspect);
            set(pass.getEffect().getUniform("FlashlightActive"), 0.0f);

            Uniform filter = pass.getEffect().getUniform("colorFilter");
            if (filter != null) filter.set(tint[0], tint[1], tint[2]);
        }
    }

    private static void set(Uniform uniform, float value) {
        if (uniform != null) uniform.set(value);
    }
}
