package com.persiki84.battlecraft.client;

import com.persiki84.battlecraft.rules.MarkerRange;

import java.util.Arrays;

public final class ClientMarkerRanges {
    private static int[] blocks = defaults();

    private ClientMarkerRanges() {}

    // WHY: пакет чужой сборки может нести другое число видов: лишнее отбрасывается, недостающее
    // WHY: остаётся заводским, иначе один разъехавшийся вид уносил бы с собой все остальные
    public static void accept(int[] sent) {
        int[] taken = defaults();
        int shared = Math.min(sent.length, taken.length);
        for (int index = 0; index < shared; index++) {
            taken[index] = clamp(sent[index]);
        }
        blocks = taken;
    }

    public static void forget() {
        blocks = defaults();
    }

    public static int blocks(MarkerRange range) {
        return blocks[range.ordinal()];
    }

    private static int[] defaults() {
        int[] made = new int[MarkerRange.values().length];
        Arrays.fill(made, MarkerRange.DEFAULT_BLOCKS);
        return made;
    }

    private static int clamp(int value) {
        return Math.max(MarkerRange.MIN_BLOCKS, Math.min(MarkerRange.MAX_BLOCKS, value));
    }
}
