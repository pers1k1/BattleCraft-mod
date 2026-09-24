package com.persiki84.airdrop.cache;

import com.google.gson.JsonObject;
import com.persiki84.airdrop.loot.LootTables;
import com.persiki84.shared.JsonRead;
import net.minecraft.core.BlockPos;

public final class LootCache {
    public static final int REFILL_MIN = 10;
    public static final int REFILL_MAX = 7200;
    public static final int REFILL_DEFAULT = 300;
    public static final long NEVER = -1L;

    private final int id;
    private final String dimension;
    private final BlockPos pos;
    private String table = LootTables.AIRDROP;
    private CacheTier tier = CacheTier.COMMON;
    private RefillMode refill = RefillMode.MATCH;
    private int refillSeconds = REFILL_DEFAULT;
    private int filledMatch;
    private long filledAt = NEVER;
    private long emptiedAt = NEVER;
    private boolean cleared = true;
    private boolean empty = true;
    private boolean missing;

    public LootCache(int id, String dimension, BlockPos pos) {
        this.id = id;
        this.dimension = dimension;
        this.pos = pos.immutable();
    }

    public int id() { return id; }
    public String dimension() { return dimension; }
    public BlockPos pos() { return pos; }
    public String table() { return table; }
    public CacheTier tier() { return tier; }
    public RefillMode refill() { return refill; }
    public int refillSeconds() { return refillSeconds; }
    public int filledMatch() { return filledMatch; }
    public long filledAt() { return filledAt; }
    public long emptiedAt() { return emptiedAt; }
    public boolean cleared() { return cleared; }
    public boolean empty() { return empty; }
    public boolean missing() { return missing; }

    public void table(String value) { table = value; }
    public void tier(CacheTier value) { tier = value; }
    public void refill(RefillMode value) { refill = value; }

    public void refillSeconds(int seconds) {
        refillSeconds = Math.max(REFILL_MIN, Math.min(REFILL_MAX, seconds));
    }

    public void filled(int match, long now, boolean nothing) {
        filledMatch = match;
        filledAt = now;
        emptiedAt = nothing ? now : NEVER;
        cleared = false;
        empty = nothing;
    }

    public void clearedOut() {
        cleared = true;
        empty = true;
        emptiedAt = NEVER;
    }

    public boolean observe(boolean nowEmpty, long now) {
        boolean changed = nowEmpty != empty;
        empty = nowEmpty;
        if (!nowEmpty) {
            changed |= emptiedAt != NEVER;
            emptiedAt = NEVER;
        } else if (emptiedAt == NEVER) {
            emptiedAt = now;
            changed = true;
        }
        return changed;
    }

    public void missing(boolean value) { missing = value; }

    public long refillAt() {
        if (!refill.timed()) return NEVER;
        long since = refill == RefillMode.TIMER ? filledAt : emptiedAt;
        return since == NEVER ? NEVER : since + refillSeconds * 20L;
    }

    public JsonObject toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("id", id);
        object.addProperty("dimension", dimension);
        object.addProperty("x", pos.getX());
        object.addProperty("y", pos.getY());
        object.addProperty("z", pos.getZ());
        object.addProperty("table", table);
        object.addProperty("tier", tier.id());
        object.addProperty("refill", refill.id());
        object.addProperty("refillSeconds", refillSeconds);
        object.addProperty("filledMatch", filledMatch);
        object.addProperty("filledAt", filledAt);
        object.addProperty("emptiedAt", emptiedAt);
        object.addProperty("cleared", cleared);
        return object;
    }

    public static LootCache fromJson(JsonObject object) {
        Float id = JsonRead.number(object, "id");
        String dimension = JsonRead.text(object, "dimension");
        Float x = JsonRead.number(object, "x");
        Float y = JsonRead.number(object, "y");
        Float z = JsonRead.number(object, "z");
        if (id == null || dimension == null || x == null || y == null || z == null) return null;

        LootCache cache = new LootCache(id.intValue(), dimension, new BlockPos(x.intValue(), y.intValue(), z.intValue()));
        cache.readSettings(object);
        cache.readState(object);
        return cache;
    }

    private void readSettings(JsonObject object) {
        String written = JsonRead.text(object, "table");
        if (written != null && LootTables.validName(written)) table = written;
        tier = CacheTier.of(JsonRead.text(object, "tier"));
        refill = RefillMode.of(JsonRead.text(object, "refill"));
        Float seconds = JsonRead.number(object, "refillSeconds");
        refillSeconds(seconds == null ? REFILL_DEFAULT : seconds.intValue());
    }

    private void readState(JsonObject object) {
        Float match = JsonRead.number(object, "filledMatch");
        filledMatch = match == null ? 0 : match.intValue();
        filledAt = stamp(object, "filledAt");
        emptiedAt = stamp(object, "emptiedAt");
        Boolean wasCleared = JsonRead.flag(object, "cleared");
        cleared = wasCleared == null || wasCleared;
        empty = cleared;
    }

    private static long stamp(JsonObject object, String key) {
        var element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return NEVER;
        return element.getAsLong();
    }
}
