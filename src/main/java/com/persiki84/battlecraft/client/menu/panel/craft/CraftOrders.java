package com.persiki84.battlecraft.client.menu.panel.craft;

import com.persiki84.battlecraft.craft.CraftPhase;

// WHY: студия правит запреты теми же командами, что и оператор в чате: сервер проверяет права,
// WHY: разбирает предмет и пишет файл одним путём, и ответ виден в строке отклика под окном
final class CraftOrders {
    private static final String ROOT = "battlecraft craft ";

    private CraftOrders() {}

    static String ban(String spec, CraftPhase phase) {
        return ROOT + "ban " + spec + " " + phase.id();
    }

    static String unban(String key) {
        return ROOT + "unban " + key;
    }

    static String phase(String key, CraftPhase phase) {
        return ROOT + "phase " + key + " " + phase.id();
    }

    static String clear(CraftPhase phase) {
        return ROOT + "clear " + phase.id();
    }
}
