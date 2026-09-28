package com.persiki84.battlecraft.client.menu.desktop;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Locale;

// WHY: ранг это ступень, а не балл: начало названия, начало слова, вхождение, все слова запроса
// WHY: вразброс, начало слова в ключе. Внутри ступени порядок источников, как у Spotlight
final class SpotlightRank {
    static final int MISS = Integer.MAX_VALUE;

    private static final int TITLE_START = 0;
    private static final int WORD_START = 1;
    private static final int INSIDE = 2;
    private static final int SCATTERED = 3;
    private static final int KEY_WORD = 4;
    private static final String[] NO_WORDS = new String[0];

    private SpotlightRank() {}

    static String needle(String query) {
        return query.trim().toLowerCase(Locale.ROOT);
    }

    static String haystack(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    static String withoutEllipsis(String text) {
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == '.' || text.charAt(end - 1) == '…'
                || Character.isWhitespace(text.charAt(end - 1)))) {
            end--;
        }
        return text.substring(0, end);
    }

    static String keyOf(Component title) {
        if (!(title.getContents() instanceof TranslatableContents contents)) return null;
        return contents.getArgs().length == 0 ? contents.getKey() : null;
    }

    // WHY: слова запроса и хвост ключа считаются раз на запрос и раз на строку индекса, а не на
    // WHY: каждую сверку: с сотнями клавиш модов разбор регуляркой шёл тысячами на каждую букву
    static String[] words(String needle) {
        return needle.indexOf(' ') < 0 ? NO_WORDS : needle.split("\\s+");
    }

    static int tier(String local, String english, String tail, String needle, String[] words) {
        int best = Math.min(phrase(local, needle), phrase(english, needle));
        if (best != MISS) return best;

        best = Math.min(scattered(local, words), scattered(english, words));
        if (best != MISS) return best;

        return phrase(tail, needle) <= WORD_START ? KEY_WORD : MISS;
    }

    // WHY: общие префиксы ключей (battlecraft.custom., options.) совпали бы с любым запросом
    // WHY: вроде "bat" и залили бы выдачу, поэтому ключ сверяется только последним звеном
    static String keyTail(String key) {
        return key == null ? "" : haystack(key.substring(key.lastIndexOf('.') + 1));
    }

    private static int phrase(String haystack, String needle) {
        if (haystack.isEmpty()) return MISS;
        if (haystack.startsWith(needle)) return TITLE_START;

        int at = haystack.indexOf(needle);
        if (at < 0) return MISS;
        while (at > 0) {
            if (!Character.isLetterOrDigit(haystack.charAt(at - 1))) return WORD_START;
            at = haystack.indexOf(needle, at + 1);
        }
        return INSIDE;
    }

    private static int scattered(String haystack, String[] words) {
        if (haystack.isEmpty() || words.length == 0) return MISS;

        for (String word : words) {
            if (!haystack.contains(word)) return MISS;
        }
        return SCATTERED;
    }
}
