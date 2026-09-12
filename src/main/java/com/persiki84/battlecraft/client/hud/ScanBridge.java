package com.persiki84.battlecraft.client.hud;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.simple.SimpleChannel;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

// WHY: Explosion Overhaul рисует ход сканирования своим оверлеем поверх нашего интерфейса; его
// WHY: обработчик снимается с шины, а состояние читается полями - оно всё равно статическое
public final class ScanBridge {
    private static final String MOD_ID = "explosionoverhaul";
    private static final String PACKAGE = "com.vinlanx.explosionoverhaul.";
    private static final String[] OVERLAYS = {
            "ScanProgressHUD", "ScanPromptHUD", "ScanInfoHUD", "ScanLoadPromptHUD", "ScanLoadInfoHUD"
    };

    private static boolean probed;
    private static boolean available;

    private static SimpleChannel channel;
    private static Method loadPromptVisible;
    private static Method loadPromptHide;
    private static Method loadInfoHide;
    private static Method scanPromptVisible;
    private static Method scanPromptHide;
    private static Method scanInfoHide;
    private static Constructor<?> loadPacket;
    private static Constructor<?> scanPacket;
    private static Method send;

    private static Field showing;
    private static Field complete;
    private static Field total;
    private static Field scanned;
    private static Field lamps;
    private static Field dripstones;
    private static Field glass;
    private static Field rate;
    private static Field hideAt;

    private ScanBridge() {}

    public static boolean available() {
        return available;
    }

    public static void tick() {
        if (!probed) {
            probed = true;
            available = ModList.get().isLoaded(MOD_ID) && bind();
            if (available) silence();
        }
        if (!available) return;

        try {
            answerPrompts();
            expire();
        } catch (Throwable error) {
            available = false;
        }
    }

    // WHY: прятал законченный ход сам чужой оверлей, а он снят с шины: без этого правила
    // WHY: карточка «мир просканирован» висит до выхода из мира
    private static void expire() throws IllegalAccessException {
        if (hideAt == null || showing == null) return;

        long due = hideAt.getLong(null);
        if (due <= 0L || System.currentTimeMillis() <= due) return;

        showing.setBoolean(null, false);
    }

    private static void answerPrompts() throws Exception {
        if ((boolean) loadPromptVisible.invoke(null)) {
            send.invoke(channel, loadPacket.newInstance(true));
            loadPromptHide.invoke(null, false);
            loadInfoHide.invoke(null, false);
        }
        if ((boolean) scanPromptVisible.invoke(null)) {
            send.invoke(channel, scanPacket.newInstance(true));
            scanPromptHide.invoke(null, false);
            scanInfoHide.invoke(null, false);
        }
    }

    public static boolean showing() {
        return available && flag(showing);
    }

    public static boolean complete() {
        return flag(complete);
    }

    public static int total() {
        return count(total);
    }

    public static int scanned() {
        return count(scanned);
    }

    public static int lamps() {
        return count(lamps);
    }

    public static int dripstones() {
        return count(dripstones);
    }

    public static int glass() {
        return count(glass);
    }

    public static float share() {
        int all = total();
        if (all <= 0) return 0.0f;
        return Math.min(1.0f, scanned() / (float) all);
    }

    // WHY: остаток считается по скорости самого мода, а не по своему таймеру: его очередь
    // WHY: буксует на генерации местности, и равномерный отсчёт тут врёт
    public static int secondsLeft() {
        if (rate == null) return -1;

        try {
            double speed = rate.getDouble(null);
            int left = total() - scanned();
            if (speed <= 0.0 || left <= 0) return -1;
            return (int) Math.ceil(left / speed);
        } catch (Throwable error) {
            available = false;
            return -1;
        }
    }

    private static boolean flag(Field field) {
        if (field == null) return false;

        try {
            return field.getBoolean(null);
        } catch (Throwable error) {
            available = false;
            return false;
        }
    }

    private static int count(Field field) {
        if (field == null) return 0;

        try {
            return field.getInt(null);
        } catch (Throwable error) {
            available = false;
            return 0;
        }
    }

    private static void silence() {
        for (String name : OVERLAYS) {
            try {
                MinecraftForge.EVENT_BUS.unregister(Class.forName(PACKAGE + name));
            } catch (Throwable ignored) {
                // WHY: пропажа одного оверлея чужого мода не имеет права гасить весь мост
            }
        }
    }

    private static boolean bind() {
        return bindPrompts() && bindProgress();
    }

    private static boolean bindPrompts() {
        try {
            Class<?> handler = Class.forName(PACKAGE + "PacketHandler");
            Class<?> loadPrompt = Class.forName(PACKAGE + "ScanLoadPromptHUD");
            Class<?> loadInfo = Class.forName(PACKAGE + "ScanLoadInfoHUD");
            Class<?> scanPrompt = Class.forName(PACKAGE + "ScanPromptHUD");
            Class<?> scanInfo = Class.forName(PACKAGE + "ScanInfoHUD");
            Class<?> loadControl = Class.forName(PACKAGE + "ScanLoadControlPacket");
            Class<?> scanControl = Class.forName(PACKAGE + "ScanControlPacket");

            channel = (SimpleChannel) handler.getField("INSTANCE").get(null);
            loadPromptVisible = loadPrompt.getMethod("isVisible");
            loadPromptHide = loadPrompt.getMethod("setVisible", boolean.class);
            loadInfoHide = loadInfo.getMethod("setVisible", boolean.class);
            scanPromptVisible = scanPrompt.getMethod("isVisible");
            scanPromptHide = scanPrompt.getMethod("setVisible", boolean.class);
            scanInfoHide = scanInfo.getMethod("setVisible", boolean.class);
            loadPacket = loadControl.getConstructor(boolean.class);
            scanPacket = scanControl.getConstructor(boolean.class);
            send = SimpleChannel.class.getMethod("sendToServer", Object.class);
            return channel != null;
        } catch (Throwable error) {
            return false;
        }
    }

    private static boolean bindProgress() {
        try {
            Class<?> progress = Class.forName(PACKAGE + "ScanProgressHUD");
            showing = reach(progress, "isVisible");
            complete = reach(progress, "isComplete");
            total = reach(progress, "totalChunks");
            scanned = reach(progress, "scannedChunks");
            lamps = reach(progress, "lampsFound");
            dripstones = reach(progress, "dripstonesFound");
            glass = reach(progress, "glassBlocksFound");
            rate = reach(progress, "chunksPerSecond");
            hideAt = reach(progress, "hideTime");
            return showing != null && total != null && scanned != null;
        } catch (Throwable error) {
            return false;
        }
    }

    private static Field reach(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable error) {
            return null;
        }
    }
}
