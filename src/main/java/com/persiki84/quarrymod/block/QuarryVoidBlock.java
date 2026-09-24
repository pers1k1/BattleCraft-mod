package com.persiki84.quarrymod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

// WHY: выработка стоит на месте добытой руды до конца отката, и всё её поведение это защита
// WHY: от обхода карьера: неразрушима, не толкается, не пропускает игрока и не даёт спавн
public class QuarryVoidBlock extends Block {
    public static final int LIGHT = 4;

    private static final BlockBehaviour.Properties PROPERTIES = BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(-1.0F, 3600000.0F)
            .noLootTable()
            .noOcclusion()
            .noParticlesOnBreak()
            .sound(SoundType.AMETHYST)
            .lightLevel(state -> LIGHT)
            .pushReaction(PushReaction.BLOCK)
            .isValidSpawn((state, level, pos, type) -> false)
            .isRedstoneConductor((state, level, pos) -> false)
            .isSuffocating((state, level, pos) -> false)
            .isViewBlocking((state, level, pos) -> false);

    public QuarryVoidBlock() {
        super(PROPERTIES);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }
}
