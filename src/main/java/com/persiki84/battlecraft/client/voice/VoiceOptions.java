package com.persiki84.battlecraft.client.voice;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class VoiceOptions {
    private static final String VOICE_ACTIVATION = "VOICE";
    private static final String PUSH_TO_TALK = "PTT";
    private static final String DEFAULT_DEVICE = "";

    private static boolean probed;
    private static boolean bound;

    private static Object config;
    private static Method entryGet;
    private static Method entrySet;
    private static Method entrySave;

    private static Field speakerEntry;
    private static Field microphoneEntry;
    private static Field activationEntry;
    private static Field onboardingEntry;
    private static Field mutedEntry;
    private static Field disabledEntry;

    private static Method allSpeakers;
    private static Method microphoneNames;
    private static Method cleanDeviceName;
    private static Method runningClient;
    private static Method reloadAudio;
    private static Field pushToTalkKey;

    private VoiceOptions() {}

    public static boolean available() {
        if (!probed) {
            probed = true;
            bound = ModList.get().isLoaded("voicechat") && bind();
        }
        return bound;
    }

    public static List<String> speakers() {
        return devices(allSpeakers);
    }

    public static List<String> microphones() {
        return devices(microphoneNames);
    }

    public static Component deviceName(String device, Component fallback) {
        if (device == null || device.isEmpty()) return fallback;

        String cleaned = device;
        if (cleanDeviceName != null) {
            try {
                Object shortened = cleanDeviceName.invoke(null, device);
                if (shortened instanceof String text && !text.isEmpty()) cleaned = text;
            } catch (Throwable error) {
                cleaned = device;
            }
        }
        return Component.literal(cleaned);
    }

    public static String speaker() {
        return text(speakerEntry);
    }

    public static void speaker(String device) {
        write(speakerEntry, device == null ? DEFAULT_DEVICE : device);
        reloadAudio();
    }

    public static String microphone() {
        return text(microphoneEntry);
    }

    public static void microphone(String device) {
        write(microphoneEntry, device == null ? DEFAULT_DEVICE : device);
        reloadAudio();
    }

    public static boolean pushToTalk() {
        Object mode = read(activationEntry);
        return mode != null && PUSH_TO_TALK.equals(mode.toString());
    }

    // WHY: тип активации хранится перечислением чужого мода, поэтому значение берётся из
    // WHY: уже лежащего в конфиге объекта его же классом, а не собирается по имени
    public static void pushToTalk(boolean value) {
        Object current = read(activationEntry);
        if (current == null) return;

        Object wanted = constant(current.getClass(), value ? PUSH_TO_TALK : VOICE_ACTIVATION);
        if (wanted == null) return;

        write(activationEntry, wanted);
        if (!value) write(mutedEntry, Boolean.FALSE);
    }

    public static KeyMapping talkKey() {
        if (pushToTalkKey == null) return null;

        try {
            return (KeyMapping) pushToTalkKey.get(null);
        } catch (Throwable error) {
            return null;
        }
    }

    public static Component talkKeyName(Component unbound) {
        KeyMapping mapping = talkKey();
        if (mapping == null || mapping.isUnbound()) return unbound;

        return mapping.getTranslatedKeyMessage();
    }

    public static void talkKey(InputConstants.Key key) {
        KeyMapping mapping = talkKey();
        if (mapping == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        minecraft.options.setKey(mapping, key);
        KeyMapping.resetMapping();
    }

    // WHY: свой мастер уже спросил устройства и способ ввода, поэтому чужой мастер объявляется
    // WHY: пройденным: иначе Simple Voice Chat покажет свои экраны поверх наших при первом входе
    public static void settled() {
        write(onboardingEntry, Boolean.TRUE);
        write(disabledEntry, Boolean.FALSE);
    }

    private static List<String> devices(Method source) {
        List<String> found = new ArrayList<>();
        if (source == null) return found;

        try {
            Object listed = source.invoke(null);
            if (listed instanceof List<?> list) {
                for (Object device : list) {
                    if (device instanceof String name && !name.isEmpty()) found.add(name);
                }
            }
        } catch (Throwable error) {
            return found;
        }
        return found;
    }

    private static Object constant(Class<?> type, String name) {
        Object[] known = type.getEnumConstants();
        if (known == null) return null;

        for (Object value : known) {
            if (name.equals(value.toString())) return value;
        }
        return null;
    }

    private static String text(Field entry) {
        Object value = read(entry);
        return value instanceof String stored ? stored : DEFAULT_DEVICE;
    }

    private static Object read(Field entry) {
        if (!available() || entry == null) return null;

        try {
            return entryGet.invoke(entry.get(config));
        } catch (Throwable error) {
            return null;
        }
    }

    private static void write(Field entry, Object value) {
        if (!available() || entry == null) return;

        try {
            entrySave.invoke(entrySet.invoke(entry.get(config), value));
        } catch (Throwable error) {
            bound = false;
        }
    }

    private static void reloadAudio() {
        if (runningClient == null || reloadAudio == null) return;

        try {
            Object client = runningClient.invoke(null);
            if (client != null) reloadAudio.invoke(client);
        } catch (Throwable error) {
            bound = false;
        }
    }

    private static boolean bind() {
        try {
            Class<?> clientMod = Class.forName("de.maxhenkel.voicechat.VoicechatClient");
            Class<?> settings = Class.forName("de.maxhenkel.voicechat.config.ClientConfig");
            Class<?> entry = Class.forName("de.maxhenkel.voicechat.configbuilder.entry.ConfigEntry");

            config = clientMod.getField("CLIENT_CONFIG").get(null);
            entryGet = entry.getMethod("get");
            entrySet = entry.getMethod("set", Object.class);
            entrySave = entry.getMethod("save");

            speakerEntry = settings.getField("speaker");
            microphoneEntry = settings.getField("microphone");
            activationEntry = settings.getField("microphoneActivationType");
            onboardingEntry = settings.getField("onboardingFinished");
            mutedEntry = settings.getField("muted");
            disabledEntry = settings.getField("disabled");
        } catch (Throwable error) {
            return false;
        }

        bindDevices();
        bindClient();
        bindKey();
        return config != null;
    }

    private static void bindDevices() {
        try {
            Class<?> speakers = Class.forName("de.maxhenkel.voicechat.voice.client.SoundManager");
            allSpeakers = speakers.getMethod("getAllSpeakers");
            cleanDeviceName = speakers.getMethod("cleanDeviceName", String.class);
        } catch (Throwable error) {
            allSpeakers = null;
        }

        try {
            microphoneNames = Class.forName("de.maxhenkel.voicechat.voice.client.microphone.MicrophoneManager")
                    .getMethod("deviceNames");
        } catch (Throwable error) {
            microphoneNames = null;
        }
    }

    private static void bindClient() {
        try {
            runningClient = Class.forName("de.maxhenkel.voicechat.voice.client.ClientManager")
                    .getMethod("getClient");
            reloadAudio = Class.forName("de.maxhenkel.voicechat.voice.client.ClientVoicechat")
                    .getMethod("reloadAudio");
        } catch (Throwable error) {
            runningClient = null;
        }
    }

    private static void bindKey() {
        try {
            pushToTalkKey = Class.forName("de.maxhenkel.voicechat.voice.client.KeyEvents")
                    .getField("KEY_PTT");
        } catch (Throwable error) {
            pushToTalkKey = null;
        }
    }
}
