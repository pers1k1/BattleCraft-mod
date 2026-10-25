package com.persiki84.battlecraft.client.menu.rules;

import com.persiki84.battlecraft.client.hud.ToastHud;
import com.persiki84.battlecraft.craft.CraftBan;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// WHY: о правке запретов игрок узнаёт тостом с названиями, а новые плитки в правилах помечены, пока
// WHY: он их не открыл. Первый список после входа тоста не даёт: это не правка, а знакомство.
// WHY: Пачка правок оператора приходит пакетом на каждую правку, поэтому тост один и копит счёт,
// WHY: пока виден, а не вытесняет чужие уведомления
public final class CraftBanToasts {
    private static final String TOAST_KEY = "craft_bans";
    private static final int NAMES_SHOWN = 3;
    private static final long HOLD_MS = 6000L;
    private static final Set<String> known = new HashSet<>();
    private static final Set<String> fresh = new HashSet<>();
    private static final List<Component> names = new ArrayList<>();
    private static boolean primed;
    private static int added;
    private static int removed;
    private static long shownUntil;
    private static Component text = Component.empty();

    private CraftBanToasts() {}

    public static void accept(Collection<CraftBan> bans) {
        Set<String> now = new HashSet<>();
        List<Component> appeared = new ArrayList<>();
        for (CraftBan ban : bans) {
            String seen = phased(ban);
            now.add(seen);
            if (!primed || known.contains(seen)) continue;
            appeared.add(ban.stack().getHoverName());
            fresh.add(ban.key());
        }
        int gone = 0;
        for (String key : known) {
            if (!now.contains(key)) gone++;
        }
        known.clear();
        known.addAll(now);
        if (primed && appeared.size() + gone > 0) announce(appeared, gone);
        primed = true;
    }

    private static void announce(List<Component> appeared, int gone) {
        long time = System.currentTimeMillis();
        if (time > shownUntil) {
            added = 0;
            removed = 0;
            names.clear();
        }
        added += appeared.size();
        removed += gone;
        for (int index = 0; index < appeared.size() && names.size() < NAMES_SHOWN; index++) {
            names.add(appeared.get(index));
        }
        text = compose();
        shownUntil = time + HOLD_MS;
        ToastHud.pushRenewing(TOAST_KEY, () -> text, HOLD_MS);
    }

    private static Component compose() {
        MutableComponent listed = Component.empty();
        for (int index = 0; index < names.size(); index++) {
            listed.append(index > 0 ? ", " : "").append(names.get(index));
        }
        Component tail = names.isEmpty() ? Component.empty() : Component.translatable("rules.match.toast.names", listed);
        return Component.translatable("rules.match.toast.craft", added, removed, tail);
    }

    // WHY: перенос запрета в другую фазу для игрока тоже правка: ключ знакомого списка учитывает фазу
    private static String phased(CraftBan ban) {
        return ban.key() + "@" + ban.phase().id();
    }

    public static boolean fresh(String key) {
        return fresh.contains(key);
    }

    public static void seen(String key) {
        fresh.remove(key);
    }

    public static void forget() {
        known.clear();
        fresh.clear();
        names.clear();
        added = 0;
        removed = 0;
        shownUntil = 0L;
        primed = false;
    }
}
