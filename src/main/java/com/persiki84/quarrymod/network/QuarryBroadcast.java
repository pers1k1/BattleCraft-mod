package com.persiki84.quarrymod.network;

import com.persiki84.quarrymod.data.QuarryBlockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QuarryBroadcast {
    private static final int FIELD_RADIUS = 48;
    private static final int FIELD_LIMIT = 192;
    private static final int PERIOD_TICKS = 40;
    private static final long FORCE_MILLIS = 15000L;
    private static final double PULSE_RADIUS = FIELD_RADIUS;

    private static final Map<UUID, Long> sentSignature = new HashMap<>();
    private static final Map<UUID, Long> sentAt = new HashMap<>();

    private QuarryBroadcast() {}

    public static void tick(MinecraftServer server, QuarryBlockManager manager) {
        if (server.getTickCount() % PERIOD_TICKS != 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player, manager, false);
        }
    }

    public static void forget(UUID player) {
        sentSignature.remove(player);
        sentAt.remove(player);
    }

    public static void clear() {
        sentSignature.clear();
        sentAt.clear();
    }

    // WHY: снимок уходит только когда состав поля под игроком изменился: секунды отката клиент
    // WHY: ведёт сам от импульса, и пересылка целого списка ради тикающего числа гонит трафик зря
    public static void sync(ServerPlayer player, QuarryBlockManager manager, boolean forced) {
        Field field = collect(player, manager);
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();
        Long previous = sentSignature.get(id);
        Long stamp = sentAt.get(id);
        boolean stale = stamp == null || now - stamp > FORCE_MILLIS;

        if (!forced && !stale && previous != null && previous == field.signature) return;

        sentSignature.put(id, field.signature);
        sentAt.put(id, now);
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), field.packet());
    }

    public static void broken(ServerLevel level, BlockPos pos, Block ore, int multiplier, int seconds) {
        send(level, pos, new QuarryPulsePacket(pos, QuarryPulsePacket.BROKEN, oreId(ore), multiplier, seconds));
    }

    public static void regenerated(ServerLevel level, BlockPos pos, Block ore, QuarryBlockManager manager) {
        send(level, pos, new QuarryPulsePacket(pos, QuarryPulsePacket.REGENERATED, oreId(ore),
                manager.multiplier(ore), 0));
    }

    public static void refused(ServerPlayer player, BlockPos pos, Block ore, int seconds) {
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new QuarryPulsePacket(pos, QuarryPulsePacket.REFUSED, oreId(ore), 1, seconds));
    }

    private static void send(ServerLevel level, BlockPos pos, QuarryPulsePacket packet) {
        PacketDistributor.TargetPoint point = new PacketDistributor.TargetPoint(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                PULSE_RADIUS, level.dimension());
        PacketHandler.INSTANCE.send(PacketDistributor.NEAR.with(() -> point), packet);
    }

    private static String oreId(Block ore) {
        var id = ForgeRegistries.BLOCKS.getKey(ore);
        return id == null ? "" : id.toString();
    }

    private static Field collect(ServerPlayer player, QuarryBlockManager manager) {
        Field field = new Field();
        manager.collectAround(player.serverLevel(), player.blockPosition(), FIELD_RADIUS, FIELD_LIMIT, field::add);
        return field;
    }

    private static final class Field {
        private final List<String> ores = new ArrayList<>();
        private final List<long[]> rows = new ArrayList<>();
        private long signature = 1L;

        private void add(BlockPos pos, net.minecraft.resources.ResourceLocation ore, int multiplier,
                         int remaining, int total) {
            String name = ore == null ? "" : ore.toString();
            int slot = ores.indexOf(name);
            if (slot < 0) {
                slot = ores.size();
                ores.add(name);
            }
            rows.add(new long[] {pos.asLong(), slot, multiplier, remaining, total});
            signature = signature * 31L + pos.asLong();
            signature = signature * 31L + (long) name.hashCode() * 31L + multiplier;
            signature = signature * 31L + (remaining > 0 ? 1L : 0L);
        }

        private QuarryFieldPacket packet() {
            int count = rows.size();
            long[] positions = new long[count];
            int[] oreIndices = new int[count];
            int[] multipliers = new int[count];
            int[] remaining = new int[count];
            int[] totals = new int[count];
            for (int index = 0; index < count; index++) {
                long[] row = rows.get(index);
                positions[index] = row[0];
                oreIndices[index] = (int) row[1];
                multipliers[index] = (int) row[2];
                remaining[index] = (int) row[3];
                totals[index] = (int) row[4];
            }
            return new QuarryFieldPacket(ores, positions, oreIndices, multipliers, remaining, totals);
        }
    }
}
