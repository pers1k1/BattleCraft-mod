package com.persiki84.dmgndctr.event;

import com.persiki84.dmgndctr.network.DamagePacket;
import com.persiki84.dmgndctr.network.PacketHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public final class DamageLedger {
    private static final int CAPACITY = 256;
    private static final double SPREAD = 0.5;
    private static final double LIFT = 0.4;

    private static final ServerPlayer[] attackers = new ServerPlayer[CAPACITY];
    private static final int[] targets = new int[CAPACITY];
    private static final double[] xs = new double[CAPACITY];
    private static final double[] ys = new double[CAPACITY];
    private static final double[] zs = new double[CAPACITY];
    private static final float[] amounts = new float[CAPACITY];
    private static final boolean[] crits = new boolean[CAPACITY];

    private static int count;

    private DamageLedger() {}

    // WHY: удары одного тика по одной цели складываются здесь, а не пакетом на каждый: дробь,
    // WHY: две части урона пули TACZ и взрыв по толпе иначе шли десятками пакетов за тик
    public static void record(ServerPlayer attacker, LivingEntity target, float amount, boolean crit) {
        int slot = find(attacker, target.getId());
        if (slot >= 0) {
            amounts[slot] += amount;
            crits[slot] |= crit;
            return;
        }
        if (count >= CAPACITY) return;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        attackers[count] = attacker;
        targets[count] = target.getId();
        xs[count] = target.getX() + (random.nextDouble() - 0.5) * SPREAD;
        ys[count] = target.getY() + target.getBbHeight() + random.nextDouble() * LIFT;
        zs[count] = target.getZ() + (random.nextDouble() - 0.5) * SPREAD;
        amounts[count] = amount;
        crits[count] = crit;
        count++;
    }

    public static void flush() {
        for (int slot = 0; slot < count; slot++) {
            if (attackers[slot] != null) send(attackers[slot], slot);
        }
        Arrays.fill(attackers, 0, count, null);
        count = 0;
    }

    private static int find(ServerPlayer attacker, int target) {
        for (int slot = 0; slot < count; slot++) {
            if (attackers[slot] == attacker && targets[slot] == target) return slot;
        }
        return -1;
    }

    private static void send(ServerPlayer attacker, int first) {
        DamagePacket packet = new DamagePacket(countOf(attacker, first));
        for (int slot = first; slot < count; slot++) {
            if (attackers[slot] != attacker) continue;

            packet.add(targets[slot], xs[slot], ys[slot], zs[slot], amounts[slot], crits[slot]);
            attackers[slot] = null;
        }
        if (attacker.connection != null) {
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> attacker), packet);
        }
    }

    private static int countOf(ServerPlayer attacker, int first) {
        int found = 0;
        for (int slot = first; slot < count; slot++) {
            if (attackers[slot] == attacker) found++;
        }
        return found;
    }
}
