package com.persiki84.battlecraft.client.hud;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;

final class GuiBridge {
    private static final String TITLE = "f_93001_";
    private static final String SUBTITLE = "f_93002_";
    private static final String TITLE_TIME = "f_93000_";
    private static final String FADE_IN = "f_92970_";
    private static final String STAY = "f_92971_";
    private static final String FADE_OUT = "f_92972_";
    private static final String ACTION_TEXT = "f_92990_";
    private static final String ACTION_TIME = "f_92991_";

    private static final float ACTION_FADE_TICKS = 20.0f;
    private static final float VISIBLE_FLOOR = 8.0f / 255.0f;

    private static boolean probed;
    private static boolean available;

    private static Field title;
    private static Field subtitle;
    private static Field titleTime;
    private static Field fadeIn;
    private static Field stay;
    private static Field fadeOut;
    private static Field actionText;
    private static Field actionTime;

    private GuiBridge() {}

    record Notice(Component text, Component detail, float alpha) {}

    static boolean available() {
        if (!probed) {
            probed = true;
            available = bind();
        }
        return available;
    }

    static Notice title(Gui gui, float partialTick) {
        if (!available()) return null;
        try {
            Component text = (Component) title.get(gui);
            int age = titleTime.getInt(gui);
            if (text == null || age <= 0) return null;

            float alpha = titleAlpha(gui, age - partialTick, age);
            if (alpha <= VISIBLE_FLOOR) return null;
            return new Notice(text, (Component) subtitle.get(gui), alpha);
        } catch (Throwable error) {
            available = false;
            return null;
        }
    }

    static Notice action(Gui gui, float partialTick) {
        if (!available()) return null;
        try {
            Component text = (Component) actionText.get(gui);
            int age = actionTime.getInt(gui);
            if (text == null || age <= 0) return null;

            float alpha = Math.min(1.0f, (age - partialTick) / ACTION_FADE_TICKS);
            if (alpha <= VISIBLE_FLOOR) return null;
            return new Notice(text, null, alpha);
        } catch (Throwable error) {
            available = false;
            return null;
        }
    }

    private static float titleAlpha(Gui gui, float age, int remaining) throws IllegalAccessException {
        int in = fadeIn.getInt(gui);
        int hold = stay.getInt(gui);
        int out = fadeOut.getInt(gui);

        float opacity = 1.0f;
        if (remaining > out + hold) {
            opacity = (in + hold + out - age) / Math.max(1.0f, in);
        }
        if (remaining <= out) {
            opacity = age / Math.max(1.0f, out);
        }
        return Math.max(0.0f, Math.min(1.0f, opacity));
    }

    private static boolean bind() {
        try {
            title = ObfuscationReflectionHelper.findField(Gui.class, TITLE);
            subtitle = ObfuscationReflectionHelper.findField(Gui.class, SUBTITLE);
            titleTime = ObfuscationReflectionHelper.findField(Gui.class, TITLE_TIME);
            fadeIn = ObfuscationReflectionHelper.findField(Gui.class, FADE_IN);
            stay = ObfuscationReflectionHelper.findField(Gui.class, STAY);
            fadeOut = ObfuscationReflectionHelper.findField(Gui.class, FADE_OUT);
            actionText = ObfuscationReflectionHelper.findField(Gui.class, ACTION_TEXT);
            actionTime = ObfuscationReflectionHelper.findField(Gui.class, ACTION_TIME);
            return true;
        } catch (Throwable error) {
            return false;
        }
    }
}
