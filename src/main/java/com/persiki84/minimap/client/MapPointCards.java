package com.persiki84.minimap.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

// WHY: окно у точки под курсором одно; ушедшее с точки догорает своим ходом, пока окно новой точки
// WHY: уже приходит, поэтому уходящие живут отдельным списком до конца своей анимации
final class MapPointCards {
    private final List<MapPointCard> leaving = new ArrayList<>();
    @Nullable
    private MapPointCard current;

    void hover(Font font, @Nullable String name, boolean last, double worldX, double worldZ) {
        if (current != null && (!current.name().equals(name) || current.stale())) release();
        if (name == null) return;
        if (current == null) current = new MapPointCard(font, name, last, worldX, worldZ);
    }

    // WHY: и уходящие окна едут за своей точкой, пока карту тянут
    void follow(double mapX, double mapZ, double zoom, int centerX, int centerY) {
        for (int index = 0; index < leaving.size(); index++) {
            leaving.get(index).follow(mapX, mapZ, zoom, centerX, centerY);
        }
        if (current != null) current.follow(mapX, mapZ, zoom, centerX, centerY);
    }

    void release() {
        if (current == null) return;
        current.beginClose();
        leaving.add(current);
        current = null;
    }

    void render(GuiGraphics graphics, float width, float height) {
        for (int index = leaving.size() - 1; index >= 0; index--) {
            if (leaving.get(index).gone()) leaving.remove(index);
        }
        for (int index = 0; index < leaving.size(); index++) {
            leaving.get(index).render(graphics, width, height);
        }
        if (current != null) current.render(graphics, width, height);
    }
}
