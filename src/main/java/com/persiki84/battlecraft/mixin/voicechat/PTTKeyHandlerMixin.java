package com.persiki84.battlecraft.mixin.voicechat;

import com.persiki84.battlecraft.client.voice.RadioTalk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// WHY: микрофон голосового чата открывается только своей клавишей, поэтому клавиша эфира обязана
// WHY: считаться тангентой: без этого игрок с рацией на поясе жал бы её впустую - сервер ждёт
// WHY: кадры звука, а их никто не шлёт. Спрашивают из потока микрофона, отсюда volatile у флага
@Mixin(targets = "de.maxhenkel.voicechat.voice.client.PTTKeyHandler", remap = false)
public class PTTKeyHandlerMixin {

    @Inject(method = "isPTTDown", at = @At("HEAD"), cancellable = true)
    private void battlecraft$radioKeyTalks(CallbackInfoReturnable<Boolean> info) {
        if (RadioTalk.onAir()) info.setReturnValue(true);
    }

    @Inject(method = "isAnyDown", at = @At("HEAD"), cancellable = true)
    private void battlecraft$radioKeyCounts(CallbackInfoReturnable<Boolean> info) {
        if (RadioTalk.onAir()) info.setReturnValue(true);
    }
}
