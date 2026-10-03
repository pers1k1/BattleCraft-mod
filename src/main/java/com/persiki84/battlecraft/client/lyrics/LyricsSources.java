package com.persiki84.battlecraft.client.lyrics;

// WHY: порядок источников по точности: LRCLIB с пословной разметкой, потом пословный yrc NetEase (он
// WHY: знает затянутые слова), потом строчный LRC LRCLIB, потом строчный LRC NetEase. Сбой одного
// WHY: источника не записывает песню в ненайденные: «нет текста» только когда оба честно ответили «нет»
final class LyricsSources {
    private static final long SOURCE_PAUSE_MS = 400L;

    private final LrclibClient lrclib;
    private final NeteaseClient netease;

    LyricsSources(String agent) {
        LyricsHttp http = new LyricsHttp(agent);
        lrclib = new LrclibClient(http);
        netease = new NeteaseClient(http);
    }

    LyricsOutcome find(TrackQuery query) throws InterruptedException {
        LyricsOutcome primary = lrclib.find(query);
        if (primary.worded()) return primary;

        Thread.sleep(SOURCE_PAUSE_MS);
        LyricsOutcome secondary = netease.find(query);
        if (secondary.worded()) return secondary;
        if (primary.found()) return primary;
        if (secondary.found()) return secondary;
        boolean failed = primary.kind() == LyricsOutcome.Kind.FAILED || secondary.kind() == LyricsOutcome.Kind.FAILED;
        return failed ? LyricsOutcome.FAILED : LyricsOutcome.MISSING;
    }
}
