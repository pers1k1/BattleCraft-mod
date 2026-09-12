package com.persiki84.battlecraft.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Accessor("effectActive")
    boolean battlecraft$effectActive();

    @Accessor("effectActive")
    void battlecraft$effectActive(boolean active);
}
