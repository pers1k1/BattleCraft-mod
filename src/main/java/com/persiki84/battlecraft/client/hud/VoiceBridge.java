package com.persiki84.battlecraft.client.hud;

import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class VoiceBridge {
    public enum State { NONE, DISCONNECTED, DISABLED, NO_MICROPHONE, MUTED, TALKING }

    private static final long DEVICE_SCAN_INTERVAL_MS = 3000L;
    private static final String PUSH_TO_TALK = "PTT";

    private static boolean probed;
    private static boolean available;

    private static Method stateManager;
    private static Method client;
    private static Method isDisabled;
    private static Method isDisconnected;
    private static Method isMuted;
    private static Method micThread;
    private static Method isTalking;
    private static Method deviceNames;
    private static Method configGet;
    private static Field clientConfig;
    private static Field activationType;

    private static long devicesCheckedAt;
    private static boolean devicesPresent = true;
    private static State polled = State.NONE;

    private VoiceBridge() {}

    // WHY: значок читался семью рефлективными вызовами на каждый кадр, а меняется он от силы
    // WHY: двадцать раз в секунду: опрос идёт по тику, рендер берёт готовое
    public static void poll() {
        polled = state();
    }

    public static State current() {
        return polled;
    }

    public static void clear() {
        polled = State.NONE;
    }

    public static State state() {
        if (!probed) {
            probed = true;
            available = ModList.get().isLoaded("voicechat") && bind();
        }
        if (!available) return State.NONE;

        try {
            return read();
        } catch (Throwable error) {
            available = false;
            return State.NONE;
        }
    }

    private static State read() throws Exception {
        Object manager = stateManager.invoke(null);
        if (manager == null) return State.NONE;
        if ((boolean) isDisconnected.invoke(manager)) return State.DISCONNECTED;
        if ((boolean) isDisabled.invoke(manager)) return State.DISABLED;

        Object voice = client.invoke(null);
        if (voice == null) return State.NONE;

        Object mic = micThread.invoke(voice);
        if (mic == null || !microphonePresent()) return State.NO_MICROPHONE;
        if ((boolean) isMuted.invoke(manager) && !pushToTalk()) return State.MUTED;
        return (boolean) isTalking.invoke(mic) ? State.TALKING : State.NONE;
    }

    private static boolean pushToTalk() throws Exception {
        Object config = clientConfig.get(null);
        if (config == null) return false;

        Object mode = configGet.invoke(activationType.get(config));
        return mode != null && PUSH_TO_TALK.equals(mode.toString());
    }

    private static boolean microphonePresent() throws Exception {
        long now = System.currentTimeMillis();
        if (now - devicesCheckedAt < DEVICE_SCAN_INTERVAL_MS) return devicesPresent;

        devicesCheckedAt = now;
        Object names = deviceNames.invoke(null);
        devicesPresent = !(names instanceof List<?> list) || !list.isEmpty();
        return devicesPresent;
    }

    private static boolean bind() {
        try {
            Class<?> manager = Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager");
            Class<?> states = Class.forName("de.maxhenkel.voicechat.voice.client.ClientPlayerStateManager");
            Class<?> voice = Class.forName("de.maxhenkel.voicechat.voice.client.ClientVoicechat");
            Class<?> mic = Class.forName("de.maxhenkel.voicechat.voice.client.MicThread");
            Class<?> microphones = Class.forName("de.maxhenkel.voicechat.voice.client.microphone.MicrophoneManager");
            Class<?> clientMod = Class.forName("de.maxhenkel.voicechat.VoicechatClient");
            Class<?> config = Class.forName("de.maxhenkel.voicechat.config.ClientConfig");
            Class<?> entry = Class.forName("de.maxhenkel.voicechat.configbuilder.entry.ConfigEntry");

            stateManager = manager.getMethod("getPlayerStateManager");
            client = manager.getMethod("getClient");
            isDisabled = states.getMethod("isDisabled");
            isDisconnected = states.getMethod("isDisconnected");
            isMuted = states.getMethod("isMuted");
            micThread = voice.getMethod("getMicThread");
            isTalking = mic.getMethod("isTalking");
            deviceNames = microphones.getMethod("deviceNames");
            clientConfig = clientMod.getField("CLIENT_CONFIG");
            activationType = config.getField("microphoneActivationType");
            configGet = entry.getMethod("get");
            return true;
        } catch (Throwable error) {
            return false;
        }
    }
}
