package com.persiki84.zones.client.menu;

import com.persiki84.zones.client.ClientShopData;
import com.persiki84.zones.shop.ShopEntry;
import com.persiki84.zones.shop.ShopSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ShopView {
    public record Found(String sectionId, String childId, String sectionTitle, ShopEntry entry) {}

    private final List<Found> shown = new ArrayList<>();
    private final List<Found> readOnly = Collections.unmodifiableList(shown);

    private String query = "";
    private String sectionId;
    private String childId;
    private int catalogVersion = -1;
    private boolean stale = true;

    public boolean searching() {
        return !query.isEmpty();
    }

    public void search(String text) {
        String trimmed = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (trimmed.equals(query)) return;

        query = trimmed;
        stale = true;
    }

    public void browse(String section, String child) {
        if (equal(section, sectionId) && equal(child, childId)) return;

        sectionId = section;
        childId = child;
        stale = true;
    }

    public List<Found> shown() {
        if (stale || catalogVersion != ClientShopData.version()) rebuild();
        return readOnly;
    }

    public Found find(String entryId) {
        for (Found found : shown()) {
            if (found.entry().id().equals(entryId)) return found;
        }
        return null;
    }

    private void rebuild() {
        catalogVersion = ClientShopData.version();
        stale = false;
        shown.clear();

        if (searching()) {
            for (ShopSection section : ClientShopData.offered()) {
                collectMatches(section);
            }
            return;
        }
        collectBrowsed();
    }

    private void collectBrowsed() {
        ShopSection section = sectionId == null ? null : ClientShopData.offeredSection(sectionId);
        if (section == null) return;

        if (childId == null) {
            addAll(section, null, section.title());
            return;
        }

        ShopSection child = section.child(childId);
        if (child != null && child.access().visibleTo(ClientShopData.team())) {
            addAll(child, childId, child.title());
        }
    }

    private void collectMatches(ShopSection section) {
        String team = ClientShopData.team();
        for (ShopEntry entry : section.visibleEntries(team, false)) {
            if (matches(entry)) shown.add(new Found(section.id(), null, section.title(), entry));
        }

        for (ShopSection child : section.visibleChildren(team, false)) {
            for (ShopEntry entry : child.visibleEntries(team, false)) {
                if (matches(entry)) shown.add(new Found(section.id(), child.id(), child.title(), entry));
            }
        }
    }

    private void addAll(ShopSection source, String child, String title) {
        for (ShopEntry entry : source.visibleEntries(ClientShopData.team(), false)) {
            shown.add(new Found(sectionId, child, title, entry));
        }
    }

    private boolean matches(ShopEntry entry) {
        if (contains(entry.stack().getHoverName().getString())) return true;
        if (contains(entry.description())) return true;
        return contains(entry.id());
    }

    private boolean contains(String text) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(query);
    }

    private static boolean equal(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
