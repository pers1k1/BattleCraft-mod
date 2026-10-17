package com.persiki84.battlecraft.client.menu.lobby;

import com.persiki84.battlecraft.BattleCraftManager;
import com.persiki84.battlecraft.client.ClientGameData;
import com.persiki84.battlecraft.client.ClientLobbyData;

import java.util.List;

public enum LobbyGate {
    OPEN("battlecraft.lobby.gate.open"),
    MINE("battlecraft.lobby.gate.mine"),
    FULL("battlecraft.lobby.gate.full"),
    COOLDOWN("battlecraft.lobby.gate.cooldown"),
    LOCKED("battlecraft.lobby.gate.locked"),
    OFF("battlecraft.lobby.gate.off");

    private final String key;

    LobbyGate(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public boolean admits() {
        return this == OPEN;
    }

    // WHY: колонка обязана закрываться ровно по тем правилам, по которым откажет сервер
    // WHY: (BattleCraftManager.selectTeam): фаза, своя команда, откат смены и LobbyRoster.hasRoom.
    // WHY: Клиент лишь показывает причину, решает по-прежнему сервер
    public static LobbyGate of(String team) {
        if (ClientGameData.isSoftDisabled()) return OFF;

        String own = ClientLobbyData.ownTeam();
        if (team.equals(own)) return MINE;
        if (!choicesOpen(own)) return LOCKED;
        if (own != null && ClientGameData.getSwitchCooldownSeconds() > 0) return COOLDOWN;
        return hasRoom(team, own) ? OPEN : FULL;
    }

    private static boolean choicesOpen(String own) {
        BattleCraftManager.GamePhase phase = ClientGameData.getCurrentPhase();
        if (phase == BattleCraftManager.GamePhase.LOBBY) return true;
        return phase == BattleCraftManager.GamePhase.ACTIVE && own == null;
    }

    private static boolean hasRoom(String team, String own) {
        int size = sizeWithout(team, own);
        if (size < ClientLobbyData.slotsPerTeam()) return true;

        List<String> teams = ClientLobbyData.teams();
        for (int index = 0; index < teams.size(); index++) {
            if (sizeWithout(teams.get(index), own) < size) return false;
        }
        return true;
    }

    private static int sizeWithout(String team, String own) {
        return ClientLobbyData.sizeOf(team) - (team.equals(own) ? 1 : 0);
    }
}
