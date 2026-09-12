package com.persiki84.shared.gunsmith;

import java.util.List;

public record GunSlot(String id, String label, String installed, List<GunOption> options) {

    public record GunOption(String value, String label) {}
}
