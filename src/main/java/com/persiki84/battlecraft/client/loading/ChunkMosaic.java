package com.persiki84.battlecraft.client.loading;

import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.world.level.chunk.ChunkStatus;

// WHY: клетки квадратные и с зазором намеренно: мозаика говорит на языке пиксельной головы,
// WHY: а скруглённые плитки рядом с ней читались бы как второй интерфейс на том же экране
public final class ChunkMosaic {
    private static final int GAP = 1;
    private static final int STRIDE_MAX = 6;
    private static final float WIDTH_SHARE = 0.55f;
    private static final float ROOM_SHARE = 0.92f;
    private static final float EMPTY_ALPHA = 0.14f;
    private static final float SEEDED_ALPHA = 0.55f;
    private static final float DONE_LIGHTEN = 0.45f;

    private static final Batch BATCH = new Batch();

    private ChunkMosaic() {}

    public static void paint(GuiGraphics graphics, StoringChunkProgressListener listener,
                             float centerX, float centerY, int width, int height) {
        int diameter = listener.getDiameter();
        if (diameter <= 0) return;

        int stride = strideFor(diameter, Math.min(width * WIDTH_SHARE, height * ROOM_SHARE));
        int cell = stride > GAP + 1 ? stride - GAP : stride;
        int span = diameter * stride - (stride - cell);
        BATCH.listener = listener;
        BATCH.diameter = diameter;
        BATCH.cell = cell;
        BATCH.stride = stride;
        BATCH.left = Math.round(centerX - span / 2.0f);
        BATCH.top = Math.round(centerY - span / 2.0f);
        graphics.drawManaged(BATCH.on(graphics));
    }

    // WHY: шаг считается от отданного места, а не от доли экрана: у прогрузки мира поперечник
    // WHY: доходит до полусотни чанков, и доля выносила мозаику за нижний край кадра
    private static int strideFor(int diameter, float limit) {
        int wanted = (int) (limit / diameter);
        return Math.max(1, Math.min(STRIDE_MAX, wanted));
    }

    private static int tone(ChunkStatus status) {
        if (status == null) return UiTheme.alpha(UiAccent.faint(), EMPTY_ALPHA);
        if (status == ChunkStatus.FULL) return UiTheme.lighten(UiAccent.color(), DONE_LIGHTEN);

        float grown = status.getIndex() / (float) ChunkStatus.FULL.getIndex();
        return UiTheme.alpha(UiTheme.mix(UiAccent.faint(), UiAccent.color(), grown), SEEDED_ALPHA);
    }

    private static final class Batch implements Runnable {
        private GuiGraphics graphics;
        private StoringChunkProgressListener listener;
        private int diameter;
        private int cell;
        private int stride;
        private int left;
        private int top;

        private Runnable on(GuiGraphics target) {
            graphics = target;
            return this;
        }

        @Override
        public void run() {
            for (int row = 0; row < diameter; row++) {
                for (int column = 0; column < diameter; column++) {
                    int x = left + column * stride;
                    int y = top + row * stride;
                    graphics.fill(x, y, x + cell, y + cell, tone(listener.getStatus(column, row)));
                }
            }
        }
    }
}
