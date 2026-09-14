package com.persiki84.battlecraft.client.voice;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.compat.walkie.Walkie;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

// WHY: клавиша включения у чужого мода играет свой щелчок питания, и он приходит пакетом звука
// WHY: самому игроку. Свой щелчок лёг бы поверх - два звука на одно нажатие. Гасим чужой ровно
// WHY: тогда, когда звучит наш: со снятой ручкой рация обязана щёлкать по-старому
@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID, value = Dist.CLIENT)
public final class RadioChirpFilter {
    private static final Set<String> POWER_SOUNDS = Set.of("walkietalkie_on", "walkietalkie_off");

    private RadioChirpFilter() {}

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (!HudConfig.radioChirp()) return;

        SoundInstance sound = event.getSound();
        if (sound == null) return;

        ResourceLocation location = sound.getLocation();
        if (!Walkie.MOD_ID.equals(location.getNamespace())) return;
        if (!POWER_SOUNDS.contains(location.getPath())) return;

        event.setSound(null);
    }
}
