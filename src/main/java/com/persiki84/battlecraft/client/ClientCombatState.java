package com.persiki84.battlecraft.client;

public final class ClientCombatState {
    private static boolean engaged;

    private ClientCombatState() {}

    public static void accept(boolean value) {
        engaged = value;
    }

    public static void forget() {
        engaged = false;
    }

    public static boolean engaged() {
        return engaged;
    }
}
