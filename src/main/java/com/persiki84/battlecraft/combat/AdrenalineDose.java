package com.persiki84.battlecraft.combat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

public enum AdrenalineDose {
    SYRINGE("adrenaline_syringe", 900, 1.0, 0.5, "8f3c1c5e-6b7a-4e3d-9a11-2c6f4d0e77b1"),
    INJECTOR("adrenaline_injector", 700, 0.75, 0.25, "8f3c1c5e-6b7a-4e3d-9a11-2c6f4d0e77b2");

    private static final String NAMESPACE = "survival_instinct";
    private static final double PERCENT = 100.0;

    private final ResourceLocation item;
    private final int rushTicks;
    private final double recoveryBonus;
    private final double reliefShare;
    private final UUID modifierId;

    AdrenalineDose(String path, int rushTicks, double recoveryBonus, double reliefShare, String modifierId) {
        this.item = new ResourceLocation(NAMESPACE, path);
        this.rushTicks = rushTicks;
        this.recoveryBonus = recoveryBonus;
        this.reliefShare = reliefShare;
        this.modifierId = UUID.fromString(modifierId);
    }

    public static AdrenalineDose of(ItemStack stack) {
        if (stack.isEmpty()) return null;

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        for (AdrenalineDose dose : values()) {
            if (dose.item.equals(id)) return dose;
        }
        return null;
    }

    public int rushTicks() {
        return rushTicks;
    }

    public double recoveryBonus() {
        return recoveryBonus;
    }

    public double reliefShare() {
        return reliefShare;
    }

    public UUID modifierId() {
        return modifierId;
    }

    public int recoveryPercent() {
        return (int) Math.round(recoveryBonus * PERCENT);
    }

    public int reliefPercent() {
        return (int) Math.round(reliefShare * PERCENT);
    }
}
