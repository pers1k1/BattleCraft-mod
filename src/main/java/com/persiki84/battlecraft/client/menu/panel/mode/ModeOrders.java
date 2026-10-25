package com.persiki84.battlecraft.client.menu.panel.mode;

import com.persiki84.battlecraft.mode.ModeId;

// WHY: студия правит режимы теми же командами, что и оператор в чате: права, пределы и запись файла
// WHY: живут на сервере одним путём, а ответ виден в строке отклика под окном
final class ModeOrders {
    private static final String ROOT = "battlecraft mode ";

    private ModeOrders() {}

    static String number(ModeId mode, String field, int value) {
        return ROOT + mode.id() + " set " + field + " " + value;
    }

    static String quarries(ModeId mode, boolean value) {
        return ROOT + mode.id() + " quarries " + value;
    }

    static String money(ModeId mode, int value) {
        return ROOT + mode.id() + " money " + value;
    }

    static String reset(ModeId mode) {
        return ROOT + mode.id() + " reset";
    }

    static String active(ModeId mode) {
        return ROOT + "active " + mode.id();
    }

    static String lock(boolean value) {
        return ROOT + "lock " + value;
    }

    static String kitSlot(ModeId mode, int slot) {
        return ROOT + mode.id() + " kit add slot " + slot;
    }

    static String kitItem(ModeId mode, String spec, int count) {
        return ROOT + mode.id() + " kit add " + spec + " " + count;
    }

    static String kitCount(ModeId mode, int index, int count) {
        return ROOT + mode.id() + " kit " + index + " count " + count;
    }

    static String kitRemove(ModeId mode, int index) {
        return ROOT + mode.id() + " kit " + index + " remove";
    }

    static String kitMove(ModeId mode, int from, int to) {
        return ROOT + mode.id() + " kit " + from + " move " + to;
    }

    static String kitAttach(ModeId mode, int index, String option) {
        return ROOT + mode.id() + " kit " + index + " attach \"" + option + "\"";
    }

    static String kitDetach(ModeId mode, int index, String slot) {
        return ROOT + mode.id() + " kit " + index + " detach " + slot;
    }

    static String describe(ModeId mode, String text) {
        String trimmed = text.strip();
        return ROOT + mode.id() + " describe" + (trimmed.isEmpty() ? "" : " " + trimmed);
    }

    static String kitClear(ModeId mode) {
        return ROOT + mode.id() + " kit clear";
    }
}
