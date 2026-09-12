package com.persiki84.zones.client;

import com.persiki84.zones.shop.ShopSection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.scores.Team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientShopData {
    private static final Map<String, ShopSection> sections = new LinkedHashMap<>();
    private static int version;

    private ClientShopData() {}

    public static void replaceAll(Collection<ShopSection> incoming) {
        sections.clear();
        for (ShopSection section : incoming) {
            sections.put(section.id(), section);
        }
        version++;
    }

    public static void clear() {
        sections.clear();
        version++;
    }

    public static int version() {
        return version;
    }

    public static List<ShopSection> sections() {
        return new ArrayList<>(sections.values());
    }

    public static ShopSection section(String id) {
        return sections.get(id);
    }

    // WHY: оператору сервер шлёт полный каталог ради админского экрана, поэтому закупочный
    // WHY: экран отбирает своё по команде сам, иначе оператор видел бы чужие отделы в магазине
    public static String team() {
        LocalPlayer player = Minecraft.getInstance().player;
        Team team = player == null ? null : player.getTeam();
        return team == null ? null : team.getName();
    }

    public static List<ShopSection> offered() {
        String team = team();
        List<ShopSection> shown = new ArrayList<>();
        for (ShopSection section : sections.values()) {
            if (section.access().visibleTo(team)) shown.add(section);
        }
        return shown;
    }

    public static ShopSection offeredSection(String id) {
        ShopSection section = sections.get(id);
        return section != null && section.access().visibleTo(team()) ? section : null;
    }

}
