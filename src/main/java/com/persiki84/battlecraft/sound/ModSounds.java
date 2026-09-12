package com.persiki84.battlecraft.sound;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.shared.client.ui.UiCue;
import com.persiki84.shared.client.ui.UiSoundScheme;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.Set;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, BattleCraftMod.MOD_ID);

    private ModSounds() {}

    public static void register(IEventBus modEventBus) {
        declare();
        SOUNDS.register(modEventBus);
    }

    // WHY: у нескольких сигналов один и тот же семпл, а реестр не терпит повторной регистрации имени
    private static void declare() {
        Set<String> declared = new HashSet<>();
        for (UiSoundScheme scheme : UiSoundScheme.values()) {
            if (!scheme.sampled()) continue;

            for (UiCue cue : UiCue.values()) {
                String path = "ui." + scheme.id() + "." + cue.sample();
                if (!declared.add(path)) continue;
                SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(
                        new ResourceLocation(BattleCraftMod.MOD_ID, path)));
            }
        }
    }
}
