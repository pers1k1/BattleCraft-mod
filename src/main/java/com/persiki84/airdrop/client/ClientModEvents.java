package com.persiki84.airdrop.client;

import com.persiki84.airdrop.AirDropMod;
import com.persiki84.airdrop.client.particle.SignalSmokeParticle;
import com.persiki84.airdrop.client.renderer.AirDropRenderer;
import com.persiki84.airdrop.entity.ModEntities;
import com.persiki84.airdrop.particle.AirDropParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AirDropMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.AIRDROP.get(), AirDropRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(AirDropParticles.SIGNAL_SMOKE.get(), SignalSmokeParticle.Provider::new);
    }
}
