package com.persiki84.battlecraft.client.voice;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.compat.walkie.Walkie;
import com.persiki84.battlecraft.sound.RadioCue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.UUID;

// WHY: рация обязана щёлкать сама по себе, а не когда нарисована карточка эфира: слот худа
// WHY: выключается отдельной ручкой, а звук нажатия и отпускания клавиши от неё не зависит.
// WHY: состояние ведётся всегда, даже с выключенным звуком, иначе первое же включение ручки
// WHY: выстреливает пачкой сигналов за все передачи, которые шли, пока она была снята
public final class RadioChirp {
    private static final int NO_CANAL = Integer.MIN_VALUE;
    private static final int NO_RANGE = -1;

    private static final boolean[] incoming = new boolean[VoiceTraffic.VOICES];
    private static final UUID[] heard = new UUID[VoiceTraffic.VOICES];
    private static final long[] gates = new long[RadioCue.values().length];

    private static boolean sending;
    private static boolean powered;
    private static boolean known;
    private static int heldCanal = NO_CANAL;
    private static int heldRange = NO_RANGE;

    private RadioChirp() {}

    public static void tick(Player player) {
        if (player == null || !Walkie.available()) {
            forget();
            return;
        }

        long now = System.currentTimeMillis();
        watchOwn(player, now);
        watchIncoming(now);
        watchPower(player);
    }

    public static void forget() {
        Arrays.fill(incoming, false);
        Arrays.fill(heard, null);
        sending = false;
        powered = false;
        known = false;
        heldCanal = NO_CANAL;
        heldRange = NO_RANGE;
    }

    // WHY: условие своей передачи то же, что у карточки эфира: рация в руке или зажатая клавиша
    // WHY: эфира, и голос идёт. Считать по одной клавише нельзя - при голосовой активации её не
    // WHY: нажимают вовсе, а рация в руке вещает и без неё
    private static void watchOwn(Player player, long now) {
        boolean live = RadioTalk.speaking(player, now);
        if (live == sending) return;

        sending = live;
        play(live ? RadioCue.KEY_DOWN : RadioCue.KEY_UP);
    }

    // WHY: место в списке передач переиспользуется другим говорящим, поэтому открытие считается
    // WHY: только у того же самого: смена говорящего закрывает прежнюю передачу и ждёт кадр
    private static void watchIncoming(long now) {
        for (int index = 0; index < VoiceTraffic.VOICES; index++) {
            VoiceTraffic.Radio radio = VoiceTraffic.at(index);
            UUID speaker = radio.id();
            boolean same = speaker != null && speaker.equals(heard[index]);
            boolean live = same && radio.live(now) && radio.tape().talking(now);
            heard[index] = speaker;
            if (live == incoming[index]) continue;

            incoming[index] = live;
            play(live ? RadioCue.INCOMING_OPEN : RadioCue.INCOMING_CLOSE);
        }
    }

    // WHY: питание щёлкают у рации, которой игрок слушает частоту, а не у той, что в руке:
    // WHY: клавиша чужого мода включает лучшую рацию хотбара, не доставая её. Смена канала или
    // WHY: дальности значит, что это другая рация: отсчёт заводится заново и молчит
    private static void watchPower(Player player) {
        ItemStack carried = Walkie.carried(player);
        if (carried.isEmpty()) {
            known = false;
            heldCanal = NO_CANAL;
            heldRange = NO_RANGE;
            return;
        }

        int canal = Walkie.canal(carried);
        int range = Walkie.range(carried);
        boolean active = Walkie.active(carried);
        if (!known || canal != heldCanal || range != heldRange) {
            known = true;
            heldCanal = canal;
            heldRange = range;
            powered = active;
            return;
        }
        if (active == powered) return;

        powered = active;
        play(active ? RadioCue.POWER_ON : RadioCue.POWER_OFF);
    }

    private static void play(RadioCue cue) {
        if (!HudConfig.radioChirp() || !ready(cue)) return;

        SoundEvent event = cue.event();
        Minecraft mc = Minecraft.getInstance();
        if (event == null || mc.getSoundManager() == null) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(event, cue.pitch(), cue.gain()));
    }

    // WHY: на частоте говорят разом несколько человек, а рация щёлкает один раз: сигналы одного
    // WHY: рода, пришедшие в один кадр, складываются в кашу вместо щелчка
    private static boolean ready(RadioCue cue) {
        long now = System.currentTimeMillis();
        if (now - gates[cue.ordinal()] < cue.intervalMs()) return false;

        gates[cue.ordinal()] = now;
        return true;
    }
}
