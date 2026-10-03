package com.persiki84.battlecraft.client.lyrics;

import java.util.List;

public record Lyrics(List<LyricLine> lines) {
    public static final Lyrics NONE = new Lyrics(List.of());

    public Lyrics {
        lines = List.copyOf(lines);
    }

    public boolean present() {
        return !lines.isEmpty();
    }

    public int lineAt(long positionMs) {
        int low = 0;
        int high = lines.size() - 1;
        int found = -1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (lines.get(middle).startMs() <= positionMs) {
                found = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return found;
    }
}
