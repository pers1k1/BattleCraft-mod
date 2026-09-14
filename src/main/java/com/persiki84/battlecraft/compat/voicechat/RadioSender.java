package com.persiki84.battlecraft.compat.voicechat;

import com.persiki84.battlecraft.radio.RadioAir;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;

import java.util.UUID;

// WHY: рацию с пояса чужой мод не слышит вовсе - он шлёт звук только с рации в руке. Этот путь
// WHY: повторяет его отправку один в один (тот же статический пакет), но берёт готовый список
// WHY: получателей из серверного тика: в звуковом потоке нельзя ни читать инвентари, ни ходить
// WHY: по списку игроков, а сюда приходит пятьдесят кадров в секунду на каждого говорящего
final class RadioSender {
    private RadioSender() {}

    static void relay(MicrophonePacketEvent event, UUID speaker) {
        UUID[] listeners = RadioAir.route(speaker);
        if (listeners.length == 0) return;

        VoicechatServerApi api = event.getVoicechat();
        if (api == null) return;

        StaticSoundPacket packet = event.getPacket().staticSoundPacketBuilder().build();
        for (UUID listener : listeners) {
            VoicechatConnection connection = api.getConnectionOf(listener);
            if (connection != null) api.sendStaticSoundPacketTo(connection, packet);
        }
    }
}
