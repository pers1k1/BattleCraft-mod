package com.persiki84.airdrop.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public final class SignalSmokeParticle extends TextureSheetParticle {
    private static final float FADE_TICKS = 50.0F;
    private static final float GROWTH = 1.6F;
    private static final float ASH = 0.42F;

    private final float startSize;
    private final float hotRed;
    private final float hotGreen;
    private final float hotBlue;

    private SignalSmokeParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
        super(level, x, y, z);
        scale(2.6F + random.nextFloat() * 1.2F);
        setSize(0.25F, 0.25F);
        startSize = quadSize;
        lifetime = 110 + random.nextInt(60);
        gravity = 3.0E-6F;
        friction = 0.96F;
        xd = dx;
        yd = dy;
        zd = dz;
        hotRed = 0.86F + random.nextFloat() * 0.1F;
        hotGreen = 0.1F + random.nextFloat() * 0.08F;
        hotBlue = 0.08F + random.nextFloat() * 0.05F;
        setColor(hotRed, hotGreen, hotBlue);
        setAlpha(0.92F);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime || alpha <= 0.0F) {
            remove();
            return;
        }
        xd += random.nextFloat() / 4000.0F * (random.nextBoolean() ? 1 : -1);
        zd += random.nextFloat() / 4000.0F * (random.nextBoolean() ? 1 : -1);
        yd -= gravity;
        move(xd, yd, zd);
        xd *= friction;
        zd *= friction;
        cool();
        if (age >= lifetime - FADE_TICKS) alpha = Math.max(0.0F, alpha - 0.92F / FADE_TICKS);
    }

    private void cool() {
        float spent = (float) age / lifetime;
        float ash = Mth.lerp(spent, 0.0F, ASH);
        setColor(Mth.lerp(ash, hotRed, 0.55F), Mth.lerp(ash, hotGreen, 0.5F), Mth.lerp(ash, hotBlue, 0.5F));
    }

    @Override
    public float getQuadSize(float partialTick) {
        float spent = Mth.clamp((age + partialTick) / lifetime, 0.0F, 1.0F);
        return startSize * (1.0F + GROWTH * spent);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double dx, double dy, double dz) {
            SignalSmokeParticle particle = new SignalSmokeParticle(level, x, y, z, dx, dy, dz);
            particle.pickSprite(sprites);
            return particle;
        }
    }
}
