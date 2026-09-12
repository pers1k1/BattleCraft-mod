package com.persiki84.shared.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.function.Supplier;

public final class UiSound {
    private static final long[] GATES = new long[UiCue.Gate.values().length];

    // WHY: общий уровень интерфейсных сигналов, чтобы не разъезжался баланс между схемами:
    // WHY: тише делается здесь, а не правкой двух десятков усилений по отдельности
    private static final float LEVEL = 0.6f;
    private static final float MUTE = 0.004f;

    private static Supplier<Float> volume = () -> 1.0f;
    private static Supplier<Boolean> hoverEnabled = () -> true;
    private static Supplier<UiSoundScheme> scheme = () -> UiSoundScheme.FALLBACK;

    private UiSound() {}

    public static void source(Supplier<Float> value) {
        volume = value;
    }

    public static void hoverSource(Supplier<Boolean> value) {
        hoverEnabled = value;
    }

    public static void schemeSource(Supplier<UiSoundScheme> value) {
        if (value != null) scheme = value;
    }

    // WHY: чужие моды спрашивают, звучит ли наш сигнал на действие, ещё до самого действия,
    // WHY: поэтому ответ считается по настройкам, а не по факту недавнего проигрывания
    public static boolean audible(UiCue cue) {
        if (cue == UiCue.HOVER && !hoverEnabled.get()) return false;
        return volume.get() > MUTE;
    }

    public static void hover() {
        if (!hoverEnabled.get()) return;
        cue(UiCue.HOVER);
    }

    public static void press() {
        cue(UiCue.PRESS);
    }

    public static void toggle(boolean on) {
        cue(on ? UiCue.TOGGLE_ON : UiCue.TOGGLE_OFF);
    }

    public static void confirm() {
        cue(UiCue.CONFIRM);
    }

    public static void deny() {
        cue(UiCue.DENY);
    }

    public static void slot() {
        cue(UiCue.SLOT);
    }

    public static void toast() {
        cue(UiCue.NOTIFY);
    }

    public static void alert() {
        cue(UiCue.ALERT);
    }

    public static void slide(float value) {
        if (!ready(UiCue.SLIDE)) return;
        if (sampled(UiCue.SLIDE)) return;
        play(SoundEvents.NOTE_BLOCK_HAT.value(), 0.08f, 1.6f + UiAnim.clamp01(value) * 0.5f);
    }

    public static void screenOpen() {
        cue(UiCue.SCREEN_OPEN);
    }

    public static void screenClose() {
        cue(UiCue.SCREEN_CLOSE);
    }

    public static void chip(boolean gained) {
        cue(gained ? UiCue.CHIP_UP : UiCue.CHIP_DOWN);
    }

    // WHY: образец звучит от наведения и обязан быть единственным: свой сигнал кнопки заглушается
    public static void preview(UiSoundScheme previewed, UiCue cue) {
        if (previewed == null || !ready(cue)) return;

        hush(UiCue.Gate.HOVER);
        if (!previewed.sampled()) {
            notes(cue);
            return;
        }
        play(previewed.event(cue), cue.gain(), cue.pitch());
    }

    private static void hush(UiCue.Gate gate) {
        GATES[gate.ordinal()] = System.currentTimeMillis();
    }

    private static void cue(UiCue cue) {
        if (!ready(cue)) return;
        if (sampled(cue)) return;
        notes(cue);
    }

    private static boolean sampled(UiCue cue) {
        UiSoundScheme chosen = scheme.get();
        if (chosen == null || !chosen.sampled()) return false;

        SoundEvent event = chosen.event(cue);
        if (event == null) return false;

        play(event, cue.gain(), cue.pitch());
        return true;
    }

    // WHY: схема собрана из нотных блоков. Ванильный ui.button.click отсюда убран: движок его
    // WHY: принимает и не воспроизводит, проверено логом на каждом нажатии
    private static void notes(UiCue cue) {
        switch (cue) {
            case HOVER -> play(SoundEvents.NOTE_BLOCK_HAT.value(), 0.12f, 0.9f);
            case PRESS, TOGGLE_ON, TOGGLE_OFF, CONFIRM -> {
                play(SoundEvents.NOTE_BLOCK_HAT.value(), 0.55f, 1.4f);
                play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.20f, 1.5f);
            }
            case DENY -> play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.22f, 0.72f);
            case SLOT -> play(SoundEvents.NOTE_BLOCK_HAT.value(), 0.16f, 1.85f);
            case NOTIFY -> play(SoundEvents.UI_TOAST_IN, 0.55f, 1.25f);
            case ALERT -> {
                play(SoundEvents.NOTE_BLOCK_BELL.value(), 0.30f, 0.72f);
                play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.16f, 0.62f);
            }
            case SLIDE -> play(SoundEvents.NOTE_BLOCK_HAT.value(), 0.08f, 1.6f);
            case SCREEN_OPEN -> play(SoundEvents.NOTE_BLOCK_HAT.value(), 0.16f, 1.6f);
            case SCREEN_CLOSE -> play(SoundEvents.NOTE_BLOCK_HAT.value(), 0.14f, 1.15f);
            case CHIP_UP -> play(SoundEvents.NOTE_BLOCK_HAT.value(), 0.10f, 1.7f);
            case CHIP_DOWN -> play(SoundEvents.NOTE_BLOCK_HAT.value(), 0.10f, 1.1f);
        }
    }

    private static boolean ready(UiCue cue) {
        int gate = cue.gate().ordinal();
        long now = System.currentTimeMillis();
        if (now - GATES[gate] < cue.intervalMs()) return false;
        GATES[gate] = now;
        return true;
    }

    private static void play(SoundEvent event, float gain, float pitch) {
        float master = volume.get();
        if (master <= MUTE || event == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, gain * master * LEVEL));
    }
}
