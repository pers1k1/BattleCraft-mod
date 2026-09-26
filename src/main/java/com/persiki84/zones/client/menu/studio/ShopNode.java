package com.persiki84.zones.client.menu.studio;

import com.persiki84.shared.client.menu.studio.StudioNav;
import com.persiki84.zones.client.ClientShopData;
import com.persiki84.zones.shop.ShopSection;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public record ShopNode(String section, String child) {
    private static final String SECTION_PREFIX = "s:";
    private static final String CHILD_PREFIX = "c:";
    private static final char SEPARATOR = '/';

    public static ShopNode parse(String key) {
        if (key == null) return null;
        if (key.startsWith(SECTION_PREFIX)) return new ShopNode(key.substring(SECTION_PREFIX.length()), null);
        if (!key.startsWith(CHILD_PREFIX)) return null;

        String body = key.substring(CHILD_PREFIX.length());
        int split = body.indexOf(SEPARATOR);
        return split < 0 ? null : new ShopNode(body.substring(0, split), body.substring(split + 1));
    }

    public static String keyOf(String section, String child) {
        return child == null ? SECTION_PREFIX + section : CHILD_PREFIX + section + SEPARATOR + child;
    }

    public String key() {
        return keyOf(section, child);
    }

    public boolean top() {
        return child == null;
    }

    public ShopSection topSection() {
        return ClientShopData.section(section);
    }

    public ShopSection owner() {
        ShopSection top = topSection();
        if (top == null || child == null) return top;
        return top.child(child);
    }

    public String childSuffix() {
        return child == null ? "" : " " + child;
    }

    public static List<StudioNav.Node> tree() {
        List<StudioNav.Node> nodes = new ArrayList<>();
        for (ShopSection section : ClientShopData.sections()) {
            nodes.add(StudioNav.Node.item(keyOf(section.id(), null), Component.literal(section.title()),
                    count(section.deepEntryIds().size()), 0, true));
            for (ShopSection child : section.children().values()) {
                nodes.add(StudioNav.Node.item(keyOf(section.id(), child.id()), Component.literal(child.title()),
                        count(child.entries().size()), 1, true));
            }
        }
        return nodes;
    }

    private static Component count(int value) {
        return Component.literal(String.valueOf(value));
    }
}
