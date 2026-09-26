package com.persiki84.zones.shop;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ShopEntry {
    public static final int UNLIMITED = -1;
    public static final int DESCRIPTION_LIMIT = 160;
    public static final int BUNDLE_LIMIT = 64;
    public static final String OWN_POOL = "";

    private final String id;
    private final ItemStack stack;
    private final ShopAccess access = new ShopAccess();
    private final Map<String, StockPool> pools = new LinkedHashMap<>();
    private int price;
    private String description;
    private int stock = UNLIMITED;
    private int restockSeconds;
    private StockScope scope = StockScope.DEFAULT;

    public ShopEntry(String id, ItemStack stack, int price, String description) {
        this.id = id;
        this.stack = stack;
        this.price = price;
        this.description = description;
    }

    public String id() { return id; }
    public ItemStack stack() { return stack; }
    public ShopAccess access() { return access; }
    public int price() { return price; }
    public String description() { return description; }
    public int stock() { return stock; }
    public int restockSeconds() { return restockSeconds; }
    public StockScope scope() { return scope; }

    public void setPrice(int price) { this.price = price; }
    public void setDescription(String description) { this.description = description; }

    public int bundle() { return Math.max(1, stack.getCount()); }

    public boolean limited() { return stock != UNLIMITED; }

    // WHY: клиенту приезжает ровно один склад - тот, из которого покупает он сам, поэтому
    // WHY: безключевые ответы это его собственный запас, а сервер всегда спрашивает по ключу
    public int available() { return availableIn(OWN_POOL); }

    public int availableItems() { return limited() ? available() * bundle() : UNLIMITED; }

    public boolean soldOut() { return soldOutIn(OWN_POOL); }

    public int remainingSeconds(long now) { return remainingSecondsIn(OWN_POOL, now); }

    public int availableIn(String key) {
        if (!limited()) return UNLIMITED;

        StockPool pool = pools.get(key);
        return pool == null ? stock : pool.available();
    }

    public boolean soldOutIn(String key) {
        return limited() && availableIn(key) <= 0;
    }

    public int remainingSecondsIn(String key, long now) {
        StockPool pool = pools.get(key);
        return pool == null ? 0 : pool.remainingSeconds(now);
    }

    public void setStock(int stock, int restockSeconds) {
        this.stock = stock < 0 ? UNLIMITED : stock;
        this.restockSeconds = Math.max(0, restockSeconds);
        pools.clear();
    }

    // WHY: распроданный без завоза склад стоит с нулевой отметкой готовности, и включённый потом
    // WHY: завоз его не оживлял: пустые склады получают отсчёт от момента включения
    public void setRestockSeconds(int seconds) {
        this.restockSeconds = Math.max(0, seconds);
        if (restockSeconds <= 0) return;
        long due = System.currentTimeMillis() + restockSeconds * 1000L;
        for (StockPool pool : pools.values()) {
            pool.scheduleIfIdle(due);
        }
    }

    public void refill() {
        pools.clear();
    }

    // WHY: склады разных областей несопоставимы: личный остаток нельзя выдать команде,
    // WHY: поэтому смена области возвращает всем полный запас, а не переносит остатки
    public void setScope(StockScope scope) {
        this.scope = scope == null ? StockScope.DEFAULT : scope;
        pools.clear();
    }

    public ShopEntry renamed(String newId) {
        ShopEntry copy = new ShopEntry(newId, stack, price, description);
        copy.access.restore(access.list());
        copy.stock = stock;
        copy.restockSeconds = restockSeconds;
        copy.scope = scope;
        for (Map.Entry<String, StockPool> pool : pools.entrySet()) {
            copy.pools.put(pool.getKey(), new StockPool(pool.getValue().available(), pool.getValue().readyAt()));
        }
        return copy;
    }

    public ShopEntry copied(String newId) {
        ShopEntry copy = new ShopEntry(newId, stack.copy(), price, description);
        copy.access.restore(access.list());
        copy.stock = stock;
        copy.restockSeconds = restockSeconds;
        copy.scope = scope;
        return copy;
    }

    // WHY: запас хранится связками, поэтому смена размера покупки сохраняет число связок,
    // WHY: а не штук: иначе остаток разный у тех, кто уже покупал, и у тех, кто ещё нет
    public void setBundle(int count) {
        stack.setCount(Math.max(1, count));
    }

    public void restorePool(String key, int available, long readyAt) {
        if (!limited()) return;

        StockPool pool = new StockPool(Math.min(available, stock), readyAt);
        if (pool.full(stock)) return;
        pools.put(key == null ? OWN_POOL : key, pool);
    }

    public List<String> poolKeys() {
        return new ArrayList<>(pools.keySet());
    }

    public StockPool pool(String key) {
        return pools.get(key);
    }

    public int take(String key, int units, long now) {
        if (!limited()) return units;
        return pools.computeIfAbsent(key, unused -> new StockPool(stock, 0L))
                .take(units, restockSeconds, now);
    }

    // WHY: наполнившийся склад равен новому, поэтому он выбрасывается из карты: иначе у личного
    // WHY: запаса копится по записи на каждого, кто хоть раз покупал, и они едут в файл навсегда
    public boolean restockIfDue(long now) {
        if (!limited() || pools.isEmpty()) return false;

        boolean changed = false;
        Iterator<Map.Entry<String, StockPool>> cursor = pools.entrySet().iterator();
        while (cursor.hasNext()) {
            StockPool pool = cursor.next().getValue();
            changed |= pool.refillIfDue(stock, now);
            if (pool.full(stock)) cursor.remove();
        }
        return changed;
    }

    public void write(FriendlyByteBuf buf, String key) {
        buf.writeUtf(id);
        buf.writeItem(stack);
        buf.writeInt(price);
        buf.writeUtf(description == null ? "" : description);
        buf.writeInt(stock);
        buf.writeInt(availableIn(key));
        buf.writeInt(restockSeconds);
        buf.writeVarInt(remainingSecondsIn(key, System.currentTimeMillis()));
        buf.writeUtf(scope.id());
        access.write(buf);
    }

    public static ShopEntry read(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        ItemStack stack = buf.readItem();
        int price = buf.readInt();
        String description = buf.readUtf();

        ShopEntry entry = new ShopEntry(id, stack, price, description.isEmpty() ? null : description);
        entry.stock = buf.readInt();
        int available = buf.readInt();
        entry.restockSeconds = buf.readInt();

        int waiting = buf.readVarInt();
        entry.restorePool(OWN_POOL, available,
                waiting <= 0 ? 0L : System.currentTimeMillis() + waiting * 1000L);
        entry.scope = StockScope.byId(buf.readUtf());
        entry.access.read(buf);
        return entry;
    }
}
