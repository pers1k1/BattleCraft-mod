package com.persiki84.zones.shop;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public final class ShopEntry {
    public static final int UNLIMITED = -1;

    private final String id;
    private final ItemStack stack;
    private final ShopAccess access = new ShopAccess();
    private int price;
    private String description;
    private int stock = UNLIMITED;
    private int available = UNLIMITED;
    private int restockSeconds;
    private long readyAt;

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
    public int available() { return available; }
    public int restockSeconds() { return restockSeconds; }
    public long readyAt() { return readyAt; }

    public void setPrice(int price) { this.price = price; }
    public void setDescription(String description) { this.description = description; }

    public int bundle() { return Math.max(1, stack.getCount()); }

    public int availableItems() { return limited() ? available * bundle() : UNLIMITED; }

    public boolean limited() { return stock != UNLIMITED; }

    public boolean soldOut() { return limited() && available <= 0; }

    public void setStock(int stock, int restockSeconds) {
        this.stock = stock < 0 ? UNLIMITED : stock;
        this.restockSeconds = Math.max(0, restockSeconds);
        this.available = this.stock;
        this.readyAt = 0L;
    }

    public void setRestockSeconds(int seconds) {
        this.restockSeconds = Math.max(0, seconds);
    }

    public void restore(int available, long readyAt) {
        this.available = limited() ? Math.min(available, stock) : UNLIMITED;
        this.readyAt = readyAt;
    }

    public int take(int units, long now) {
        if (!limited()) return units;

        int taken = Math.min(units, Math.max(0, available));
        available -= taken;
        if (available <= 0 && restockSeconds > 0) {
            readyAt = now + restockSeconds * 1000L;
        }
        return taken;
    }

    public boolean restockIfDue(long now) {
        if (!limited() || available > 0 || readyAt <= 0L || now < readyAt) return false;

        available = stock;
        readyAt = 0L;
        return true;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeItem(stack);
        buf.writeInt(price);
        buf.writeUtf(description == null ? "" : description);
        buf.writeInt(stock);
        buf.writeInt(available);
        buf.writeInt(restockSeconds);
        buf.writeVarInt(remainingSeconds(System.currentTimeMillis()));
        access.write(buf);
    }

    public static ShopEntry read(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        ItemStack stack = buf.readItem();
        int price = buf.readInt();
        String description = buf.readUtf();

        ShopEntry entry = new ShopEntry(id, stack, price, description.isEmpty() ? null : description);
        entry.stock = buf.readInt();
        entry.available = buf.readInt();
        entry.restockSeconds = buf.readInt();

        int waiting = buf.readVarInt();
        entry.readyAt = waiting <= 0 ? 0L : System.currentTimeMillis() + waiting * 1000L;
        entry.access.read(buf);
        return entry;
    }

    public int remainingSeconds(long now) {
        if (readyAt <= 0L || now >= readyAt) return 0;
        return (int) Math.ceil((readyAt - now) / 1000.0);
    }
}
