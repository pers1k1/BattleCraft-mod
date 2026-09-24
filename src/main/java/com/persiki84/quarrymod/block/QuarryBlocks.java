package com.persiki84.quarrymod.block;

import com.persiki84.quarrymod.QuarryMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class QuarryBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, QuarryMod.MODID);

    public static final RegistryObject<Block> EXCAVATED = BLOCKS.register("excavated", QuarryVoidBlock::new);

    private QuarryBlocks() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }

    public static BlockState excavated() {
        return EXCAVATED.get().defaultBlockState();
    }

    public static boolean isExcavated(BlockState state) {
        return state.is(EXCAVATED.get());
    }
}
