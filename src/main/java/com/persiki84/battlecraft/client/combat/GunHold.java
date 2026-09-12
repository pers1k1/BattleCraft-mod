package com.persiki84.battlecraft.client.combat;

import net.minecraft.client.player.LocalPlayer;

final class GunHold {

    private GunHold() {}

    static boolean armed(LocalPlayer player) {
        return TaczCombat.armed(player) || SuperbCombat.armed(player);
    }
}
