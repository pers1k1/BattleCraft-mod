package com.persiki84.battlecraft.compat.voicechat;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.radio.RadioRelay;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

// WHY: классы голосового чата есть только в рантайме и только когда мод стоит. Плагин находит
// WHY: сам голосовой чат по аннотации, поэтому ни одна наша строка на этот класс не ссылается:
// WHY: без мода он просто не грузится, и мост к рации молчит вместе с ним
@ForgeVoicechatPlugin
public class BattleCraftVoicePlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return BattleCraftMod.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, BattleCraftVoicePlugin::onMicrophone);
        if (FMLEnvironment.dist == Dist.CLIENT) RadioEar.listen(registration);
    }

    private static void onMicrophone(MicrophonePacketEvent event) {
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null || sender.getPlayer() == null) return;
        if (sender.getPlayer().getPlayer() instanceof ServerPlayer speaker) {
            RadioSender.relay(event, speaker.getUUID());
            RadioRelay.onSpeak(speaker.getUUID());
        }
    }
}
