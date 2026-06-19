package com.persiki84.battlecraft.mixin;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ServerLevel.class)
public abstract class ServerLevelSoundMixin {

    private static final Set<String> battlecraft$VIBRATION_NAMESPACES = Set.of("superbwarfare");

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"))
    private void battlecraft$emitSoundVibration(@Nullable Player player, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed, CallbackInfo ci) {
        if (!battlecraft$isVibrationSound(sound)) return;
        ((ServerLevel) (Object) this).gameEvent(player, GameEvent.PROJECTILE_SHOOT, new Vec3(x, y, z));
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"))
    private void battlecraft$emitSoundVibration(@Nullable Player player, Entity entity, Holder<SoundEvent> sound, SoundSource source, float volume, float pitch, long seed, CallbackInfo ci) {
        if (!battlecraft$isVibrationSound(sound)) return;
        ((ServerLevel) (Object) this).gameEvent(player, GameEvent.PROJECTILE_SHOOT, entity.position());
    }

    private static boolean battlecraft$isVibrationSound(Holder<SoundEvent> sound) {
        ResourceLocation location = sound.unwrapKey().map(ResourceKey::location).orElse(null);
        return location != null && battlecraft$VIBRATION_NAMESPACES.contains(location.getNamespace());
    }
}
