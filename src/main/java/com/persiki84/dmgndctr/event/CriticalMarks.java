package com.persiki84.dmgndctr.event;

import net.minecraft.world.entity.Entity;

public final class CriticalMarks {
    private static final int NOBODY = -1;

    private static int attackerId = NOBODY;
    private static int targetId = NOBODY;
    private static long markedAt = Long.MIN_VALUE;

    private CriticalMarks() {}

    // WHY: крит и хедшот решаются до урона тем же вызовом, в котором урон и наносится, поэтому
    // WHY: одной пометки на пару «кто-кого» в пределах тика хватает; у пули TACZ урона два
    // WHY: (обычная и бронебойная часть), и оба обязаны остаться критом
    public static void mark(Entity attacker, Entity target) {
        attackerId = attacker.getId();
        targetId = target.getId();
        markedAt = attacker.level().getGameTime();
    }

    public static boolean marked(Entity attacker, Entity target) {
        return attacker.getId() == attackerId && target.getId() == targetId
                && attacker.level().getGameTime() == markedAt;
    }
}
