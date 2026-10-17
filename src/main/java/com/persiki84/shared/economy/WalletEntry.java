package com.persiki84.shared.economy;

import net.minecraft.nbt.CompoundTag;

public record WalletEntry(WalletEntryKind kind, long delta, long after, long time, String subject, int count) {
    public static final String KIND = "kind";
    public static final String DELTA = "delta";
    public static final String AFTER = "after";
    public static final String TIME = "time";
    public static final String SUBJECT = "subject";
    public static final String COUNT = "count";
    public static final int SUBJECT_LIMIT = 64;

    // WHY: предметом записи бывает имя предмета из магазина, а оно ничем не ограничено: журнал едет
    // WHY: в файл мира и в снимок студии, и одна длинная строка раздувала бы оба
    public WalletEntry {
        subject = subject == null ? "" : subject.length() > SUBJECT_LIMIT ? subject.substring(0, SUBJECT_LIMIT)
                : subject;
        count = Math.max(0, count);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(KIND, kind.id());
        tag.putLong(DELTA, delta);
        tag.putLong(AFTER, after);
        tag.putLong(TIME, time);
        tag.putString(SUBJECT, subject);
        tag.putInt(COUNT, count);
        return tag;
    }
}
