package com.persiki84.shared.client.ui;

// WHY: точное евклидово поле расстояний (Felzenszwalb, Huttenlocher, «Distance Transforms of Sampled
// WHY: Functions»): квадрат расстояния считается двумя проходами одномерной нижней огибающей парабол,
// WHY: по столбцам и по строкам, за линейное время от числа пикселей
final class UiDistanceField {
    private static final float FAR = 1.0e20f;

    private UiDistanceField() {}

    static float[] squaredTo(boolean[] feature, boolean wanted, int width, int height) {
        float[] grid = new float[width * height];
        for (int index = 0; index < grid.length; index++) {
            grid[index] = feature[index] == wanted ? 0.0f : FAR;
        }
        Envelope envelope = new Envelope(Math.max(width, height));
        for (int column = 0; column < width; column++) {
            envelope.pass(grid, column, width, height);
        }
        for (int row = 0; row < height; row++) {
            envelope.pass(grid, row * width, 1, width);
        }
        return grid;
    }

    static boolean[] eroded(boolean[] shape, int width, int height, float radius) {
        float[] reach = squaredTo(shape, false, width, height);
        boolean[] kept = new boolean[shape.length];
        float limit = radius * radius;
        for (int index = 0; index < shape.length; index++) {
            kept[index] = shape[index] && reach[index] > limit;
        }
        return kept;
    }

    static boolean[] dilated(boolean[] shape, int width, int height, float radius) {
        float[] reach = squaredTo(shape, true, width, height);
        boolean[] grown = new boolean[shape.length];
        float limit = radius * radius;
        for (int index = 0; index < shape.length; index++) {
            grown[index] = reach[index] <= limit;
        }
        return grown;
    }

    static float[] signed(boolean[] shape, int width, int height) {
        float[] toOutside = squaredTo(shape, false, width, height);
        float[] toInside = squaredTo(shape, true, width, height);
        float[] field = new float[shape.length];
        for (int index = 0; index < shape.length; index++) {
            field[index] = shape[index]
                    ? (float) Math.sqrt(toOutside[index]) - 0.5f
                    : 0.5f - (float) Math.sqrt(toInside[index]);
        }
        return field;
    }

    private static final class Envelope {
        private final float[] source;
        private final float[] result;
        private final int[] apex;
        private final float[] bound;

        Envelope(int length) {
            source = new float[length];
            result = new float[length];
            apex = new int[length];
            bound = new float[length + 1];
        }

        void pass(float[] grid, int start, int stride, int length) {
            for (int step = 0; step < length; step++) {
                source[step] = grid[start + step * stride];
            }
            build(length);
            fill(length);
            for (int step = 0; step < length; step++) {
                grid[start + step * stride] = result[step];
            }
        }

        private void build(int length) {
            int top = 0;
            apex[0] = 0;
            bound[0] = -FAR;
            bound[1] = FAR;
            for (int point = 1; point < length; point++) {
                float crossing = crossing(point, apex[top]);
                while (crossing <= bound[top]) {
                    top--;
                    crossing = crossing(point, apex[top]);
                }
                top++;
                apex[top] = point;
                bound[top] = crossing;
                bound[top + 1] = FAR;
            }
        }

        private void fill(int length) {
            int top = 0;
            for (int point = 0; point < length; point++) {
                while (bound[top + 1] < point) top++;
                float offset = point - apex[top];
                result[point] = offset * offset + source[apex[top]];
            }
        }

        private float crossing(int point, int other) {
            double rise = (source[point] + (double) point * point) - (source[other] + (double) other * other);
            return (float) (rise / (2.0 * point - 2.0 * other));
        }
    }
}
