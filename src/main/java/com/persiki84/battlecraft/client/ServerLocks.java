package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.rules.GameRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;

public final class ServerLocks {
    private static final String REASON = "battlecraft.locked.server";
    private static final double OFF = 0.0;

    private static boolean glintHidden;
    private static boolean glintClamped;
    private static boolean snapshotRead;
    private static double keptGlintSpeed;
    private static double keptGlintStrength;

    private ServerLocks() {}

    // WHY: рендер спрашивает свечение на каждый предмет каждого кадра, поэтому решение считается
    // WHY: раз в тик и лежит готовым полем, а не собирается из правил и настроек внутри миксина
    public static void refresh() {
        recoverSnapshot();
        glintHidden = glintLocked();
        if (glintHidden) {
            clampGlint();
        } else {
            releaseGlint();
        }
    }

    public static void release() {
        glintHidden = false;
        releaseGlint();
    }

    public static boolean glintHidden() {
        return glintHidden;
    }

    // WHY: правило пришло от сервера, к которому мы подключены, и одиночный мир тут не исключение:
    // WHY: его правила игрок держит сам в /bc, а свечение обязано слушаться их так же, как на сервере
    public static boolean glintLocked() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && ClientGameRules.allows(GameRule.NO_ENCHANT_GLINT);
    }

    public static boolean glintLoose() {
        if (!glintLocked()) return false;

        Options options = Minecraft.getInstance().options;
        return options.glintSpeed().get() != OFF || options.glintStrength().get() != OFF;
    }

    public static boolean packsLocked() {
        return ServerRules.restricted() && ClientGameRules.allows(GameRule.BLOCK_RESOURCE_PACKS);
    }

    public static Component reason() {
        return Component.translatable(REASON);
    }

    // WHY: журнал прошлого сеанса читается один раз при первом тике: игру закрыли прямо из мира,
    // WHY: ваниль сохранила зажатые нули, и вернуть игроку его значения больше неоткуда
    private static void recoverSnapshot() {
        if (snapshotRead) return;

        snapshotRead = true;
        double[] kept = LockedOptions.read();
        if (kept == null) return;

        keptGlintSpeed = kept[0];
        keptGlintStrength = kept[1];
        glintClamped = true;
    }

    // WHY: значения не сохраняются в options.txt: правило снимут, и файл обязан вернуть игроку его
    // WHY: заметность и скорость, а не нули, оставленные сервером после последней сессии
    private static void clampGlint() {
        Options options = Minecraft.getInstance().options;
        if (!glintClamped) {
            keptGlintSpeed = options.glintSpeed().get();
            keptGlintStrength = options.glintStrength().get();
            glintClamped = true;
            LockedOptions.hold(keptGlintSpeed, keptGlintStrength);
        }
        if (options.glintSpeed().get() != OFF) options.glintSpeed().set(OFF);
        if (options.glintStrength().get() != OFF) options.glintStrength().set(OFF);
    }

    private static void releaseGlint() {
        if (!glintClamped) return;

        glintClamped = false;
        Options options = Minecraft.getInstance().options;
        options.glintSpeed().set(keptGlintSpeed);
        options.glintStrength().set(keptGlintStrength);
        options.save();
        LockedOptions.drop();
    }
}
