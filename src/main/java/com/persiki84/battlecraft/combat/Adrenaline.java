package com.persiki84.battlecraft.combat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class Adrenaline {
    public static final String RUSH_NAME = "BattleCraft Adrenaline Rush";

    public static final int WINDOW_TICKS = 3600;
    public static final int DOSE_LIMIT = 2;
    public static final int OVERDOSE_AMPLIFIER = 1;
    public static final int POISON_TICKS = 300;
    public static final int NAUSEA_TICKS = 200;

    public static final int TICKS_PER_SECOND = 20;
    public static final int TICKS_PER_MINUTE = 1200;

    private static final ResourceLocation RECOVERY = new ResourceLocation("parcool", "stamina_recovery");

    private static boolean probed;
    private static Attribute recovery;

    private Adrenaline() {}

    public static AdrenalineDose dose(ItemStack stack) {
        return AdrenalineDose.of(stack);
    }

    public static AttributeInstance recovery(Player player) {
        Attribute attribute = recoveryAttribute();
        return attribute == null ? null : player.getAttribute(attribute);
    }

    public static AdrenalineDose active(Player player) {
        AttributeInstance instance = recovery(player);
        if (instance == null) return null;

        for (AdrenalineDose dose : AdrenalineDose.values()) {
            if (instance.getModifier(dose.modifierId()) != null) return dose;
        }
        return null;
    }

    public static boolean rushing(Player player) {
        return active(player) != null;
    }

    private static Attribute recoveryAttribute() {
        if (!probed) {
            probed = true;
            recovery = ForgeRegistries.ATTRIBUTES.getValue(RECOVERY);
        }
        return recovery;
    }
}
