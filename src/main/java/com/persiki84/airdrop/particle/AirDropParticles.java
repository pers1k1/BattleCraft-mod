package com.persiki84.airdrop.particle;

import com.persiki84.airdrop.AirDropMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class AirDropParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, AirDropMod.MOD_ID);

    public static final RegistryObject<SimpleParticleType> SIGNAL_SMOKE =
            PARTICLE_TYPES.register("signal_smoke", () -> new SimpleParticleType(true));

    private AirDropParticles() {
    }

    public static void register(IEventBus bus) {
        PARTICLE_TYPES.register(bus);
    }
}
