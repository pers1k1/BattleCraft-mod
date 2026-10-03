package com.persiki84.battlecraft.client.island;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.lyrics.LyricLine;
import com.persiki84.battlecraft.client.lyrics.Lyrics;
import com.persiki84.battlecraft.client.lyrics.LyricsService;
import com.persiki84.battlecraft.client.media.MediaTrack;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiSweep;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

// WHY: во вступлении, в проигрыше дольше REST_MS и после последней строки в слоте стоит название:
// WHY: погасшая строка висела бы над тишиной, а пустой слот выглядел бы поломкой. Остров HUD и
// WHY: остров рабочего стола держат каждый свою строку, а текст песни у них общий (LyricsService)
public final class IslandLyrics {
    private static final long LINGER_MS = 1500L;
    private static final long LEAD_MS = 200L;
    private static final float WIDEN_RATE = 6.0f;
    private static final long REST_MS = 4000L;
    private static final float MORPH_SHARE = 0.3f;
    private static final float MORPH_MIN_SECONDS = 0.14f;
    private static final float MORPH_MAX_SECONDS = 0.6f;

    private final LineSweep sweep = new LineSweep();
    private final float[] measuredScales = {Float.NaN, Float.NaN, Float.NaN};
    private final float[] widths = new float[3];
    private Lyrics lyrics = Lyrics.NONE;
    private LyricLine shown;
    private boolean inSong;
    private float widened;
    private LyricLine sung;
    private Lyrics measured;
    private float measuredPixels;
    private int nextSlot;

    public LyricLine line(MediaTrack track, long now) {
        Lyrics fresh = HudConfig.islandLyrics() && track.present() ? LyricsService.lyrics(track) : Lyrics.NONE;
        if (fresh != lyrics) {
            lyrics = fresh;
            shown = null;
        }
        sung = current(track, now);
        return sung;
    }

    private LyricLine current(MediaTrack track, long now) {
        if (!lyrics.present()) {
            inSong = false;
            return null;
        }

        long at = track.elapsedMs(now) - Math.round(HudConfig.islandLyricsOffset() * 1000.0f);
        int index = lyrics.lineAt(at + LEAD_MS);
        inSong = index >= 0 && !(index + 1 >= lyrics.lines().size() && resting(index, at));
        if (index < 0 || resting(index, at)) return null;
        LyricLine line = lyrics.lines().get(index);
        if (line != shown) {
            shown = line;
            sweep.load(line);
        }
        sweep.time(at);
        return line;
    }

    // WHY: строка выбирается на LEAD_MS раньше своего начала, а подсветка идёт по настоящему времени:
    // WHY: смена строки длится доли секунды, и без упреждения первые слова пелись над ещё приходящей строкой
    private boolean resting(int index, long at) {
        LyricLine line = lyrics.lines().get(index);
        long quietFrom = line.endMs() + LINGER_MS;
        if (at <= quietFrom) return false;
        return index + 1 >= lyrics.lines().size() || lyrics.lines().get(index + 1).startMs() - quietFrom > REST_MS;
    }

    // WHY: остров расширяется под строки не на смене песни, а когда начинается первая строка, держит
    // WHY: ширину через проигрыши и сужается после последней. Переход идёт плавно, а не скачком
    public float widen(float delta) {
        float target = inSong ? 1.0f : 0.0f;
        widened += (target - widened) * (1.0f - (float) Math.exp(-WIDEN_RATE * delta));
        if (Math.abs(target - widened) < 0.001f) widened = target;
        return widened;
    }

    public boolean engaged() {
        return lyrics.present();
    }

    public UiSweep sweep() {
        return sung == null ? UiSweep.NONE : sweep;
    }

    public static float pace(LyricLine line) {
        float seconds = line.spanMs() / 1000.0f * MORPH_SHARE;
        return Math.max(MORPH_MIN_SECONDS, Math.min(MORPH_MAX_SECONDS, seconds));
    }

    // WHY: ширина острова держится по самой длинной строке песни: подгонка под каждую строку
    // WHY: дёргала бы стекло каждые две-три секунды. Замер один раз на текст, кегль и плотность пикселей
    public float widest(GuiGraphics graphics, Font font, float scale) {
        float pixels = UiRender.pixels(graphics);
        if (measured != lyrics || measuredPixels != pixels) {
            measured = lyrics;
            measuredPixels = pixels;
            Arrays.fill(measuredScales, Float.NaN);
        }
        for (int slot = 0; slot < measuredScales.length; slot++) {
            if (measuredScales[slot] == scale) return widths[slot];
        }
        int slot = nextSlot;
        nextSlot = (nextSlot + 1) % measuredScales.length;
        measuredScales[slot] = scale;
        widths[slot] = 0.0f;
        for (LyricLine line : lyrics.lines()) {
            widths[slot] = Math.max(widths[slot], UiRender.measureToned(graphics, font, Component.literal(line.text()), scale));
        }
        return widths[slot];
    }
}
