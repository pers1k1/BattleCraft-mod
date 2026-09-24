package com.persiki84.shared.client.ui;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

// WHY: метка это плашка поверх мира и глубины не знает, поэтому без луча она подписывала руду
// WHY: за стеной: визуал не имеет права знать больше глаза. Луч идёт по клеткам сеткой
// WHY: Amanatides-Woo на одном изменяемом BlockPos, без аллокаций в кадре
public final class UiSight {
    private static final int MAX_STEPS = 64;
    private static final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
    private static final Axis x = new Axis();
    private static final Axis y = new Axis();
    private static final Axis z = new Axis();

    private UiSight() {}

    public static boolean clear(BlockGetter level, Vec3 eye, BlockPos target) {
        double spanX = target.getX() + 0.5 - eye.x;
        double spanY = target.getY() + 0.5 - eye.y;
        double spanZ = target.getZ() + 0.5 - eye.z;
        x.aim(eye.x, spanX);
        y.aim(eye.y, spanY);
        z.aim(eye.z, spanZ);
        return walk(level, target);
    }

    private static boolean walk(BlockGetter level, BlockPos target) {
        for (int step = 0; step < MAX_STEPS; step++) {
            if (reached(target)) return true;

            if (x.next < y.next && x.next < z.next) {
                x.advance();
            } else if (y.next < z.next) {
                y.advance();
            } else {
                z.advance();
            }
            if (reached(target)) return true;

            probe.set(x.cell, y.cell, z.cell);
            if (level.getBlockState(probe).isSolidRender(level, probe)) return false;
        }
        return false;
    }

    private static boolean reached(BlockPos target) {
        return x.cell == target.getX() && y.cell == target.getY() && z.cell == target.getZ();
    }

    private static final class Axis {
        private int cell;
        private int step;
        private double next;
        private double stride;

        private void aim(double start, double span) {
            cell = Mth.floor(start);
            if (Math.abs(span) < 1.0E-9) {
                step = 0;
                next = Double.MAX_VALUE;
                stride = Double.MAX_VALUE;
                return;
            }
            step = span > 0.0 ? 1 : -1;
            stride = Math.abs(1.0 / span);
            double toBorder = span > 0.0 ? cell + 1.0 - start : start - cell;
            next = toBorder * stride;
        }

        private void advance() {
            cell += step;
            next += stride;
        }
    }
}
