package com.persiki84.shared.client.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

// WHY: цифры часов рисуются полем расстояний, а не шрифтом интерфейса: на любом масштабе кромка
// WHY: ровно в пиксель, а рельеф стекла берётся из самого поля. Глиф заливается в SUPERSAMPLE раз
// WHY: крупнее ячейки, углы скругляются открытием (сжатие и рост на радиус, как у SF Pro Rounded),
// WHY: поле усредняется по блоку ячейки. У Inter в hmtx нулевой левый отступ, поэтому глиф ставится
// WHY: по центру своей ширины, и центр - середина между рамкой и центром масс краски: иначе
// WHY: у единицы с флажком ствол уезжает к соседу справа. Двоеточие - две окружности тоньше штриха
// WHY: цифр: у Inter точки квадратные и жирные
public final class UiDigitSheet {
    public static final String GLYPHS = "0123456789:";
    public static final int FIGURE_TEXELS = 128;
    public static final int SPREAD_TEXELS = 24;

    // WHY: поле хранится в 16 битах: в 8 битах шаг глубины 0.19 текселя, и по нему шли ступени
    // WHY: кромки и шум нормали, видимый как зерно вдоль контура и на фаске
    private static final float DEPTH_LEVELS = 65535.0f;

    private static final int SUPERSAMPLE = 4;
    private static final float ROUNDING = 0.04f;
    private static final float SOFTENING = 0.02f;
    private static final float TRACKING = 0.04f;
    private static final float OPTICAL_CENTRING = 0.5f;
    private static final float COLON_DOT = 0.14f;
    private static final float COLON_SPREAD = 0.23f;
    private static final float COLON_ADVANCE = 0.26f;

    private final int width;
    private final int height;
    private final short[] field;
    private final int[] cellLeft;
    private final int[] cellWidth;
    private final float[] penOffset;
    private final float[] advance;
    private final float topOffset;
    private final float texelsPerFigure;

    private UiDigitSheet(int width, int height, short[] field, int[] cellLeft, int[] cellWidth, float[] penOffset,
                         float[] advance, float topOffset, float texelsPerFigure) {
        this.width = width;
        this.height = height;
        this.field = field;
        this.cellLeft = cellLeft;
        this.cellWidth = cellWidth;
        this.penOffset = penOffset;
        this.advance = advance;
        this.topOffset = topOffset;
        this.texelsPerFigure = texelsPerFigure;
    }

    public static UiDigitSheet build(Font typeface) {
        Layout layout = Layout.of(typeface);
        int count = GLYPHS.length();
        short[][] fields = new short[count][];
        int[] cellLeft = new int[count];
        int[] cellWidth = new int[count];
        float[] penOffset = new float[count];
        float[] advance = new float[count];
        int cursor = 0;
        for (int index = 0; index < count; index++) {
            Cell cell = layout.cell(GLYPHS.charAt(index));
            fields[index] = cell.texels();
            cellLeft[index] = cursor;
            cellWidth[index] = cell.width();
            penOffset[index] = cell.penOffset();
            advance[index] = cell.advance();
            cursor += cell.width();
        }
        short[] packed = pack(fields, cellLeft, cellWidth, cursor, layout.rows());
        return new UiDigitSheet(cursor, layout.rows(), packed, cellLeft, cellWidth, penOffset, advance,
                layout.topOffset(), layout.texelsPerFigure());
    }

    private static short[] pack(short[][] fields, int[] cellLeft, int[] cellWidth, int width, int height) {
        short[] packed = new short[width * height];
        for (int index = 0; index < fields.length; index++) {
            for (int row = 0; row < height; row++) {
                System.arraycopy(fields[index], row * cellWidth[index], packed, row * width + cellLeft[index],
                        cellWidth[index]);
            }
        }
        return packed;
    }

    public static int indexOf(char glyph) {
        return GLYPHS.indexOf(glyph);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    short[] field() {
        return field;
    }

    public int cellLeft(int index) {
        return cellLeft[index];
    }

    public int cellWidth(int index) {
        return cellWidth[index];
    }

    public float penOffset(int index) {
        return penOffset[index];
    }

    public float advance(int index) {
        return advance[index];
    }

    public float topOffset() {
        return topOffset;
    }

    public float texelsPerFigure() {
        return texelsPerFigure;
    }

    public float cellSpan(int index) {
        return cellWidth[index] / texelsPerFigure;
    }

    public float rowSpan() {
        return height / texelsPerFigure;
    }

    private record Cell(short[] texels, int width, float penOffset, float advance) {}

    private static final class Layout {
        private final Font font;
        private final FontRenderContext context;
        private final float figureTop;
        private final float figurePixels;
        private int windowTop;
        private int rows;

        private Layout(Font font, FontRenderContext context, float figureTop, float figurePixels) {
            this.font = font;
            this.context = context;
            this.figureTop = figureTop;
            this.figurePixels = figurePixels;
        }

        static Layout of(Font typeface) {
            FontRenderContext context = new FontRenderContext(new AffineTransform(), true, true);
            Rectangle2D probe = outline(typeface.deriveFont(1000.0f), context, '1').getBounds2D();
            float size = 1000.0f * FIGURE_TEXELS * SUPERSAMPLE / (float) probe.getHeight();
            Font font = typeface.deriveFont(size);
            Rectangle2D one = outline(font, context, '1').getBounds2D();
            Layout layout = new Layout(font, context, (float) one.getMinY(), (float) one.getHeight());
            layout.frame();
            return layout;
        }

        private void frame() {
            float top = Float.MAX_VALUE;
            float bottom = -Float.MAX_VALUE;
            for (int index = 0; index < GLYPHS.length(); index++) {
                Rectangle2D bounds = shape(GLYPHS.charAt(index)).getBounds2D();
                top = Math.min(top, (float) bounds.getMinY());
                bottom = Math.max(bottom, (float) bounds.getMaxY());
            }
            int margin = SPREAD_TEXELS * SUPERSAMPLE;
            windowTop = (int) Math.floor((top - margin) / SUPERSAMPLE) * SUPERSAMPLE;
            int windowBottom = (int) Math.ceil((bottom + margin) / SUPERSAMPLE) * SUPERSAMPLE;
            rows = (windowBottom - windowTop) / SUPERSAMPLE;
        }

        private static Shape outline(Font font, FontRenderContext context, char glyph) {
            GlyphVector vector = font.createGlyphVector(context, String.valueOf(glyph));
            return vector.getOutline(0.0f, 0.0f);
        }

        private Shape shape(char glyph) {
            if (glyph != ':') return outline(font, context, glyph);
            float diameter = COLON_DOT * figurePixels;
            float middle = figureTop + figurePixels * 0.5f;
            float reach = COLON_SPREAD * figurePixels;
            Area dots = new Area(new Ellipse2D.Float(0.0f, middle - reach - diameter / 2.0f, diameter, diameter));
            dots.add(new Area(new Ellipse2D.Float(0.0f, middle + reach - diameter / 2.0f, diameter, diameter)));
            return dots;
        }

        private float advance(char glyph) {
            if (glyph == ':') return COLON_ADVANCE * figurePixels;
            float natural = font.createGlyphVector(context, String.valueOf(glyph)).getGlyphMetrics(0).getAdvance();
            return natural - TRACKING * figurePixels;
        }

        int rows() {
            return rows;
        }

        float topOffset() {
            return (windowTop - figureTop) / figurePixels;
        }

        float texelsPerFigure() {
            return figurePixels / SUPERSAMPLE;
        }

        Cell cell(char glyph) {
            Shape shape = shape(glyph);
            Rectangle ink = shape.getBounds();
            int margin = SPREAD_TEXELS * SUPERSAMPLE;
            int left = (int) Math.floor((ink.x - margin) / (float) SUPERSAMPLE) * SUPERSAMPLE;
            int right = (int) Math.ceil((ink.x + ink.width + margin) / (float) SUPERSAMPLE) * SUPERSAMPLE;
            int columns = (right - left) / SUPERSAMPLE;
            int lines = rows * SUPERSAMPLE;
            boolean[] fine = rounded(fill(shape, left, windowTop, right - left, lines), right - left, lines);
            short[] texels = coarse(UiDistanceField.signed(fine, right - left, lines), columns, rows);
            float advance = advance(glyph);
            Rectangle2D exact = shape.getBounds2D();
            float middle = (float) exact.getCenterX() - left;
            float optical = middle + (inkCentre(fine, right - left) - middle) * OPTICAL_CENTRING;
            return new Cell(texels, columns, (advance / 2.0f - optical) / figurePixels, advance / figurePixels);
        }

        private static float inkCentre(boolean[] fine, int columns) {
            double sum = 0.0;
            long count = 0L;
            for (int index = 0; index < fine.length; index++) {
                if (!fine[index]) continue;
                sum += index % columns + 0.5;
                count++;
            }
            return count == 0L ? columns / 2.0f : (float) (sum / count);
        }

        private boolean[] rounded(boolean[] shape, int columns, int lines) {
            float opening = ROUNDING * figurePixels;
            float closing = SOFTENING * figurePixels;
            boolean[] opened = UiDistanceField.dilated(UiDistanceField.eroded(shape, columns, lines, opening),
                    columns, lines, opening);
            return UiDistanceField.eroded(UiDistanceField.dilated(opened, columns, lines, closing),
                    columns, lines, closing);
        }

        private static boolean[] fill(Shape shape, int left, int top, int columns, int lines) {
            BufferedImage canvas = new BufferedImage(columns, lines, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D brush = canvas.createGraphics();
            brush.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            brush.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            brush.setColor(Color.WHITE);
            brush.translate(-left, -top);
            brush.fill(shape);
            brush.dispose();
            byte[] pixels = ((DataBufferByte) canvas.getRaster().getDataBuffer()).getData();
            boolean[] inside = new boolean[pixels.length];
            for (int index = 0; index < pixels.length; index++) {
                inside[index] = (pixels[index] & 0xFF) >= 128;
            }
            return inside;
        }

        private static short[] coarse(float[] fine, int columns, int lines) {
            int fineWidth = columns * SUPERSAMPLE;
            short[] texels = new short[columns * lines];
            float scale = 1.0f / (SUPERSAMPLE * SUPERSAMPLE * SUPERSAMPLE);
            for (int row = 0; row < lines; row++) {
                for (int column = 0; column < columns; column++) {
                    float sum = 0.0f;
                    for (int dy = 0; dy < SUPERSAMPLE; dy++) {
                        int base = (row * SUPERSAMPLE + dy) * fineWidth + column * SUPERSAMPLE;
                        for (int dx = 0; dx < SUPERSAMPLE; dx++) sum += fine[base + dx];
                    }
                    texels[row * columns + column] = encode(sum * scale);
                }
            }
            return texels;
        }

        private static short encode(float distance) {
            float level = 0.5f + distance / (2.0f * SPREAD_TEXELS);
            return (short) Math.round(Math.max(0.0f, Math.min(1.0f, level)) * DEPTH_LEVELS);
        }
    }
}
