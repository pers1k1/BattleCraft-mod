package com.persiki84.zones.shop;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ShopAccess {
    private final Set<String> teams = new LinkedHashSet<>();

    public boolean everyone() {
        return teams.isEmpty();
    }

    public void showEveryone() {
        teams.clear();
    }

    public void allow(String team) {
        teams.add(team);
    }

    public void forbid(String team) {
        teams.remove(team);
    }

    public boolean allows(String team) {
        return teams.contains(team);
    }

    public boolean visibleTo(String team) {
        return everyone() || (team != null && teams.contains(team));
    }

    public List<String> list() {
        return new ArrayList<>(teams);
    }

    public void restore(Collection<String> stored) {
        teams.clear();
        if (stored != null) teams.addAll(stored);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(teams.size());
        for (String team : teams) {
            buf.writeUtf(team);
        }
    }

    public void read(FriendlyByteBuf buf) {
        teams.clear();
        int count = buf.readVarInt();
        for (int index = 0; index < count; index++) {
            teams.add(buf.readUtf());
        }
    }
}
