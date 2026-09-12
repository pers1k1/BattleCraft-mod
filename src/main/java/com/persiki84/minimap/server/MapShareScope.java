package com.persiki84.minimap.server;

public enum MapShareScope {
    EVERYONE,
    TEAM,
    PLAYER;

    public static MapShareScope byIndex(int index) {
        MapShareScope[] all = values();
        return index < 0 || index >= all.length ? EVERYONE : all[index];
    }

}
