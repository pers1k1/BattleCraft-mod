package com.persiki84.shared.client.ui;

// WHY: ячейки Вороного, а не лучи из точки удара: у настоящего скола рёбра сходятся по три под
// WHY: тупыми углами, а лучевая нарезка даёт концентрические дуги и читается как веер, не как стекло
public final class UiShards {
    public static final int MAX_CORNERS = 12;

    private static final float CELL_SHARE = 0.30f;
    private static final float JITTER = 0.34f;
    private static final int COLUMNS_MIN = 3;
    private static final int ROWS_MIN = 2;
    private static final int COLUMNS_MAX = 12;
    private static final int ROWS_MAX = 8;
    private static final float SEAM_SHARE = 0.030f;
    private static final float DROP_SHARE = 0.05f;
    private static final float CRACK_UNTIL = 0.16f;
    private static final float SEAM_UNTIL = 0.30f;
    private static final float FLY_POWER = 1.7f;
    private static final float DRIFT_MIN = 0.13f;
    private static final float DRIFT_SPAN = 0.17f;
    private static final float SPIN_SPAN = 0.44f;
    private static final float SHADE_SPAN = 0.10f;
    private static final int HASH_MIX = 0x27D4EB2D;

    private final int count;
    private final float[] cornerX;
    private final float[] cornerY;
    private final int[] corners;
    private final float[] centreX;
    private final float[] centreY;
    private final float[] driftX;
    private final float[] driftY;
    private final float[] spin;
    private final float[] shade;
    private final float[] pace;
    private final float[] lag;

    private UiShards(int count) {
        this.count = count;
        this.cornerX = new float[count * MAX_CORNERS];
        this.cornerY = new float[count * MAX_CORNERS];
        this.corners = new int[count];
        this.centreX = new float[count];
        this.centreY = new float[count];
        this.driftX = new float[count];
        this.driftY = new float[count];
        this.spin = new float[count];
        this.shade = new float[count];
        this.pace = new float[count];
        this.lag = new float[count];
    }

    public static UiShards of(float left, float top, float width, float height, long seed) {
        return of(left, top, width, height, Math.min(width, height), seed);
    }

    // WHY: мера отделена от габарита: замощение обязано накрыть весь экран, чтобы шапка и кнопки за
    // WHY: пределами панели тоже раскалывались, а размер ячейки и разлёт остаются от размера панели
    public static UiShards of(float left, float top, float width, float height, float measure, long seed) {
        if (width <= 0.0f || height <= 0.0f || measure <= 0.0f) return null;

        float cell = measure * CELL_SHARE;
        int columns = clamp(Math.round(width / cell), COLUMNS_MIN, COLUMNS_MAX);
        int rows = clamp(Math.round(height / cell), ROWS_MIN, ROWS_MAX);
        UiShards shards = new UiShards(columns * rows);
        shards.seed(left, top, width, height, columns, rows, seed);
        shards.carve(left, top, width, height, measure);
        return shards;
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    private void seed(float left, float top, float width, float height,
                      int columns, int rows, long salt) {
        float stepX = width / columns;
        float stepY = height / rows;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int index = row * columns + column;
                centreX[index] = left + (column + 0.5f + noise(column, row, salt) * JITTER) * stepX;
                centreY[index] = top + (row + 0.5f + noise(column, row, salt + 91L) * JITTER) * stepY;
            }
        }
    }

    private static float noise(int x, int y, long salt) {
        int hash = (int) (x * 73856093L ^ y * 19349663L ^ salt * 83492791L);
        hash = (hash ^ (hash >>> 13)) * HASH_MIX;
        return ((hash >>> 16) & 0xFFFF) / 65536.0f - 0.5f;
    }

    private void carve(float left, float top, float width, float height, float reach) {
        float[] polyX = new float[MAX_CORNERS * 2];
        float[] polyY = new float[MAX_CORNERS * 2];
        float pivotX = left + width / 2.0f;
        float pivotY = top + height / 2.0f;
        for (int index = 0; index < count; index++) {
            int size = rect(polyX, polyY, left, top, width, height);
            size = clipAll(index, polyX, polyY, size);
            store(index, polyX, polyY, size, pivotX, pivotY, reach);
        }
    }

    private static int rect(float[] polyX, float[] polyY,
                            float left, float top, float width, float height) {
        polyX[0] = left;
        polyY[0] = top;
        polyX[1] = left + width;
        polyY[1] = top;
        polyX[2] = left + width;
        polyY[2] = top + height;
        polyX[3] = left;
        polyY[3] = top + height;
        return 4;
    }

    private int clipAll(int owner, float[] polyX, float[] polyY, int size) {
        for (int other = 0; other < count && size >= 3; other++) {
            if (other == owner) continue;
            size = clip(polyX, polyY, size,
                    centreX[other] - centreX[owner], centreY[other] - centreY[owner],
                    (centreX[owner] + centreX[other]) / 2.0f, (centreY[owner] + centreY[other]) / 2.0f);
        }
        return size;
    }

    // WHY: отсечение полуплоскостью по срединным перпендикулярам даёт ровно замощение без щелей:
    // WHY: соседние ячейки делят одно ребро, поэтому между осколками не остаётся непокрытых полос
    private static int clip(float[] polyX, float[] polyY, int size,
                            float normalX, float normalY, float midX, float midY) {
        float[] outX = new float[MAX_CORNERS * 2];
        float[] outY = new float[MAX_CORNERS * 2];
        int written = 0;
        for (int index = 0; index < size && written < outX.length - 1; index++) {
            int next = (index + 1) % size;
            float here = side(polyX[index], polyY[index], normalX, normalY, midX, midY);
            float there = side(polyX[next], polyY[next], normalX, normalY, midX, midY);
            if (here <= 0.0f) {
                outX[written] = polyX[index];
                outY[written] = polyY[index];
                written++;
            }
            if (here * there >= 0.0f) continue;

            float ratio = here / (here - there);
            outX[written] = polyX[index] + (polyX[next] - polyX[index]) * ratio;
            outY[written] = polyY[index] + (polyY[next] - polyY[index]) * ratio;
            written++;
        }
        System.arraycopy(outX, 0, polyX, 0, written);
        System.arraycopy(outY, 0, polyY, 0, written);
        return written;
    }

    private static float side(float x, float y, float normalX, float normalY, float midX, float midY) {
        return (x - midX) * normalX + (y - midY) * normalY;
    }

    private void store(int index, float[] polyX, float[] polyY, int size,
                       float pivotX, float pivotY, float reach) {
        int kept = Math.min(size, MAX_CORNERS);
        corners[index] = kept < 3 ? 0 : kept;
        if (corners[index] == 0) return;

        int base = index * MAX_CORNERS;
        float sumX = 0.0f;
        float sumY = 0.0f;
        for (int corner = 0; corner < kept; corner++) {
            cornerX[base + corner] = polyX[corner];
            cornerY[base + corner] = polyY[corner];
            sumX += polyX[corner];
            sumY += polyY[corner];
        }
        centreX[index] = sumX / kept;
        centreY[index] = sumY / kept;
        motion(index, pivotX, pivotY, reach);
    }

    private void motion(int index, float pivotX, float pivotY, float reach) {
        float awayX = centreX[index] - pivotX;
        float awayY = centreY[index] - pivotY;
        float length = (float) Math.sqrt(awayX * awayX + awayY * awayY);
        float unitX = length > 0.001f ? awayX / length : 0.0f;
        float unitY = length > 0.001f ? awayY / length : -1.0f;

        float roll = noise((int) centreX[index], (int) centreY[index], 17L) + 0.5f;
        float thrust = reach * (DRIFT_MIN + DRIFT_SPAN * roll);
        pace[index] = thrust;
        driftX[index] = unitX * thrust;
        driftY[index] = unitY * thrust;
        spin[index] = noise((int) centreY[index], (int) centreX[index], 53L) * SPIN_SPAN;
        shade[index] = 1.0f - (roll * SHADE_SPAN);
        lag[index] = roll;
    }

    public int count() {
        return count;
    }

    public int corners(int index) {
        return corners[index];
    }

    public float cornerX(int index, int corner) {
        return cornerX[index * MAX_CORNERS + corner];
    }

    public float cornerY(int index, int corner) {
        return cornerY[index * MAX_CORNERS + corner];
    }

    public float shade(int index) {
        return shade[index];
    }

    public float lag(int index) {
        return lag[index];
    }

    public static float crackUntil() {
        return CRACK_UNTIL;
    }

    public static float seamUntil() {
        return SEAM_UNTIL;
    }

    public static float thrown(float phase) {
        return (float) Math.pow(UiAnim.clamp01((phase - CRACK_UNTIL) / (1.0f - CRACK_UNTIL)), FLY_POWER);
    }

    // WHY: полёт осколка спрашивают трое: сам раскол, копия кадра в сцене и снимок подложки, и все
    // WHY: трое обязаны получить одно и то же место, иначе мир за стеклом разъедется с осколком
    public Flight flight(int index, float phase) {
        float seam = UiAnim.clamp01(phase / SEAM_UNTIL);
        float thrown = thrown(phase);
        float turn = spin[index] * thrown;
        return new Flight(centreX[index], centreY[index], 1.0f - SEAM_SHARE * seam,
                (float) Math.cos(turn), (float) Math.sin(turn),
                driftX[index] * thrown,
                driftY[index] * thrown + pace[index] * DROP_SHARE * thrown * thrown);
    }

    // WHY: швы раскрываются сжатием ячейки к своему центру, а не разлётом: у образца на этой стадии
    // WHY: текст внутри осколка стоит на месте, двигаются только границы
    public record Flight(float centreX, float centreY, float scale, float cos, float sin,
                         float shiftX, float shiftY) {
        public float x(float pointX, float pointY) {
            float localX = (pointX - centreX) * scale;
            float localY = (pointY - centreY) * scale;
            return centreX + localX * cos - localY * sin + shiftX;
        }

        public float y(float pointX, float pointY) {
            float localX = (pointX - centreX) * scale;
            float localY = (pointY - centreY) * scale;
            return centreY + localX * sin + localY * cos + shiftY;
        }
    }
}
