package com.persiki84.battlecraft.client.hud;

import com.mojang.logging.LogUtils;
import com.persiki84.shared.client.ui.UiVital;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class VisualsBridge {
    private static final ResourceLocation BEAT = new ResourceLocation("enhancedvisuals", "heartbeatout");
    private static final ResourceLocation PREBEAT = new ResourceLocation("enhancedvisuals", "heartbeatin");
    private static final int HOLD_TICKS = 40;
    private static final int REFRACTORY_TICKS = 3;
    private static final float PREBEAT_TICKS = 5.0f;
    private static final float PREBEAT_OF_PERIOD = 0.32f;
    private static final float PREBEAT_VOLUME = 0.85f;
    private static final float PREBEAT_SHARE = 0.45f;
    private static final float WOUND_CEILING = 0.7f;
    private static final float FATIGUE_FLOOR = 0.5f;
    private static final float FATIGUE_CEILING = 0.35f;
    private static final float FATIGUE_VOLUME = 0.6f;
    private static final float RUSH_VOLUME = 0.85f;
    private static final float RUSH_FLOOR = 0.02f;
    private static final float FALLBACK_THRESHOLD = 0.3f;

    private static boolean probed;
    private static boolean ready;

    private static Object heartbeat;
    private static Field enabled;
    private static Field bufferTicks;
    private static Field usePercentage;
    private static Field maxHealth;
    private static Field maxHealthPercentage;
    private static Field overlayType;
    private static Field blurType;
    private static Field overlayIntensity;
    private static Field overlayDuration;
    private static Field blurIntensity;
    private static Field blurDuration;
    private static Field volume;
    private static Method addVisual;
    private static Method playSound;
    private static Constructor<?> curve;

    private static long pendingBeat = Long.MIN_VALUE;
    private static long primedBeat = Long.MIN_VALUE;
    private static int sinceBeat = REFRACTORY_TICKS;
    private static boolean announced;

    private VisualsBridge() {}

    public static void tick(Player player) {
        if (!bind()) return;
        try {
            if (!enabled.getBoolean(heartbeat) || !player.isAlive() || player.isSpectator()) {
                forget();
                return;
            }
            bufferTicks.setInt(heartbeat, HOLD_TICKS);
            sinceBeat++;

            long beat = UiVital.beatIndex();
            if (struck(beat)) {
                strike(player);
                return;
            }
            prime(player, beat);
        } catch (Throwable error) {
            ready = false;
            LogUtils.getLogger().warn("[battlecraft] heartbeat bridge off: {}", String.valueOf(error));
        }
    }

    private static void forget() {
        pendingBeat = Long.MIN_VALUE;
        primedBeat = Long.MIN_VALUE;
        sinceBeat = REFRACTORY_TICKS;
    }

    private static boolean struck(long beat) {
        boolean passed = pendingBeat != Long.MIN_VALUE && beat > pendingBeat;
        pendingBeat = beat;
        return passed && sinceBeat >= REFRACTORY_TICKS;
    }

    private static void strike(Player player) throws ReflectiveOperationException {
        float wound = woundDrive(player);
        float fatigue = fatigueDrive();
        float rush = rushDrive();
        if (wound <= 0.0f && fatigue <= 0.0f && rush <= 0.0f) return;

        sinceBeat = 0;
        announce();
        playSound.invoke(heartbeat, BEAT, volume.getFloat(heartbeat) * loudness(wound, fatigue, rush));
        if (wound <= 0.0f) return;

        visual(overlayType, overlayIntensity, overlayDuration, wound);
        visual(blurType, blurIntensity, blurDuration, wound);
    }

    private static void prime(Player player, long beat) throws ReflectiveOperationException {
        if (primedBeat == beat || UiVital.secondsToBeat() * 20.0f > leadTicks()) return;

        float wound = woundDrive(player);
        float fatigue = fatigueDrive();
        float rush = rushDrive();
        if (wound <= 0.0f && fatigue <= 0.0f && rush <= 0.0f) return;

        primedBeat = beat;
        playSound.invoke(heartbeat, PREBEAT,
                volume.getFloat(heartbeat) * loudness(wound, fatigue, rush) * PREBEAT_VOLUME);
        if (wound <= 0.0f) return;

        visual(blurType, blurIntensity, blurDuration, wound * PREBEAT_SHARE);
    }

    private static float leadTicks() {
        float period = 20.0f / Math.max(0.05f, UiVital.beatsPerSecond());
        return Math.min(PREBEAT_TICKS, period * PREBEAT_OF_PERIOD);
    }

    private static float loudness(float wound, float fatigue, float rush) {
        if (wound > 0.0f) return 1.0f;
        return Math.max(FATIGUE_VOLUME * fatigue / FATIGUE_CEILING, RUSH_VOLUME * rush);
    }

    private static void announce() {
        if (announced) return;
        announced = true;
        LogUtils.getLogger().info("[battlecraft] heartbeat bridge live at {} bpm",
                Math.round(UiVital.beatsPerSecond() * 60.0f));
    }

    public static boolean wounded(Player player) {
        if (player == null) return false;
        if (!bind()) return player.getHealth() <= player.getMaxHealth() * FALLBACK_THRESHOLD;

        try {
            return woundDrive(player) > 0.0f;
        } catch (ReflectiveOperationException error) {
            return player.getHealth() <= player.getMaxHealth() * FALLBACK_THRESHOLD;
        }
    }

    private static float woundDrive(Player player) throws ReflectiveOperationException {
        float max = Math.max(1.0f, player.getMaxHealth());
        float health = player.getHealth();
        if (usePercentage.getBoolean(heartbeat)) {
            float threshold = maxHealthPercentage.getFloat(heartbeat);
            if (health / max >= threshold) return 0.0f;
            return Math.min(WOUND_CEILING, (threshold - health / max) * 2.0f);
        }
        float threshold = maxHealth.getInt(heartbeat);
        if (health >= threshold) return 0.0f;
        return Math.min(WOUND_CEILING, (threshold - health) / max * 2.0f);
    }

    private static float fatigueDrive() {
        if (!HudConfig.heartbeatFatigue()) return 0.0f;
        float fatigue = UiVital.exertion();
        if (fatigue <= FATIGUE_FLOOR) return 0.0f;
        return (fatigue - FATIGUE_FLOOR) / (1.0f - FATIGUE_FLOOR) * FATIGUE_CEILING;
    }

    // WHY: прилив адреналина гонит только звук — потемнение и блюр остаются за приводом ранения
    private static float rushDrive() {
        float drive = UiVital.rush();
        return drive < RUSH_FLOOR ? 0.0f : drive;
    }

    private static void visual(Field type, Field intensity, Field duration, float drive)
            throws ReflectiveOperationException {
        Object fadeOut = curve.newInstance(0.0, drive * intensity.getFloat(heartbeat),
                (double) duration.getInt(heartbeat), 0.0);
        addVisual.invoke(null, type.get(heartbeat), heartbeat, fadeOut);
    }

    private static boolean bind() {
        if (!probed) {
            probed = true;
            ready = ModList.get().isLoaded("enhancedvisuals") && attachHandler() && attachVisuals();
            if (!ready) {
                LogUtils.getLogger().warn("[battlecraft] heartbeat bridge not bound, EnhancedVisuals keeps its own beat");
            }
        }
        return ready;
    }

    private static boolean attachHandler() {
        try {
            Class<?> handlers = Class.forName("team.creative.enhancedvisuals.common.handler.VisualHandlers");
            heartbeat = handlers.getField("HEARTBEAT").get(null);
            if (heartbeat == null) return false;

            Class<?> handler = heartbeat.getClass();
            enabled = handler.getField("enabled");
            bufferTicks = handler.getField("effectBufferTicks");
            usePercentage = handler.getField("useHealthPercentage");
            maxHealth = handler.getField("maxHealth");
            maxHealthPercentage = handler.getField("maxHealthPercentage");
            volume = handler.getField("heartbeatVolume");
            overlayType = handler.getField("lowhealth");
            blurType = handler.getField("blur");
            overlayIntensity = handler.getField("heartbeatOverlayIntensity");
            overlayDuration = handler.getField("heartbeatOverlayDuration");
            blurIntensity = handler.getField("heartbeatBlurIntensity");
            blurDuration = handler.getField("heartbeatBlurDuration");
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    private static boolean attachVisuals() {
        try {
            Class<?> manager = Class.forName("team.creative.enhancedvisuals.client.VisualManager");
            Class<?> visualType = Class.forName("team.creative.enhancedvisuals.api.type.VisualType");
            Class<?> visualHandler = Class.forName("team.creative.enhancedvisuals.api.VisualHandler");
            Class<?> curveApi = Class.forName("team.creative.creativecore.common.config.premade.curve.Curve");
            Class<?> decimalCurve = Class.forName("team.creative.creativecore.common.config.premade.curve.DecimalCurve");

            addVisual = manager.getMethod("addVisualFadeOut", visualType, visualHandler, curveApi);
            playSound = visualHandler.getMethod("playSound", ResourceLocation.class, float.class);
            curve = decimalCurve.getConstructor(double.class, double.class, double.class, double.class);
            return true;
        } catch (Throwable error) {
            return false;
        }
    }
}
