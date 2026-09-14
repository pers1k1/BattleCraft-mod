package com.persiki84.battlecraft.client.voice;

// WHY: в моде рации голос идёт чистым, будто собеседник стоит рядом. Эфир так не звучит:
// WHY: полоса режется до телефонной, тракт поджимается до хрипа, а дальность добавляет шум,
// WHY: провалы и сужение полосы - разборчивость падает вместе с силой сигнала
public final class RadioFilter {
    private static final float RATE = 48000.0f;
    private static final float HIGH_CUT = 320.0f;
    private static final float LOW_CUT_NEAR = 3400.0f;
    private static final float LOW_CUT_FAR = 1900.0f;
    private static final float DRIVE_NEAR = 1.6f;
    private static final float DRIVE_FAR = 4.2f;
    private static final float HISS_FLOOR = 0.006f;
    private static final float HISS_FAR = 0.085f;
    private static final float DROPS_PER_SECOND = 2.2f;
    private static final float DROP_HOLD_MS = 110.0f;
    private static final float GATE_PER_SAMPLE = 0.00035f;
    private static final float CLICK_MS = 45.0f;
    private static final int SIGNAL_STEPS = 16;

    private final Biquad highPass = new Biquad();
    private final Biquad lowPass = new Biquad();
    private final Biquad hissPass = new Biquad();

    private int tunedTo = -1;
    private float gate = 1.0f;
    private float dropLeft;
    private float clickLeft;
    private int noise = 0x9E3779B9;

    public void open() {
        clickLeft = CLICK_MS;
    }

    public void reset() {
        highPass.reset();
        lowPass.reset();
        hissPass.reset();
        tunedTo = -1;
        gate = 1.0f;
        dropLeft = 0.0f;
        clickLeft = 0.0f;
    }

    // WHY: коэффициенты считаются заново только при смене ступени сигнала: сила приходит с сервера
    // WHY: раз в несколько тиков, а кадров звука пятьдесят в секунду
    public void apply(short[] audio, float signal, float amount) {
        if (audio == null || audio.length == 0) return;

        float strength = clamp01(signal);
        tune(strength);

        float distance = 1.0f - strength;
        float drive = DRIVE_NEAR + (DRIVE_FAR - DRIVE_NEAR) * distance;
        float hiss = (HISS_FLOOR + (HISS_FAR - HISS_FLOOR) * distance * distance) * amount;
        float drops = DROPS_PER_SECOND * distance * distance * amount;
        float perSample = 1000.0f / RATE;

        for (int index = 0; index < audio.length; index++) {
            float value = audio[index] / 32768.0f;
            value = lowPass.process(highPass.process(value));
            value = crush(value * drive) / drive;
            value = value * gate(drops, perSample) + air(hiss);
            audio[index] = round(value * 32767.0f);
        }
    }

    private void tune(float strength) {
        int step = Math.round(strength * SIGNAL_STEPS);
        if (step == tunedTo) return;

        tunedTo = step;
        float share = step / (float) SIGNAL_STEPS;
        highPass.highPass(HIGH_CUT);
        lowPass.lowPass(LOW_CUT_FAR + (LOW_CUT_NEAR - LOW_CUT_FAR) * share);
        hissPass.highPass(1200.0f);
    }

    // WHY: провал длится десятки миллисекунд и входит плавно: мгновенная тишина щёлкает громче,
    // WHY: чем сами помехи, и слышится поломкой звука, а не слабым сигналом
    private float gate(float drops, float perSample) {
        if (dropLeft > 0.0f) {
            dropLeft -= perSample;
        } else if (drops > 0.0f && random() < drops / RATE) {
            dropLeft = DROP_HOLD_MS * (0.4f + random());
        }

        float target = dropLeft > 0.0f ? 0.08f : 1.0f;
        gate += Math.signum(target - gate) * Math.min(Math.abs(target - gate), GATE_PER_SAMPLE);
        return gate;
    }

    private float air(float hiss) {
        float level = hiss * (1.0f + (1.0f - gate) * 3.0f);
        if (clickLeft > 0.0f) {
            clickLeft -= 1000.0f / RATE;
            level += 0.09f * clamp01(clickLeft / CLICK_MS);
        }
        return level <= 0.0f ? 0.0f : hissPass.process((random() * 2.0f - 1.0f) * level);
    }

    private static float crush(float value) {
        float clamped = Math.max(-3.0f, Math.min(3.0f, value));
        return clamped * (27.0f + clamped * clamped) / (27.0f + 9.0f * clamped * clamped);
    }

    private static short round(float value) {
        int sample = Math.round(value);
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    // WHY: шум идёт из своего xorshift, а не из Random: метод зовут на каждый отсчёт, то есть
    // WHY: сорок восемь тысяч раз в секунду на каждого говорящего
    private float random() {
        noise ^= noise << 13;
        noise ^= noise >>> 17;
        noise ^= noise << 5;
        return (noise >>> 8) / (float) (1 << 24);
    }

    private static final class Biquad {
        private float b0 = 1.0f;
        private float b1;
        private float b2;
        private float a1;
        private float a2;
        private float x1;
        private float x2;
        private float y1;
        private float y2;

        private void highPass(float frequency) {
            double w0 = 2.0 * Math.PI * frequency / RATE;
            double cos = Math.cos(w0);
            double alpha = Math.sin(w0) / (2.0 * 0.707);
            double a0 = 1.0 + alpha;
            set((1.0 + cos) / 2.0 / a0, -(1.0 + cos) / a0, (1.0 + cos) / 2.0 / a0,
                    -2.0 * cos / a0, (1.0 - alpha) / a0);
        }

        private void lowPass(float frequency) {
            double w0 = 2.0 * Math.PI * frequency / RATE;
            double cos = Math.cos(w0);
            double alpha = Math.sin(w0) / (2.0 * 0.707);
            double a0 = 1.0 + alpha;
            set((1.0 - cos) / 2.0 / a0, (1.0 - cos) / a0, (1.0 - cos) / 2.0 / a0,
                    -2.0 * cos / a0, (1.0 - alpha) / a0);
        }

        private void set(double b0, double b1, double b2, double a1, double a2) {
            this.b0 = (float) b0;
            this.b1 = (float) b1;
            this.b2 = (float) b2;
            this.a1 = (float) a1;
            this.a2 = (float) a2;
        }

        private float process(float value) {
            float out = b0 * value + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = value;
            y2 = y1;
            y1 = out;
            return out;
        }

        private void reset() {
            x1 = 0.0f;
            x2 = 0.0f;
            y1 = 0.0f;
            y2 = 0.0f;
        }
    }
}
