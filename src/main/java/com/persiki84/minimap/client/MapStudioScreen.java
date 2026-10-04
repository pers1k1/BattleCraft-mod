package com.persiki84.minimap.client;

import com.persiki84.shared.client.menu.studio.StudioMenu;
import com.persiki84.shared.client.menu.studio.StudioScreen;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiMetrics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// WHY: редактор вещей, у которых есть место в мире: в центре карта, вещь выбирается и тащится
// WHY: прямо на ней, правая кнопка открывает её действия, а по пустому месту - создание там же.
// WHY: Перенос держится на карте до прихода снимка, иначе вещь прыгала бы назад на полсекунды
public abstract class MapStudioScreen extends StudioScreen {
    private static final long PENDING_MS = 2500L;
    private static final int DRAG_START = 4;
    private static final float HINT_HEIGHT = 12.0f;
    private static final float GHOST_PULSE_MS = 1400.0f;

    protected final MapCanvas map = new MapCanvas();
    private final MapMotion motion = new MapMotion();
    private final Map<String, Moved> moved = new HashMap<>();
    private MapThing pressed;
    private boolean carrying;
    private boolean panning;
    private boolean placing;
    private double pressX;
    private double pressY;
    private double grabX;
    private double grabZ;
    private int carryX;
    private int carryZ;
    private String hovered;
    private String focusedKey;
    private boolean mapWasShown;

    private record Moved(int x, int z, long at) {}

    protected MapStudioScreen(Component title, Screen parent) {
        super(title, parent);
    }

    protected abstract List<MapThing> things();

    protected abstract String chosenThing();

    protected abstract void chooseThing(String key);

    protected abstract void moveThing(MapThing thing, int x, int z);

    protected List<StudioMenu.Action> thingActions(MapThing thing) {
        return List.of();
    }

    protected List<StudioMenu.Action> groundActions(int x, int z) {
        return List.of();
    }

    protected MapThing ghost() {
        return null;
    }

    protected void placeGhost(int x, int z) {
    }

    protected Component mapHint() {
        return Component.translatable("studio.map.hint");
    }

    protected boolean showsMap() {
        return true;
    }

    protected boolean movable(MapThing thing) {
        return true;
    }

    protected boolean labelsAlways() {
        return true;
    }

    @Override
    protected boolean gridCanvas() {
        if (!showsMap()) mapWasShown = false;
        return !showsMap();
    }

    @Override
    protected final boolean paintsCanvas() {
        return showsMap();
    }

    protected void centerOnPlayer() {
        map.centerOnPlayer();
    }

    private List<MapThing> shownThings() {
        List<MapThing> shown = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (MapThing thing : things()) {
            Moved move = moved.get(thing.key());
            if (move != null && (now - move.at() > PENDING_MS || thing.x() == move.x() && thing.z() == move.z())) {
                moved.remove(thing.key());
                move = null;
            }
            shown.add(move == null ? thing : thing.at(move.x(), move.z()));
        }
        return shown;
    }

    @Override
    protected void renderPainted(GuiGraphics graphics, float left, float top, float width, float height,
                                 int mouseX, int mouseY) {
        float pad = UiMetrics.PAD;
        map.place(left + pad, top + pad, width - pad * 2.0f, height - pad * 3.0f - HINT_HEIGHT);
        if (!mapWasShown) {
            mapWasShown = true;
            map.reveal();
            motion.reset();
        }
        List<MapThing> shown = shownThings();
        followChoice(shown);
        hovered = carrying || panning ? hovered : keyOf(pick(shown, mouseX, mouseY));
        String held = carrying && pressed != null ? pressed.key() : null;
        List<MapMotion.Drawn> drawn = motion.advance(shown, held, carryX, carryZ);
        map.render(graphics, canvas -> paintShapes(canvas, drawn), canvas -> paintLabels(canvas, drawn));
        line(graphics, mapHint(), left + pad, width - pad * 2.0f, top + height - pad - HINT_HEIGHT + 1.0f);
    }

    // WHY: выбор в списке слева ведёт карту к вещи, а выбор щелчком на карте нет: она и так под
    // WHY: курсором, и переезд вида из-под руки сбивал бы перенос
    private void followChoice(List<MapThing> shown) {
        String chosen = chosenThing();
        if (!map.centered()) {
            MapThing first = find(shown, chosen);
            if (first != null && first.here()) map.glideTo(first.centerX(), first.centerZ(), 0.0);
            else map.centerOnPlayer();
            focusedKey = chosen;
            return;
        }
        if (chosen == null || chosen.equals(focusedKey)) return;
        focusedKey = chosen;
        MapThing target = find(shown, chosen);
        if (target != null && target.here()) map.glideTo(target.centerX(), target.centerZ(), 0.0);
    }

    private static MapThing find(List<MapThing> shown, String key) {
        if (key == null) return null;
        for (MapThing thing : shown) {
            if (thing.key().equals(key)) return thing;
        }
        return null;
    }

    private static String keyOf(MapThing thing) {
        return thing == null ? null : thing.key();
    }

    private float glowOf(String key) {
        float chosen = map.lit(key, key.equals(chosenThing()));
        return Math.max(chosen, key.equals(hovered) ? 0.45f : 0.0f);
    }

    private void paintShapes(GuiGraphics graphics, List<MapMotion.Drawn> drawn) {
        for (MapMotion.Drawn item : drawn) {
            MapThing thing = item.thing();
            float glow = glowOf(thing.key());
            if (thing.size() > MapThing.PIN_ONLY) {
                map.area(graphics, thing.shape(), item.x(), item.z(), thing.size(), thing.color(), glow, item.presence());
            }
            map.pin(graphics, item.x(), item.z(), thing.color(), glow, item.presence());
        }
        paintGhost(graphics);
    }

    private void paintGhost(GuiGraphics graphics) {
        MapThing ghost = ghost();
        if (ghost == null) return;
        float pulse = UiAnim.pulse(GHOST_PULSE_MS, 0.55f, 1.0f);
        if (ghost.size() > MapThing.PIN_ONLY) {
            map.area(graphics, ghost.shape(), ghost.centerX(), ghost.centerZ(), ghost.size(), UiAccent.color(), 1.0f,
                    pulse);
        }
        map.pin(graphics, ghost.centerX(), ghost.centerZ(), UiAccent.color(), 1.0f, pulse);
    }

    // WHY: подпись у сотен блоков карьера слилась бы в кашу, поэтому экран может показывать её
    // WHY: только у выбранной и наведённой вещи; переход подписи идёт той же подсветкой
    private void paintLabels(GuiGraphics graphics, List<MapMotion.Drawn> drawn) {
        String chosen = chosenThing();
        for (MapMotion.Drawn item : drawn) {
            String key = item.thing().key();
            boolean lit = key.equals(chosen) || key.equals(hovered);
            float glow = map.lit("label:" + key, lit);
            float presence = labelsAlways() ? item.presence() : item.presence() * glow;
            map.label(graphics, this.font, item.thing().name(), item.x(), item.z(), glow, presence);
        }
        MapThing ghost = ghost();
        if (ghost != null) map.label(graphics, this.font, ghost.name(), ghost.centerX(), ghost.centerZ(), 1.0f, 1.0f);
    }

    // WHY: булавка важнее области: мелкая точка внутри большой зоны иначе не бралась бы мышью вовсе,
    // WHY: а из перекрытых областей берётся самая маленькая, то есть та, что лежит сверху по смыслу
    private MapThing pick(List<MapThing> shown, double mouseX, double mouseY) {
        if (!map.over(mouseX, mouseY)) return null;
        for (MapThing thing : shown) {
            if (thing.here() && map.near(mouseX, mouseY, thing.centerX(), thing.centerZ())) return thing;
        }
        double worldX = map.worldX(mouseX);
        double worldZ = map.worldZ(mouseY);
        MapThing best = null;
        for (MapThing thing : shown) {
            if (!thing.here() || !thing.covers(worldX, worldZ)) continue;
            if (best == null || thing.size() < best.size()) best = thing;
        }
        return best;
    }

    @Override
    protected boolean pressPainted(double mouseX, double mouseY) {
        if (!map.over(mouseX, mouseY)) return false;
        pressX = mouseX;
        pressY = mouseY;
        MapThing hit = pick(shownThings(), mouseX, mouseY);
        if (ghost() != null && hit == null) {
            placing = true;
            placeGhost(blockX(mouseX), blockZ(mouseY));
            return true;
        }
        if (hit == null) {
            panning = true;
            return true;
        }
        select(hit);
        pressed = hit;
        grabX = map.worldX(mouseX) - hit.centerX();
        grabZ = map.worldZ(mouseY) - hit.centerZ();
        return true;
    }

    private void select(MapThing hit) {
        if (hit.key().equals(chosenThing())) return;
        flushCommits();
        marks.clear();
        focusedKey = hit.key();
        chooseThing(hit.key());
        layout();
    }

    private int blockX(double mouseX) {
        return (int) Math.floor(map.worldX(mouseX));
    }

    private int blockZ(double mouseY) {
        return (int) Math.floor(map.worldZ(mouseY));
    }

    @Override
    protected void dragPainted(double mouseX, double mouseY, double dragX, double dragY) {
        if (placing) {
            placeGhost(blockX(mouseX), blockZ(mouseY));
            return;
        }
        if (panning) {
            map.pan(dragX, dragY);
            return;
        }
        if (pressed == null || !movable(pressed)) return;
        carrying = carrying || Math.abs(mouseX - pressX) > DRAG_START || Math.abs(mouseY - pressY) > DRAG_START;
        if (!carrying) return;
        carryX = (int) Math.floor(map.worldX(mouseX) - grabX);
        carryZ = (int) Math.floor(map.worldZ(mouseY) - grabZ);
    }

    @Override
    protected void releasePainted(double mouseX, double mouseY) {
        if (carrying && pressed != null && (carryX != pressed.x() || carryZ != pressed.z())) {
            moved.put(pressed.key(), new Moved(carryX, carryZ, System.currentTimeMillis()));
            moveThing(pressed, carryX, carryZ);
        }
        pressed = null;
        carrying = false;
        panning = false;
        placing = false;
    }

    @Override
    protected void scrollPainted(double mouseX, double mouseY, double amount) {
        if (map.over(mouseX, mouseY)) map.zoomAt(mouseX, mouseY, amount);
    }

    @Override
    protected List<StudioMenu.Action> paintedActions(double mouseX, double mouseY) {
        if (!map.over(mouseX, mouseY)) return List.of();
        MapThing hit = pick(shownThings(), mouseX, mouseY);
        if (hit == null) return groundActions(blockX(mouseX), blockZ(mouseY));
        select(hit);
        return thingActions(hit);
    }
}
