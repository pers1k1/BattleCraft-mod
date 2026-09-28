package com.persiki84.battlecraft.client.menu.desktop;

import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

// WHY: английские названия нужны при любом языке игры: настройку ищут и по имени из гайда. Словарь
// WHY: читается в фоне один раз, из него остаются только строки индекса, остальное уходит сборщику
final class SpotlightEnglish {
    private static final String ENGLISH = Language.DEFAULT;

    private final AtomicInteger issue = new AtomicInteger();
    private final AtomicInteger request = new AtomicInteger();
    private volatile Map<String, String> names = Map.of();

    // WHY: чтение, начатое до смены языка или набора ключей, может закончиться позже нового: его
    // WHY: результат отбрасывается по номеру запроса, иначе он перезаписал бы свежий словарь
    void load(Set<String> keys) {
        int ticket = request.incrementAndGet();
        names = Map.of();
        Minecraft minecraft = Minecraft.getInstance();
        if (ENGLISH.equals(minecraft.getLanguageManager().getSelected())) return;

        ResourceManager resources = minecraft.getResourceManager();
        CompletableFuture.supplyAsync(() -> read(resources, keys), Util.backgroundExecutor())
                .whenComplete((found, error) -> publish(ticket, found, error));
    }

    private void publish(int ticket, Map<String, String> found, Throwable error) {
        if (error != null) {
            LogUtils.getLogger().warn("[battlecraft] spotlight english off: {}", String.valueOf(error));
            return;
        }
        if (ticket != request.get()) return;
        names = found;
        issue.incrementAndGet();
    }

    int issue() {
        return issue.get();
    }

    private static Map<String, String> read(ResourceManager resources, Set<String> keys) {
        Map<String, String> all = ClientLanguage.loadFrom(resources, List.of(ENGLISH), false).getLanguageData();
        Map<String, String> kept = new HashMap<>();
        for (String key : keys) {
            String text = all.get(key);
            if (text != null) kept.put(key, SpotlightRank.haystack(text));
        }
        return Map.copyOf(kept);
    }

    String of(String key) {
        return key == null ? "" : names.getOrDefault(key, "");
    }
}
