package com.persiki84.zones.shop;

public enum StockScope {
    SHARED("shared"),
    TEAM("team"),
    PLAYER("player");

    public static final StockScope DEFAULT = SHARED;

    private final String id;

    StockScope(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String label() {
        return "zones.shop.scope." + id;
    }

    public static StockScope byId(String id) {
        for (StockScope scope : values()) {
            if (scope.id.equals(id)) return scope;
        }
        return DEFAULT;
    }
}
