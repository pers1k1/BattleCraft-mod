package com.persiki84.quarrymod.data;

import net.minecraft.core.BlockPos;

public record QuarryBlockKey(BlockPos pos, String dimension) {
    public QuarryBlockKey {
        pos = pos.immutable();
    }
}
