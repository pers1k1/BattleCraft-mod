import math
from pathlib import Path

import numpy as np
import soundfile as sf

ROOT = Path(__file__).resolve().parent.parent
SOUNDS = ROOT / "src/main/resources/assets/battlecraft/sounds/radio"

RATE = 48000
BAND_LOW = 300.0
BAND_HIGH = 3400.0
DRIVE = 1.4
EDGE_MS = 2.0


def samples(ms):
    return int(RATE * ms / 1000.0)


def silence(ms):
    return np.zeros(samples(ms), dtype=np.float64)


def envelope(length, attack_ms, decay):
    time = np.arange(length) / RATE
    attack = np.clip(time / max(attack_ms / 1000.0, 1e-4), 0.0, 1.0)
    return attack * np.exp(-time / decay)


def tone(frequency_from, frequency_to, ms, attack_ms, decay, harmonic=0.22):
    length = samples(ms)
    time = np.arange(length) / RATE
    sweep = np.linspace(frequency_from, frequency_to, length)
    phase = 2.0 * math.pi * np.cumsum(sweep) / RATE
    voice = np.sin(phase) + harmonic * np.sin(3.0 * phase)
    return voice * envelope(length, attack_ms, decay)


def burst(ms, decay, seed, attack_ms=0.4):
    length = samples(ms)
    generator = np.random.default_rng(seed)
    return generator.standard_normal(length) * envelope(length, attack_ms, decay)


def biquad(signal, b0, b1, b2, a1, a2):
    out = np.zeros_like(signal)
    x1 = x2 = y1 = y2 = 0.0
    for index, value in enumerate(signal):
        result = b0 * value + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1 = x1, value
        y2, y1 = y1, result
        out[index] = result
    return out


def high_pass(signal, cutoff, quality=0.707):
    omega = 2.0 * math.pi * cutoff / RATE
    alpha = math.sin(omega) / (2.0 * quality)
    cosine = math.cos(omega)
    a0 = 1.0 + alpha
    return biquad(signal, (1.0 + cosine) / 2.0 / a0, -(1.0 + cosine) / a0,
                  (1.0 + cosine) / 2.0 / a0, -2.0 * cosine / a0, (1.0 - alpha) / a0)


def low_pass(signal, cutoff, quality=0.707):
    omega = 2.0 * math.pi * cutoff / RATE
    alpha = math.sin(omega) / (2.0 * quality)
    cosine = math.cos(omega)
    a0 = 1.0 + alpha
    return biquad(signal, (1.0 - cosine) / 2.0 / a0, (1.0 - cosine) / a0,
                  (1.0 - cosine) / 2.0 / a0, -2.0 * cosine / a0, (1.0 - alpha) / a0)


def band(signal, low=BAND_LOW, high=BAND_HIGH):
    return low_pass(low_pass(high_pass(high_pass(signal, low), low), high), high)


def edges(signal):
    ramp = samples(EDGE_MS)
    if len(signal) <= ramp * 2:
        return signal
    shape = np.ones(len(signal))
    shape[:ramp] = np.linspace(0.0, 1.0, ramp)
    shape[-ramp:] = np.linspace(1.0, 0.0, ramp)
    return signal * shape


def mix(*parts):
    length = max(len(part) for part in parts)
    total = np.zeros(length)
    for part in parts:
        total[:len(part)] += part
    return total


def lay(offset_ms, signal):
    return np.concatenate([silence(offset_ms), signal])


def finish(signal, peak):
    shaped = np.tanh(band(signal) * DRIVE)
    loudest = np.max(np.abs(shaped))
    if loudest < 1e-6:
        return shaped
    return edges(shaped / loudest * peak)


def key_down():
    return finish(mix(burst(9.0, 0.0022, 101) * 1.1,
                      lay(6.0, burst(70.0, 0.026, 102) * 0.16),
                      lay(8.0, tone(1850.0, 1850.0, 26.0, 1.0, 0.010) * 0.35)), 0.62)


def key_up():
    return finish(mix(tone(1520.0, 1520.0, 80.0, 3.0, 0.052) * 0.9,
                      lay(74.0, burst(46.0, 0.014, 201) * 0.42),
                      lay(96.0, burst(7.0, 0.0018, 202) * 0.5)), 0.72)


def incoming_open():
    return finish(mix(burst(26.0, 0.0075, 301) * 0.8,
                      lay(10.0, tone(880.0, 1320.0, 52.0, 2.0, 0.030) * 0.8),
                      lay(12.0, burst(80.0, 0.030, 302) * 0.13)), 0.66)


def incoming_close():
    return finish(mix(burst(130.0, 0.034, 401) * 0.95,
                      lay(4.0, tone(1180.0, 700.0, 60.0, 2.0, 0.022) * 0.45),
                      lay(126.0, burst(8.0, 0.0020, 402) * 0.35)), 0.70)


def power_on():
    return finish(mix(burst(10.0, 0.0030, 501) * 0.55,
                      lay(8.0, tone(780.0, 780.0, 62.0, 3.0, 0.040) * 0.75),
                      lay(96.0, tone(1180.0, 1180.0, 95.0, 3.0, 0.055) * 0.85),
                      lay(14.0, burst(180.0, 0.055, 502) * 0.10)), 0.68)


def power_off():
    return finish(mix(lay(0.0, tone(1180.0, 620.0, 135.0, 3.0, 0.048) * 0.85),
                      lay(126.0, burst(10.0, 0.0026, 601) * 0.6),
                      burst(90.0, 0.028, 602) * 0.09), 0.66)


CUES = {
    "key_down": key_down,
    "key_up": key_up,
    "incoming_open": incoming_open,
    "incoming_close": incoming_close,
    "power_on": power_on,
    "power_off": power_off,
}


def main():
    SOUNDS.mkdir(parents=True, exist_ok=True)
    for name, build in CUES.items():
        target = SOUNDS / (name + ".ogg")
        sf.write(target, build().astype(np.float32), RATE, format="OGG", subtype="VORBIS")
        print(f"{target.name}: {target.stat().st_size} bytes")


if __name__ == "__main__":
    main()
