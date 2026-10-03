package com.persiki84.battlecraft.client.lyrics;

import java.util.List;

public record LyricLine(long startMs, long endMs, String text, List<LyricWord> words) {
    public LyricLine {
        words = List.copyOf(words);
    }

    public long spanMs() {
        return Math.max(1L, endMs - startMs);
    }
}
