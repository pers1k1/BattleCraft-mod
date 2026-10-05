package com.persiki84.battlecraft.client.island;

import java.util.Arrays;

// WHY: подсветка и подъём идут слогами, как в Spicy Lyrics: несколько букв загораются и поднимаются
// WHY: вместе. Слоги Spicy приходят готовыми, а слова yrc, LRCLIB и построчный текст режутся здесь
// WHY: по гласным: в кириллице каждая гласная своё ядро, в латинице подряд идущие гласные одно, немая
// WHY: «e» на конце английского слова ядром не считается. Между ядрами одна согласная уходит к
// WHY: следующему слогу, две и больше делятся после первой; ь, ъ и й остаются с предыдущим слогом
final class Syllables {
    static final int NONE = -1;

    private static final String CYRILLIC_VOWELS = "аеёиоуыэюяАЕЁИОУЫЭЮЯ";
    private static final String LATIN_VOWELS = "aeiouyAEIOUY";
    private static final String CLINGING = "ьъйЬЪЙ";
    private static final String DIGRAPH_TAILS = "hgkHGK";

    private Syllables() {}

    // WHY: номер слога для каждой буквы отрезка [from, to); пробел получает NONE, знак препинания
    // WHY: держится слога слева, чтобы запятая поднималась вместе со словом
    static void mark(int[] points, int from, int to, int[] group, int firstGroup) {
        int next = firstGroup;
        int index = from;
        while (index < to) {
            if (!wordy(points[index])) {
                group[index] = Character.isWhitespace(points[index]) || index == from ? NONE : group[index - 1];
                index++;
                continue;
            }
            int end = index;
            while (end < to && wordy(points[end])) end++;
            next = markWord(points, index, end, group, next);
            index = end;
        }
    }

    private static int markWord(int[] points, int from, int to, int[] group, int next) {
        int[] cuts = cuts(points, from, to);
        int cut = 0;
        for (int index = from; index < to; index++) {
            if (cut < cuts.length && index == cuts[cut]) {
                next++;
                cut++;
            }
            group[index] = next;
        }
        return next + 1;
    }

    private static int[] cuts(int[] points, int from, int to) {
        int[] nuclei = nuclei(points, from, to);
        int[] cuts = new int[Math.max(0, nuclei.length / 2 - 1)];
        for (int pair = 1; pair < nuclei.length / 2; pair++) {
            cuts[pair - 1] = cutBetween(points, nuclei[pair * 2 - 1], nuclei[pair * 2]);
        }
        return cuts;
    }

    // WHY: ядра хранятся парами «первая гласная, последняя гласная» подряд в одном массиве
    private static int[] nuclei(int[] points, int from, int to) {
        int[] found = new int[(to - from) * 2];
        int count = 0;
        for (int index = from; index < to; index++) {
            if (!vowel(points[index])) continue;
            boolean joined = count > 0 && found[count - 1] == index - 1 && latin(points[index]) && latin(points[index - 1]);
            if (joined) {
                found[count - 1] = index;
                continue;
            }
            found[count++] = index;
            found[count++] = index;
        }
        if (count >= 4 && silentTail(points, from, to, found[count - 2])) count -= 2;
        return Arrays.copyOf(found, count);
    }

    private static boolean silentTail(int[] points, int from, int to, int nucleus) {
        return nucleus == to - 1 && to - 2 >= from && (points[nucleus] == 'e' || points[nucleus] == 'E')
                && !vowel(points[to - 2]);
    }

    private static int cutBetween(int[] points, int lastVowel, int nextVowel) {
        int consonants = nextVowel - lastVowel - 1;
        int cut = consonants <= 1 ? nextVowel - consonants : lastVowel + 2;
        if (consonants >= 2 && DIGRAPH_TAILS.indexOf(points[cut]) >= 0 && cut - 1 > lastVowel) cut--;
        while (cut < nextVowel && CLINGING.indexOf(points[cut]) >= 0) cut++;
        return cut;
    }

    private static boolean wordy(int point) {
        return Character.isLetterOrDigit(point) || point == '\'' || point == '’';
    }

    private static boolean vowel(int point) {
        return CYRILLIC_VOWELS.indexOf(point) >= 0 || LATIN_VOWELS.indexOf(point) >= 0;
    }

    private static boolean latin(int point) {
        return point < 0x0250;
    }
}
