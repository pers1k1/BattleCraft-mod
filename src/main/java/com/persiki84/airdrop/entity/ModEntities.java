package com.persiki84.airdrop.entity;

import com.persiki84.airdrop.AirDropMod;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AirDropMod.MOD_ID);

    public static final RegistryObject<EntityType<AirDropEntity>> AIRDROP =
            ENTITY_TYPES.register("airdrop", () ->
                    EntityType.Builder.<AirDropEntity>of(AirDropEntity::new, MobCategory.MISC)
                            .sized(1.0F, 1.0F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .setShouldReceiveVelocityUpdates(true)
                            .build("airdrop"));


    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
    }
}
