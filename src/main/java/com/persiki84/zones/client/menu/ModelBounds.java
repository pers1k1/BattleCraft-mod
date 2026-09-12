package com.persiki84.zones.client.menu;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ModelBounds {
    private static final int VERTEX_STRIDE = 8;
    private static final float UNKNOWN = 1.0f;

    private static final Map<BakedModel, Float> spans = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create(42L);

    private ModelBounds() {}

    static float longestSide(BakedModel model) {
        Float known = spans.get(model);
        if (known != null) return known;

        float span = measure(model);
        spans.put(model, span);
        return span;
    }

    private static float measure(BakedModel model) {
        float span = 0.0f;
        span = Math.max(span, widest(model.getQuads(null, null, RANDOM)));
        for (Direction side : Direction.values()) {
            span = Math.max(span, widest(model.getQuads(null, side, RANDOM)));
        }
        return span <= 0.0f ? UNKNOWN : span;
    }

    private static float widest(List<BakedQuad> quads) {
        float lowest = Float.MAX_VALUE;
        float highest = -Float.MAX_VALUE;

        for (BakedQuad quad : quads) {
            int[] vertices = quad.getVertices();
            for (int offset = 0; offset + 2 < vertices.length; offset += VERTEX_STRIDE) {
                lowest = Math.min(lowest, lowest(vertices, offset));
                highest = Math.max(highest, highest(vertices, offset));
            }
        }
        return highest <= lowest ? 0.0f : highest - lowest;
    }

    private static float lowest(int[] vertices, int offset) {
        return Math.min(Float.intBitsToFloat(vertices[offset]),
                Math.min(Float.intBitsToFloat(vertices[offset + 1]), Float.intBitsToFloat(vertices[offset + 2])));
    }

    private static float highest(int[] vertices, int offset) {
        return Math.max(Float.intBitsToFloat(vertices[offset]),
                Math.max(Float.intBitsToFloat(vertices[offset + 1]), Float.intBitsToFloat(vertices[offset + 2])));
    }
}
