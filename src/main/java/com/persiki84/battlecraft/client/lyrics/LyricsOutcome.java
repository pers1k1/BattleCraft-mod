package com.persiki84.battlecraft.client.lyrics;

record LyricsOutcome(Kind kind, Lyrics lyrics, String source, Format format) {
    static final LyricsOutcome MISSING = new LyricsOutcome(Kind.MISSING, Lyrics.NONE, "", Format.LRC);
    static final LyricsOutcome FAILED = new LyricsOutcome(Kind.FAILED, Lyrics.NONE, "", Format.LRC);

    enum Kind { FOUND, MISSING, FAILED }

    enum Format {
        LRC("lrc"),
        YRC("yrc");

        private final String id;

        Format(String id) {
            this.id = id;
        }

        String id() {
            return id;
        }

        Lyrics parse(String source, long durationMs) {
            return this == YRC ? YrcParser.parse(source, durationMs) : LrcParser.parse(source, durationMs);
        }

        static Format byId(String id) {
            return YRC.id.equals(id) ? YRC : LRC;
        }
    }

    static LyricsOutcome found(String source, Format format, long durationMs) {
        Lyrics lyrics = format.parse(source, durationMs);
        return lyrics.present() ? new LyricsOutcome(Kind.FOUND, lyrics, source, format) : MISSING;
    }

    boolean found() {
        return kind == Kind.FOUND;
    }

    boolean worded() {
        return found() && lyrics.lines().stream().anyMatch(line -> !line.words().isEmpty());
    }
}
