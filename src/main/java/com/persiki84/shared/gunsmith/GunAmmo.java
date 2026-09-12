package com.persiki84.shared.gunsmith;

import java.util.List;

public record GunAmmo(String weapon, List<String> rounds) {

    public boolean named() {
        return weapon != null && !weapon.isEmpty();
    }
}
