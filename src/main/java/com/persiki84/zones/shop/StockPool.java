package com.persiki84.zones.shop;

public final class StockPool {
    private int available;
    private long readyAt;

    public StockPool(int available, long readyAt) {
        this.available = available;
        this.readyAt = readyAt;
    }

    public int available() {
        return available;
    }

    public long readyAt() {
        return readyAt;
    }

    public boolean full(int stock) {
        return available >= stock && readyAt <= 0L;
    }

    public int take(int units, int restockSeconds, long now) {
        int taken = Math.min(units, Math.max(0, available));
        available -= taken;
        if (available <= 0 && restockSeconds > 0) {
            readyAt = now + restockSeconds * 1000L;
        }
        return taken;
    }

    public boolean refillIfDue(int stock, long now) {
        if (available > 0 || readyAt <= 0L || now < readyAt) return false;

        available = stock;
        readyAt = 0L;
        return true;
    }

    public int remainingSeconds(long now) {
        if (readyAt <= 0L || now >= readyAt) return 0;
        return (int) Math.ceil((readyAt - now) / 1000.0);
    }
}
