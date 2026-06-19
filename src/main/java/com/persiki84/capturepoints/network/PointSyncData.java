package com.persiki84.capturepoints.network;

import net.minecraft.core.BlockPos;

public class PointSyncData {
    public final String owner;
    public final BlockPos pos;

    public PointSyncData(String owner, BlockPos pos) {
        this.owner = owner;
        this.pos = pos;
    }
}
