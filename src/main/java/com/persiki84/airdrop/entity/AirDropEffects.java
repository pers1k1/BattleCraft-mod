package com.persiki84.airdrop.entity;

import com.persiki84.airdrop.particle.AirDropParticles;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

final class AirDropEffects {
    private static final double MODEL_UNITS = 16.0;
    private static final double FLARE_X = 0.0;
    private static final double FLARE_Y = 13.0;
    private static final double FLARE_Z = 17.0;
    private static final int TRAIL_PER_TICK = 2;
    private static final int COLUMN_PER_TICK = 2;
    private static final int DUST_GRAINS = 48;
    private static final int DUST_PUFFS = 18;

    private AirDropEffects() {
    }

    static void burnFlare(AirDropEntity drop, boolean landed) {
        Level level = drop.level();
        RandomSource random = level.getRandom();
        double yaw = Math.toRadians(180.0 - drop.getYRot());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        double x = drop.getX() + (FLARE_X * cos + FLARE_Z * sin) / MODEL_UNITS;
        double y = drop.getY() + FLARE_Y / MODEL_UNITS;
        double z = drop.getZ() + (-FLARE_X * sin + FLARE_Z * cos) / MODEL_UNITS;

        level.addParticle(ParticleTypes.SMALL_FLAME, x, y + 0.02, z, 0.0, 0.01, 0.0);
        if (random.nextInt(4) == 0) level.addParticle(ParticleTypes.LAVA, x, y, z, 0.0, 0.0, 0.0);
        if (landed) {
            rise(level, random, x, y, z);
        } else {
            trail(level, random, x, y, z);
        }
    }

    // WHY: сигнал обязан читаться издалека, а обычная частица дальше 32 блоков от камеры не рождается
    private static void rise(Level level, RandomSource random, double x, double y, double z) {
        for (int i = 0; i < COLUMN_PER_TICK; i++) {
            level.addAlwaysVisibleParticle(AirDropParticles.SIGNAL_SMOKE.get(), true,
                    x + spread(random, 0.15), y + 0.1, z + spread(random, 0.15),
                    spread(random, 0.004), 0.07 + random.nextDouble() * 0.02, spread(random, 0.004));
        }
    }

    private static void trail(Level level, RandomSource random, double x, double y, double z) {
        for (int i = 0; i < TRAIL_PER_TICK; i++) {
            level.addAlwaysVisibleParticle(AirDropParticles.SIGNAL_SMOKE.get(), true,
                    x + spread(random, 0.1), y, z + spread(random, 0.1),
                    spread(random, 0.01), 0.015 + random.nextDouble() * 0.01, spread(random, 0.01));
        }
    }

    static void raiseDust(AirDropEntity drop) {
        Level level = drop.level();
        RandomSource random = level.getRandom();
        BlockState ground = level.getBlockState(drop.blockPosition().below());
        if (ground.getRenderShape() != RenderShape.INVISIBLE) {
            BlockParticleOption grain = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            for (int i = 0; i < DUST_GRAINS; i++) burst(level, random, drop, grain, 0.9, 0.14, 0.12);
        }
        for (int i = 0; i < DUST_PUFFS; i++) burst(level, random, drop, ParticleTypes.POOF, 1.1, 0.09, 0.02);
        for (int i = 0; i < DUST_PUFFS / 2; i++) burst(level, random, drop, ParticleTypes.CLOUD, 1.3, 0.05, 0.01);
    }

    private static void burst(Level level, RandomSource random, AirDropEntity drop,
                              ParticleOptions particle,
                              double ring, double speed, double lift) {
        double angle = random.nextDouble() * Mth.TWO_PI;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double reach = ring * (0.8 + random.nextDouble() * 0.5);
        double push = speed * (0.6 + random.nextDouble() * 0.8);
        level.addParticle(particle, drop.getX() + cos * reach, drop.getY() + 0.1 + random.nextDouble() * 0.2,
                drop.getZ() + sin * reach, cos * push, lift * random.nextDouble(), sin * push);
    }

    private static double spread(RandomSource random, double half) {
        return (random.nextDouble() - 0.5) * 2.0 * half;
    }
}
