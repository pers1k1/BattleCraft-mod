package com.persiki84.battlecraft.combat;

import com.google.common.collect.Multimap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "battlecraft")
public final class BulletResistance {
    private static final ResourceLocation TACZ = new ResourceLocation("tacz", "bullet_resistance");
    private static final ResourceLocation SUPERB = new ResourceLocation("superbwarfare", "bullet_resistance");
    private static final String MIRROR = "BattleCraft Bullet Resistance";

    private BulletResistance() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemAttributes(ItemAttributeModifierEvent event) {
        Attribute tacz = ForgeRegistries.ATTRIBUTES.getValue(TACZ);
        Attribute superb = ForgeRegistries.ATTRIBUTES.getValue(SUPERB);
        if (tacz == null || superb == null) return;

        mirror(event, tacz, superb);
        mirror(event, superb, tacz);
    }

    private static void mirror(ItemAttributeModifierEvent event, Attribute from, Attribute to) {
        for (AttributeModifier modifier : copies(event.getModifiers(), from, to)) {
            event.addModifier(to, modifier);
        }
    }

    private static List<AttributeModifier> copies(Multimap<Attribute, AttributeModifier> modifiers,
                                                  Attribute from, Attribute to) {
        List<AttributeModifier> mirrored = new ArrayList<>();
        if (present(modifiers, to)) return mirrored;

        for (Map.Entry<Attribute, AttributeModifier> entry : modifiers.entries()) {
            if (entry.getKey() != from) continue;

            AttributeModifier source = entry.getValue();
            mirrored.add(new AttributeModifier(idFor(source, to), MIRROR, source.getAmount(), source.getOperation()));
        }
        return mirrored;
    }

    private static boolean present(Multimap<Attribute, AttributeModifier> modifiers, Attribute attribute) {
        return !modifiers.get(attribute).isEmpty();
    }

    private static UUID idFor(AttributeModifier source, Attribute target) {
        String seed = source.getId() + ":" + ForgeRegistries.ATTRIBUTES.getKey(target);
        return UUID.nameUUIDFromBytes(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
