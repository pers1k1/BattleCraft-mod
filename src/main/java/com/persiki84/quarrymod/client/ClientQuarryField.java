package com.persiki84.quarrymod.client;

import com.persiki84.quarrymod.network.QuarryFieldPacket;
import com.persiki84.quarrymod.network.QuarryPulsePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientQuarryField {
    public static final class Cell {
        private final BlockPos pos;
        private final AABB bounds;
        private String ore;
        private int color;
        private int multiplier;
        private long readyAt;
        private int totalSeconds;
        private long brokenAt;
        private long restoredAt;
        private long refusedAt;
        private boolean open;
        private final float drift;
        private final long bornAt;

        private Cell(BlockPos pos, String ore) {
            this.pos = pos;
            this.bornAt = System.currentTimeMillis();
            this.bounds = new AABB(pos).inflate(1.0);
            this.drift = (Mth.getSeed(pos) & 0xFFFFL) / 65536.0f;
            rename(ore);
        }

        private void rename(String value) {
            ore = value;
            color = QuarryTint.color(value);
        }

        public BlockPos pos() {
            return pos;
        }

        public AABB bounds() {
            return bounds;
        }

        public String ore() {
            return ore;
        }

        public int color() {
            return color;
        }

        public int multiplier() {
            return multiplier;
        }

        // WHY: клетка остаётся выработкой до импульса регенерации, а не до своих часов: сервер
        // WHY: возвращает руду на своём тике, и по часам клиента на месте дыры успевала мигнуть руда
        public boolean excavated() {
            return open;
        }

        public int secondsLeft() {
            return (int) Math.max(0L, (readyAt - System.currentTimeMillis() + 999L) / 1000L);
        }

        // WHY: доля считается от полного отката, а он известен не всегда: у блока, застигнутого
        // WHY: снимком посреди восстановления, полным считается остаток, иначе кольцо стоит пустым
        public float progress() {
            if (!open || totalSeconds <= 0) return 1.0f;

            float left = (readyAt - System.currentTimeMillis()) / 1000.0f;
            return Math.max(0.0f, Math.min(1.0f, 1.0f - left / totalSeconds));
        }

        private float since(long stamp) {
            return stamp <= 0L ? Float.MAX_VALUE : (System.currentTimeMillis() - stamp) / 1000.0f;
        }

        public float sinceBroken() {
            return since(brokenAt);
        }

        public float sinceRestored() {
            return since(restoredAt);
        }

        public float sinceRefused() {
            return since(refusedAt);
        }

        public float sinceBorn() {
            return since(bornAt);
        }

        public float drift() {
            return drift;
        }

        private void restore(long now) {
            open = false;
            readyAt = 0L;
            restoredAt = now;
        }
    }

    private static final Map<Long, Cell> cells = new HashMap<>();
    private static final List<Cell> listed = new ArrayList<>();

    private ClientQuarryField() {}

    public static List<Cell> cells() {
        return listed;
    }

    public static void forget() {
        cells.clear();
        listed.clear();
    }

    public static void accept(QuarryFieldPacket packet) {
        Map<Long, Cell> kept = new HashMap<>();
        long now = System.currentTimeMillis();

        for (int index = 0; index < packet.size(); index++) {
            long key = packet.positions[index];
            Cell cell = claim(key, packet.position(index), packet.ore(index));
            cell.multiplier = packet.multipliers[index];
            adopt(cell, packet.remaining[index], packet.totals[index], now);
            kept.put(key, cell);
        }

        cells.clear();
        cells.putAll(kept);
        relist();
    }

    // WHY: снимок приходит реже импульса, и остаток в нём старше на пару секунд: время готовности
    // WHY: не переписывается, пока разница мала, иначе таймер под прицелом дёргается назад
    private static void adopt(Cell cell, int remaining, int total, long now) {
        if (remaining <= 0) {
            if (cell.open) cell.restore(now);
            return;
        }

        cell.open = true;
        long wanted = now + remaining * 1000L;
        if (Math.abs(wanted - cell.readyAt) > 1500L) cell.readyAt = wanted;
        cell.totalSeconds = Math.max(total, remaining);
    }

    public static void pulse(QuarryPulsePacket packet) {
        long key = packet.position;
        Cell cell = claim(key, packet.position(), packet.ore);
        long now = System.currentTimeMillis();

        if (packet.kind == QuarryPulsePacket.BROKEN) {
            cell.multiplier = packet.multiplier;
            cell.totalSeconds = Math.max(1, packet.seconds);
            cell.readyAt = now + packet.seconds * 1000L;
            cell.brokenAt = now;
            cell.open = true;
        } else if (packet.kind == QuarryPulsePacket.REGENERATED) {
            cell.multiplier = packet.multiplier;
            cell.restore(now);
        } else {
            cell.refusedAt = now;
            if (packet.seconds > 0 && !cell.open) {
                cell.open = true;
                cell.totalSeconds = Math.max(cell.totalSeconds, packet.seconds);
                cell.readyAt = now + packet.seconds * 1000L;
            }
        }
        relist();
    }

    private static Cell claim(long key, BlockPos pos, String ore) {
        Cell known = cells.get(key);
        if (known != null) {
            if (!known.ore.equals(ore) && !ore.isEmpty()) known.rename(ore);
            return known;
        }

        Cell created = new Cell(pos.immutable(), ore);
        cells.put(key, created);
        return created;
    }

    private static void relist() {
        listed.clear();
        listed.addAll(cells.values());
    }
}
