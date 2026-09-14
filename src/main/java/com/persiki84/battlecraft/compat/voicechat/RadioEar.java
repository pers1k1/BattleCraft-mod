package com.persiki84.battlecraft.compat.voicechat;

import com.persiki84.battlecraft.client.hud.HudConfig;
import com.persiki84.battlecraft.client.voice.RadioFilter;
import com.persiki84.battlecraft.client.voice.VoiceTape;
import com.persiki84.battlecraft.client.voice.VoiceTraffic;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;

// WHY: лента ведётся отсчётами самого голосового чата, а не звуком системы: у острова источник
// WHY: это loopback, и туда бьют дискорд и браузер, а голос обязан отзываться только на голос
final class RadioEar {
    private RadioEar() {}

    static void listen(EventRegistration registration) {
        registration.registerEvent(ClientSoundEvent.class, RadioEar::onOwnVoice);
        registration.registerEvent(ClientReceiveSoundEvent.StaticSound.class, RadioEar::onRadioVoice);
    }

    private static void onOwnVoice(ClientSoundEvent event) {
        VoiceTraffic.own().feed(event.getRawAudio());
    }

    // WHY: статическим каналом ходят и группы голосового чата, поэтому искажается только то,
    // WHY: что сервер подтвердил как рацию; уровень снимается до искажения, иначе ленту ведёт шум
    private static void onRadioVoice(ClientReceiveSoundEvent.StaticSound event) {
        VoiceTraffic.Radio radio = VoiceTraffic.find(event.getId());
        long now = System.currentTimeMillis();
        if (radio == null || !radio.live(now)) return;

        short[] audio = event.getRawAudio();
        if (audio == null || audio.length == 0) return;

        VoiceTape tape = radio.tape();
        boolean opening = !tape.talking(now);
        tape.feed(audio);
        if (!HudConfig.radioStatic()) return;

        RadioFilter filter = radio.filter();
        if (opening) filter.open();
        filter.apply(audio, radio.signal(), HudConfig.radioNoise());
        event.setRawAudio(audio);
    }
}
