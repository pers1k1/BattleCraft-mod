package com.persiki84.battlecraft.client.lyrics;

// WHY: порядок источников по точности: пословный текст из моста Spotify (Spicy Lyrics), LRCLIB с
// WHY: пословной разметкой, пословный yrc NetEase (он знает затянутые слова), пословный KRC Kugou,
// WHY: потом строчные: мост, LRCLIB, NetEase. Сбой одного источника не записывает песню в
// WHY: ненайденные: «нет текста» только когда все серверы честно ответили «нет»
final class LyricsSources {
    private static final long SOURCE_PAUSE_MS = 400L;

    private final LrclibClient lrclib;
    private final NeteaseClient netease;
    private final KugouClient kugou;

    LyricsSources(String agent) {
        LyricsHttp http = new LyricsHttp(agent);
        lrclib = new LrclibClient(http);
        netease = new NeteaseClient(http);
        kugou = new KugouClient(http);
    }

    LyricsOutcome find(TrackQuery query) throws InterruptedException {
        LyricsOutcome bridged = bridged(query);
        if (bridged.worded()) return bridged;

        LyricsOutcome primary = lrclib.find(query);
        if (primary.worded()) return primary;

        Thread.sleep(SOURCE_PAUSE_MS);
        LyricsOutcome secondary = netease.find(query);
        if (secondary.worded()) return secondary;
        Thread.sleep(SOURCE_PAUSE_MS);
        LyricsOutcome tertiary = kugou.find(query);
        if (tertiary.worded()) return tertiary;
        if (bridged.found()) return bridged;
        if (primary.found()) return primary;
        if (secondary.found()) return secondary;
        boolean failed = primary.kind() == LyricsOutcome.Kind.FAILED || secondary.kind() == LyricsOutcome.Kind.FAILED
                || tertiary.kind() == LyricsOutcome.Kind.FAILED;
        return failed ? LyricsOutcome.FAILED : LyricsOutcome.MISSING;
    }

    static LyricsOutcome bridged(TrackQuery query) {
        BridgeDrop drop = SpotifyBridge.latest();
        return drop != null && drop.fits(query) ? drop.outcome() : LyricsOutcome.MISSING;
    }
}
