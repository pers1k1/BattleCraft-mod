package com.persiki84.battlecraft.mixin;

import net.minecraft.client.renderer.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(DebugRenderer.class)
public interface DebugRendererAccessor {

    @Accessor("renderChunkborder")
    void battlecraft$setRenderChunkborder(boolean value);
}
