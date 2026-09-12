package com.persiki84.battlecraft.mixin;

import com.persiki84.battlecraft.client.ServerRules;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    private static final Set<Integer> battlecraft$BLOCKED_DEBUG_KEYS = Set.of(
            org.lwjgl.glfw.GLFW.GLFW_KEY_A,
            org.lwjgl.glfw.GLFW.GLFW_KEY_B,
            org.lwjgl.glfw.GLFW.GLFW_KEY_G
    );

    @Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true)
    private void battlecraft$blockDebugKeys(int key, CallbackInfoReturnable<Boolean> callback) {
        if (!ServerRules.restricted() || !battlecraft$BLOCKED_DEBUG_KEYS.contains(key)) return;
        callback.setReturnValue(true);
    }
}
