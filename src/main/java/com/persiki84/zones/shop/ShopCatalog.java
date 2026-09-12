package com.persiki84.zones.shop;

import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ShopCatalog {
    private static final Map<String, ShopSection> sections = new LinkedHashMap<>();
    private static ServerLevel currentLevel;

    private ShopCatalog() {}

    public static void bind(ServerLevel level) {
        currentLevel = level;
        sections.clear();
        for (ShopSection section : ShopStorage.load(level)) {
            sections.put(section.id(), section);
        }
    }

    public static void unbind() {
        sections.clear();
        currentLevel = null;
    }

    public static Collection<ShopSection> sections() {
        return sections.values();
    }

    public static List<String> sectionIds() {
        return new ArrayList<>(sections.keySet());
    }

    public static ShopSection section(String id) {
        return sections.get(id);
    }

    public static ShopSection createSection(String id, String title) {
        ShopSection section = new ShopSection(id, title);
        sections.put(id, section);
        persist();
        return section;
    }

    public static boolean removeSection(String id) {
        if (sections.remove(id) == null) return false;
        persist();
        return true;
    }

    public static boolean moveSection(String id, int delta) {
        if (!ShopOrder.move(sections, id, delta)) return false;
        persist();
        return true;
    }

    public static ShopSection resolve(String sectionId, String childId) {
        ShopSection section = sections.get(sectionId);
        if (section == null) return null;
        return childId == null || childId.isEmpty() ? section : section.child(childId);
    }

    public static boolean restockDue(long now) {
        boolean changed = false;
        for (ShopSection section : sections.values()) {
            changed |= restockSection(section, now);
        }
        return changed;
    }

    private static boolean restockSection(ShopSection section, long now) {
        boolean changed = false;
        for (ShopEntry entry : section.entries().values()) {
            changed |= entry.restockIfDue(now);
        }
        for (ShopSection child : section.children().values()) {
            changed |= restockSection(child, now);
        }
        return changed;
    }

    public static void persist() {
        ShopStorage.save(currentLevel, sections.values());
    }

    public static boolean isEmpty() {
        return sections.isEmpty();
    }
}
