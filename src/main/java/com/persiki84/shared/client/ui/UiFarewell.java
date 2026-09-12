package com.persiki84.shared.client.ui;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class UiFarewell {
    private static final float MAX_STEP = 1.0f / 15.0f;
    private static final float BURN_CHARGE = 0.55f;
    private static final int TRAILS = 3;

    private static final Departure[] trail = fill();
    private static final int[] queue = new int[TRAILS];
    private static int living;
    private static long stamp = -1L;
    private static boolean reported;

    private static Departure opened;

    private UiFarewell() {}

    private static Departure[] fill() {
        Departure[] pool = new Departure[TRAILS];
        for (int i = 0; i < pool.length; i++) pool[i] = new Departure();
        return pool;
    }

    public static void begin(UiEmber source, UiPlane anchor, float sweepTop, float sweepSpan,
                             float centreX, float centreY, UiMotionSet motion) {
        opened = null;
        if (!UiReveal.enabled() || UiStage.texture() == 0) return;

        Departure departure = claim();
        departure.arm(source, anchor, sweepTop, sweepSpan, centreX, centreY, motion);
        opened = departure;
    }

    // WHY: мировая панель висит там, где её оставили, и таких может догорать несколько сразу;
    // WHY: плоский уход рисуется на месте нового экрана, поэтому второму такому места нет
    private static Departure claim() {
        for (int i = living - 1; i >= 0; i--) {
            if (trail[i].flat()) drop(i);
        }
        if (living == trail.length) drop(0);
        return trail[living++];
    }

    public static boolean burning() {
        return living > 0;
    }

    // WHY: экран снимается на первом же кадре ухода, и его removed() чистит то, что живой перерисовке
    // WHY: ещё нужно: у списка миров так пропадали карточки. Чистка откладывается до конца ухода
    public static boolean defer(UiEmber owner, Runnable disposal) {
        for (int i = living - 1; i >= 0; i--) {
            if (trail[i].owns(owner)) {
                trail[i].hold(disposal);
                return true;
            }
        }
        return false;
    }

    // WHY: первый кадр ухода рисуется тем же путём, что и остальные: у раскола нулевая фаза это
    // WHY: целая панель, и отдельная отрисовка композитным квадом дала бы чужой кадр на один тик
    public static void first(GuiGraphics graphics, float width, float height) {
        Departure departure = opened;
        opened = null;
        if (departure == null) return;

        departure.mark();
        departure.compose(graphics, width, height, 0.0f);
    }

    public static void render(GuiGraphics graphics, float width, float height, float partialTick) {
        if (living == 0) return;

        age();
        order();
        for (int i = 0; i < living; i++) {
            trail[queue[i]].paint(graphics, width, height, partialTick);
        }
        sweep();
    }

    // WHY: панели висят на разном удалении, и рисовать их по возрасту значит класть ближнюю под
    // WHY: дальнюю; глубину композит не пишет намеренно, поэтому порядок задаётся здесь
    private static void order() {
        for (int i = 0; i < living; i++) queue[i] = i;
        for (int i = 1; i < living; i++) {
            int moved = queue[i];
            float away = trail[moved].away();
            int at = i - 1;
            while (at >= 0 && trail[queue[at]].away() < away) {
                queue[at + 1] = queue[at];
                at--;
            }
            queue[at + 1] = moved;
        }
    }

    private static void age() {
        long frame = UiFrame.frame();
        if (frame == stamp) return;

        stamp = frame;
        float step = Math.min(MAX_STEP, UiFrame.delta());
        for (int i = 0; i < living; i++) trail[i].age(step);
    }

    private static void sweep() {
        for (int i = living - 1; i >= 0; i--) {
            if (!trail[i].alive()) drop(i);
        }
    }

    // WHY: уход возвращается в хвост пула, а не теряется: в кадре нет ни одной аллокации
    private static void drop(int index) {
        Departure gone = trail[index];
        gone.quit();
        System.arraycopy(trail, index + 1, trail, index, living - index - 1);
        trail[living - 1] = gone;
        living--;
    }

    private static void report(Throwable error) {
        if (reported) return;
        reported = true;
        LogUtils.getLogger().warn("[battlecraft] live burn off: {}", String.valueOf(error));
    }

    private static final class Departure {
        private static final float IDLE = -1.0f;

        private float elapsed = IDLE;
        private long painted = -1L;
        private float top;
        private float span;
        private float originX;
        private float originY;
        private float burn;
        private UiPlane plane;
        private UiEmber ember;
        private UiMotionSet set = UiMotionSet.IGNITE;
        private UiShards shards;
        private long grain;
        private Runnable pending;

        private void arm(UiEmber source, UiPlane anchor, float sweepTop, float sweepSpan,
                         float centreX, float centreY, UiMotionSet motion) {
            set = motion == UiMotionSet.GLASS && UiShatter.ready() ? UiMotionSet.GLASS : UiMotionSet.IGNITE;
            grain = System.nanoTime();
            elapsed = 0.0f;
            burn = set.leaveSeconds(Minecraft.getInstance().level != null);
            painted = -1L;
            top = sweepTop;
            span = sweepSpan;
            originX = centreX;
            originY = centreY;
            shards = null;
            plane = anchor;
            ember = source;
        }

        private boolean alive() {
            return elapsed >= 0.0f && elapsed < burn;
        }

        private boolean flat() {
            return plane == null;
        }

        private float away() {
            return plane == null ? 0.0f : plane.away();
        }

        private boolean owns(UiEmber owner) {
            return ember != null && ember == owner;
        }

        private void hold(Runnable disposal) {
            pending = disposal;
        }

        private void age(float step) {
            if (elapsed < 0.0f) return;
            elapsed += step;
        }

        private void mark() {
            painted = UiFrame.frame();
        }

        private void paint(GuiGraphics graphics, float width, float height, float partialTick) {
            long frame = UiFrame.frame();
            if (!alive() || frame == painted) return;
            painted = frame;

            float phase = UiAnim.clamp01(elapsed / burn);
            field(width, height);
            if (!refresh(graphics, partialTick, phase, width, height)) return;
            if (anchored(graphics, width, height, phase)) return;
            compose(graphics, width, height, phase);
        }

        // WHY: содержимое рисуется плоско, ровно как у открытого экрана, поэтому стекло берёт живой кадр,
        // WHY: а в мировую позу его выносит композитный квад, который сэмплит сцену по координате снятия.
        // WHY: расколу этого мало: осколок не только висит в мире, он ещё и улетает со своего места,
        // WHY: поэтому и копия кадра в сцене, и снимок подложки переносятся через поле осколков -
        // WHY: мир за стеклом приходит оттуда, куда ячейка улетела, а не оттуда, где она была
        private boolean refresh(GuiGraphics graphics, float partialTick, float phase,
                                float width, float height) {
            if (ember == null) return spent();

            graphics.flush();
            UiPlane carrier = carried();
            UiPlane.sampling(carrier);
            UiBackdrop.scatter(carrier, shards, phase, width, height);
            if (!UiStage.beginBurn(carrier, shards, phase, width, height)) {
                UiPlane.sampling(null);
                UiBackdrop.plain();
                return spent();
            }

            UiBackdrop.hold();
            try {
                ember.paintEmber(graphics, partialTick);
            } catch (Throwable error) {
                ember = null;
                report(error);
            } finally {
                UiBackdrop.resume();
                UiPlane.sampling(null);
                UiBackdrop.plain();
                graphics.flush();
                UiStage.end();
            }
            return true;
        }

        // WHY: без своей перерисовки уход рисовал бы чужое содержимое стадии: у соседнего ухода или
        // WHY: у открытого экрана, и в мире висела бы панель с пустым или подменённым нутром
        private boolean spent() {
            elapsed = burn;
            return false;
        }

        // WHY: замощение нужно уже перерисовке, а не только композиту: и копия кадра в сцене, и
        // WHY: снимок подложки переносятся через то же поле осколков
        private void field(float width, float height) {
            if (set != UiMotionSet.GLASS || shards != null) return;
            shards = UiShards.of(0.0f, 0.0f, width, height, Math.min(width, span * height), grain);
        }

        private UiPlane carried() {
            return plane != null && plane.advance() ? plane : null;
        }

        private boolean anchored(GuiGraphics graphics, float width, float height, float phase) {
            if (plane == null || !plane.bind(graphics)) return false;

            try {
                compose(graphics, width, height, phase);
            } finally {
                plane.release(graphics);
            }
            return true;
        }

        private void compose(GuiGraphics graphics, float width, float height, float phase) {
            if (set == UiMotionSet.GLASS) {
                field(width, height);
                UiShatter.draw(graphics, width, height, UiStage.texture(), shards, phase);
                return;
            }
            UiAssemble.draw(graphics, width, height, UiStage.texture(), phase, elapsed, UiReveal.BURN,
                    top * height, span * height, originX, originY, BURN_CHARGE);
        }

        private void quit() {
            elapsed = IDLE;
            plane = null;
            ember = null;
            shards = null;

            Runnable disposal = pending;
            pending = null;
            if (disposal != null) disposal.run();
        }
    }
}
