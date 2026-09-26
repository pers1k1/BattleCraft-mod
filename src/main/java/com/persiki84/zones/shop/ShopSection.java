package com.persiki84.zones.shop;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ShopSection {
    private final String id;
    private String title;
    private final ShopAccess access = new ShopAccess();
    private final Map<String, ShopSection> children = new LinkedHashMap<>();
    private final Map<String, ShopEntry> entries = new LinkedHashMap<>();

    public ShopSection(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public String id() { return id; }
    public String title() { return title == null || title.isEmpty() ? id : title; }
    public ShopAccess access() { return access; }
    public Map<String, ShopSection> children() { return children; }
    public Map<String, ShopEntry> entries() { return entries; }

    public void rename(String newTitle) { title = newTitle; }

    public ShopSection child(String childId) { return children.get(childId); }
    public ShopEntry entry(String entryId) { return entries.get(entryId); }

    public List<String> childIds() { return new ArrayList<>(children.keySet()); }
    public List<String> entryIds() { return new ArrayList<>(entries.keySet()); }

    public List<String> deepEntryIds() {
        List<String> ids = new ArrayList<>(entries.keySet());
        for (ShopSection child : children.values()) {
            ids.addAll(child.entryIds());
        }
        return ids;
    }

    public ShopSection ownerOf(String entryId) {
        if (entries.containsKey(entryId)) return this;
        for (ShopSection child : children.values()) {
            if (child.entries().containsKey(entryId)) return child;
        }
        return null;
    }

    public ShopEntry take(String entryId) {
        ShopSection owner = ownerOf(entryId);
        return owner == null ? null : owner.entries.remove(entryId);
    }

    public void place(ShopEntry entry) {
        entries.put(entry.id(), entry);
    }

    public boolean moveChild(String childId, int delta) {
        return ShopOrder.move(children, childId, delta);
    }

    public boolean moveEntry(String entryId, int delta) {
        ShopSection owner = ownerOf(entryId);
        return owner != null && ShopOrder.move(owner.entries, entryId, delta);
    }

    // WHY: скрытое от команды не доезжает до клиента вовсе, а не прячется в его интерфейсе:
    // WHY: чужой ассортимент это игровая информация, и её нельзя отдавать по слову клиента
    public void write(FriendlyByteBuf buf, ShopViewer viewer, boolean full) {
        buf.writeUtf(id);
        buf.writeUtf(title());
        access.write(buf);

        List<ShopSection> shownChildren = visibleChildren(viewer.team(), full);
        buf.writeInt(shownChildren.size());
        for (ShopSection child : shownChildren) {
            child.write(buf, viewer, full);
        }

        List<ShopEntry> shownEntries = visibleEntries(viewer.team(), full);
        buf.writeInt(shownEntries.size());
        for (ShopEntry entry : shownEntries) {
            entry.write(buf, viewer.key(entry.scope()));
        }
    }

    public List<ShopSection> visibleChildren(String viewer, boolean full) {
        List<ShopSection> shown = new ArrayList<>();
        for (ShopSection child : children.values()) {
            if (full || child.access.visibleTo(viewer)) shown.add(child);
        }
        return shown;
    }

    public List<ShopEntry> visibleEntries(String viewer, boolean full) {
        List<ShopEntry> shown = new ArrayList<>();
        for (ShopEntry entry : entries.values()) {
            if (full || entry.access().visibleTo(viewer)) shown.add(entry);
        }
        return shown;
    }

    public static ShopSection read(FriendlyByteBuf buf) {
        ShopSection section = new ShopSection(buf.readUtf(), buf.readUtf());
        section.access.read(buf);

        int childCount = buf.readInt();
        for (int i = 0; i < childCount; i++) {
            ShopSection child = read(buf);
            section.children.put(child.id(), child);
        }

        int entryCount = buf.readInt();
        for (int i = 0; i < entryCount; i++) {
            ShopEntry entry = ShopEntry.read(buf);
            section.entries.put(entry.id(), entry);
        }
        return section;
    }
}
