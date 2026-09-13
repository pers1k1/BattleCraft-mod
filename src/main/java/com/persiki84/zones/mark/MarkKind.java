package com.persiki84.zones.mark;

public enum MarkKind {
    PIN("pin"),
    TEXT("text");

    public static final MarkKind DEFAULT = PIN;

    private final String id;

    MarkKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String label() {
        return "zones.mark.kind." + id;
    }

    public static MarkKind byId(String id) {
        for (MarkKind kind : values()) {
            if (kind.id.equals(id)) return kind;
        }
        return DEFAULT;
    }
}
