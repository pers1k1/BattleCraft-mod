package com.persiki84.battlecraft.client.voice;

import java.util.UUID;

// WHY: три потока на один набор говорящих - сеть приносит заявку сервера, поток голосового чата
// WHY: кормит ленту, кадр её рисует. Поэтому фиксированный массив со снимками в volatile-полях,
// WHY: а не карта: в кадре нельзя ни аллоцировать итератор, ни ждать замок звукового потока
public final class VoiceTraffic {
    public static final int VOICES = 8;

    private static final long CLAIM_MS = 1500L;

    private static final Radio[] radios = new Radio[VOICES];
    private static final VoiceTape own = new VoiceTape();

    private static long advanced = -1L;

    static {
        for (int index = 0; index < VOICES; index++) {
            radios[index] = new Radio();
        }
    }

    private VoiceTraffic() {}

    public static VoiceTape own() {
        return own;
    }

    public static Radio at(int index) {
        return radios[index];
    }

    // WHY: сервер подтверждает каждую передачу заново, потому что клиент не имеет права решать,
    // WHY: чей статический звук считать рацией: группы голосового чата ходят тем же каналом
    public static void claim(UUID speaker, boolean friend, float signal) {
        if (speaker == null) return;

        long now = System.currentTimeMillis();
        Radio radio = find(speaker);
        if (radio == null) radio = free(now);
        if (radio == null) return;

        if (!speaker.equals(radio.id)) {
            radio.tape.forget();
            radio.id = speaker;
        }
        radio.friend = friend;
        radio.signal = signal;
        radio.claimedAt = now;
    }

    public static Radio find(UUID speaker) {
        if (speaker == null) return null;
        for (Radio radio : radios) {
            if (speaker.equals(radio.id)) return radio;
        }
        return null;
    }

    // WHY: ленты двигает первый же элемент худа, который сегодня рисуется: значок микрофона и
    // WHY: карточки эфира включаются порознь, а запись обязана идти в обоих случаях одинаково
    public static void advance(long now, long frame) {
        if (frame == advanced) return;

        advanced = frame;
        own.advance(now);
        for (Radio radio : radios) {
            radio.tape.advance(now);
        }
        prune(now);
    }

    private static void prune(long now) {
        for (Radio radio : radios) {
            if (radio.id != null && !radio.live(now) && radio.tape.idle(now)) radio.release();
        }
    }

    public static void forget() {
        own.forget();
        for (Radio radio : radios) {
            radio.release();
        }
    }

    private static Radio free(long now) {
        Radio oldest = null;
        for (Radio radio : radios) {
            if (radio.id == null) return radio;
            if (radio.live(now)) continue;
            if (oldest == null || radio.claimedAt < oldest.claimedAt) oldest = radio;
        }
        return oldest;
    }

    public static final class Radio {
        private final VoiceTape tape = new VoiceTape();
        private final RadioFilter filter = new RadioFilter();

        private volatile UUID id;
        private volatile boolean friend;
        private volatile float signal;
        private volatile long claimedAt;

        public UUID id() {
            return id;
        }

        public VoiceTape tape() {
            return tape;
        }

        public RadioFilter filter() {
            return filter;
        }

        public boolean friend() {
            return friend;
        }

        public float signal() {
            return signal;
        }

        public boolean live(long now) {
            return id != null && now - claimedAt < CLAIM_MS;
        }

        private void release() {
            id = null;
            claimedAt = 0L;
            signal = 0.0f;
            tape.forget();
            filter.reset();
        }
    }
}
