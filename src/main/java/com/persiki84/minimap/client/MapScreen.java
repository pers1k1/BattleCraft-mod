package com.persiki84.minimap.client;

import com.persiki84.zones.Zone;
import com.persiki84.zones.ZoneType;
import com.persiki84.zones.client.ClientMarkData;
import com.persiki84.zones.client.ClientZoneData;
import com.persiki84.zones.client.render.ZoneColors;
import com.persiki84.zones.mark.MapMark;
import com.persiki84.capturepoints.client.ClientCaptureData;
import com.persiki84.minimap.network.MapMarkerSyncPacket;
import com.persiki84.minimap.network.MapMarkerUpdatePacket;
import com.persiki84.minimap.network.MapTeleportPacket;
import com.persiki84.zones.mark.MarkPalette;
import com.persiki84.zones.network.MarkEditPacket;
import com.persiki84.minimap.network.MapWorldMarkerSyncPacket;
import com.persiki84.minimap.network.PacketHandler;
import com.persiki84.minimap.network.PlayerPositionSyncPacket;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiButton;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiBackdrop;
import com.persiki84.shared.client.ui.UiPalette;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MapScreen extends Screen {
    private static final int OPERATOR_LEVEL = 2;
    private static final float MARKER_CLEARANCE = 2.0f;
    private static final float LABEL_SCALE = 0.85f;
    private static final float READOUT_MIN = 74.0f;
    private static final int GRID_COLOR = 0x1AFFFFFF;
    private static final double MARKER_GRAB_PIXELS = 20.0;
    private static final float BASE_RADIUS = 5.0f;
    private static final float MARKER_DOT = 2.0f;
    private static final Component BASE_LABEL = Component.translatable("zones.marker.base");
    private static final long DOUBLE_CLICK_MS = 400L;

    private final MapActionMenu actions = new MapActionMenu();
    private final MapTextPrompt prompt = new MapTextPrompt();
    private String hoveredLabel;
    private String armedLabel;
    private String clickedLabel;
    private long clickedAt;
    private boolean movedLabel;
    private double mapX = 0;
    private double mapZ = 0;
    private float zoom = 1.0f;
    private float targetZoom = 1.0f;
    private boolean clickedWidget = false;
    private boolean hasDragged = false;

    public MapScreen() {
        super(Component.translatable("minimap.screen.title"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mapX == 0 && mapZ == 0) {
            mapX = mc.player.getX();
            mapZ = mc.player.getZ();
        }
        this.targetZoom = this.zoom;

        this.addRenderableWidget(new UiButton(this.width - 108, 10, 98, 18,
                Component.translatable("minimap.button.settings"),
                button -> Minecraft.getInstance().setScreen(new MinimapSettingsScreen(this))));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        applyZoom(mouseX, mouseY, centerX, centerY);

        UiRender.rect(guiGraphics, 0, 0, this.width, this.height, UiPalette.panelDeep());
        guiGraphics.enableScissor(0, 0, this.width, this.height);

        MapTextureManager.renderMap(guiGraphics, mapX, mapZ, zoom, centerX, centerY, 0.0f, 0.0f, this.width, this.height);
        renderGrid(guiGraphics, centerX, centerY);
        renderPoints(guiGraphics, centerX, centerY);
        renderMarkers(guiGraphics, centerX, centerY);
        renderLabels(guiGraphics, mouseX, mouseY, centerX, centerY);

        guiGraphics.disableScissor();

        UiBackdrop.capture();
        renderChrome(guiGraphics, mouseX, mouseY, centerX, centerY);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        actions.render(guiGraphics, this.font, mouseX, mouseY);
        prompt.render(guiGraphics, this.font, this.width, this.height);
    }

    private void applyZoom(int mouseX, int mouseY, int centerX, int centerY) {
        if (Math.abs(this.targetZoom - this.zoom) <= 0.001f) {
            this.zoom = this.targetZoom;
            return;
        }

        double anchorMouseX = (mouseX >= 0 && mouseX <= this.width) ? mouseX : centerX;
        double anchorMouseY = (mouseY >= 0 && mouseY <= this.height) ? mouseY : centerY;
        double anchorX = mapX + (anchorMouseX - centerX) / zoom;
        double anchorZ = mapZ + (anchorMouseY - centerY) / zoom;

        this.zoom = UiAnim.approach(this.zoom, this.targetZoom, 14.0f, UiFrame.delta());

        mapX = anchorX - (anchorMouseX - centerX) / this.zoom;
        mapZ = anchorZ - (anchorMouseY - centerY) / this.zoom;
    }

    private void renderGrid(GuiGraphics guiGraphics, int centerX, int centerY) {
        double maxWorldX = mapX + (this.width - centerX) / zoom;
        long firstGridX = (long) Math.ceil((mapX - centerX / zoom) / 100.0) * 100;
        for (double wx = firstGridX; wx <= maxWorldX; wx += 100.0) {
            double sx = centerX + (wx - mapX) * zoom;
            guiGraphics.fill((int) sx, 0, (int) sx + 1, this.height, GRID_COLOR);
        }

        double maxWorldZ = mapZ + (this.height - centerY) / zoom;
        long firstGridZ = (long) Math.ceil((mapZ - centerY / zoom) / 100.0) * 100;
        for (double wz = firstGridZ; wz <= maxWorldZ; wz += 100.0) {
            double sy = centerY + (wz - mapZ) * zoom;
            guiGraphics.fill(0, (int) sy, this.width, (int) sy + 1, GRID_COLOR);
        }
    }

    private void renderBases(GuiGraphics guiGraphics, int centerX, int centerY) {
        for (Zone zone : ClientZoneData.all()) {
            if (zone.type() != ZoneType.BASE || !ZoneColors.visibleToOwnTeam(zone)) continue;

            renderBaseMarker(guiGraphics, zone.area().centerX(), zone.area().centerZ(), centerX, centerY,
                    0xFF000000 | ZoneColors.packed(zone));
        }
    }

    private void renderBaseMarker(GuiGraphics guiGraphics, double bx, double bz, int cx, int cy, int color) {
        float screenX = (float) (cx + (bx - mapX) * zoom);
        float screenY = (float) (cy + (bz - mapZ) * zoom);
        int shown = UiTheme.muted(color, 0.2f);

        UiRender.ring(guiGraphics, screenX, screenY, BASE_RADIUS, 3.4f, 1.0f, UiPalette.panelDeep());
        UiRender.ring(guiGraphics, screenX, screenY, BASE_RADIUS, 2.0f, 1.0f, shown);
        UiRender.dot(guiGraphics, screenX, screenY, 1.4f, shown);
        labelAbove(guiGraphics, BASE_LABEL.getString(), screenX, screenY, BASE_RADIUS + 1.7f, UiAccent.text());
    }

    private void renderMarkers(GuiGraphics guiGraphics, int centerX, int centerY) {
        Minecraft mc = Minecraft.getInstance();
        renderBases(guiGraphics, centerX, centerY);

        for (MapWorldMarkerSyncPacket.WorldMarker marker : ClientMapData.getWorldMarkers()) {
            renderWorldMarker(guiGraphics, marker.x, marker.z, centerX, centerY, Component.translatable(marker.key).getString());
        }

        int pinged = 0;
        for (MapMark mark : ClientMarkData.all()) {
            if (mc.level == null || !mc.level.dimension().location().equals(mark.dimension())) continue;
            if (MapLabels.isLabel(mark)) continue;
            pinged++;
            renderMarker(guiGraphics, mark.position().getX() + 0.5, mark.position().getZ() + 0.5,
                    centerX, centerY, mark.color(), mark.label(),
                    MarkerPings.age(MarkerKeys.of(mark), mark.position().getX(), mark.position().getZ()));
        }

        for (MapMarkerSyncPacket.MarkerData marker : ClientMapData.getMarkers()) {
            if (mc.player == null) break;
            if (!ClientMapData.showOtherMarkers && !marker.playerName.equals(mc.player.getScoreboardName())) continue;
            int color = marker.isTeam ? MapRenderUtil.getPlayerTeamColor(marker.playerName) : UiAccent.color();
            String name = marker.isTeam ? marker.playerName : Component.translatable("minimap.label.personal_marker").getString();
            pinged++;
            renderMarker(guiGraphics, marker.x, marker.z, centerX, centerY, color, name,
                    MarkerPings.age(MarkerKeys.of(marker), marker.x, marker.z));
        }
        MarkerPings.sweep(pinged);

        renderPlayers(guiGraphics, mc, centerX, centerY);
    }

    private void renderPlayers(GuiGraphics guiGraphics, Minecraft mc, int centerX, int centerY) {
        for (PlayerPositionSyncPacket.PlayerPos other : ClientMapData.getPlayers()) {
            if (mc.player != null && other.playerId.equals(mc.player.getUUID())) continue;
            renderPlayerDot(guiGraphics, other.x, other.z, other.yRot, centerX, centerY,
                    MapRenderUtil.getPlayerTeamColor(other.playerName), other.playerName);
        }

        if (mc.player == null) return;
        renderPlayerDot(guiGraphics, mc.player.getX(), mc.player.getZ(), mc.player.getYRot(), centerX, centerY,
                UiAccent.color(), Component.translatable("minimap.label.you").getString());
    }

    private void renderChrome(GuiGraphics guiGraphics, int mouseX, int mouseY, int centerX, int centerY) {
        double worldX = mapX + (mouseX - centerX) / zoom;
        double worldZ = mapZ + (mouseY - centerY) / zoom;

        float scale = UiRender.crisp(guiGraphics, LABEL_SCALE);
        Component readout = Component.literal(Mth.floor(worldX) + "   " + Mth.floor(worldZ));
        float readoutWidth = UiMetrics.cardWidth(Math.max(READOUT_MIN, UiRender.widthLabel(this.font, readout) * scale));
        float rowHeight = UiMetrics.cardHeight(this.font.lineHeight * scale);
        UiGlass.panel(guiGraphics, UiMetrics.MARGIN, UiMetrics.MARGIN, readoutWidth, rowHeight,
                UiMetrics.radius(rowHeight), 1.0f);
        UiRender.labelCentered(guiGraphics, this.font, readout, UiMetrics.MARGIN + readoutWidth / 2.0f,
                UiRender.centerY(UiMetrics.MARGIN, rowHeight, scale), scale, UiAccent.text());

        Component hint = Component.translatable(hintKey());
        float hintWidth = UiMetrics.cardWidth(UiRender.width(this.font, hint) * scale);
        float hintY = this.height - UiMetrics.MARGIN_WIDE - rowHeight;
        UiGlass.panel(guiGraphics, (this.width - hintWidth) / 2.0f, hintY, hintWidth, rowHeight,
                UiMetrics.radius(rowHeight), 1.0f);
        UiRender.textCentered(guiGraphics, this.font, hint, this.width / 2.0f,
                UiRender.centerY(hintY, rowHeight, scale), scale, UiAccent.text(), false);
    }

    private void labelAbove(GuiGraphics guiGraphics, String value, float centerX,
                            float markerY, float markerRadius, int color) {
        float scale = UiRender.crisp(guiGraphics, LABEL_SCALE);
        float height = this.font.lineHeight * scale + UiMetrics.GAP;
        label(guiGraphics, value, centerX, markerY - markerRadius - MARKER_CLEARANCE - height, color);
    }

    private void label(GuiGraphics guiGraphics, String value, float centerX, float y, int color) {
        float scale = UiRender.crisp(guiGraphics, LABEL_SCALE);
        float width = UiRender.widthLabel(this.font, value) * scale + UiMetrics.GAP_WIDE * 2.0f;
        float height = this.font.lineHeight * scale + UiMetrics.GAP;
        float plateY = y - UiMetrics.GAP_TIGHT;
        UiGlass.panel(guiGraphics, centerX - width / 2.0f, plateY, width, height, height / 2.0f, 1.0f);
        UiRender.labelCentered(guiGraphics, this.font, value, centerX,
                UiRender.centerY(plateY, height, scale), scale, color);
    }

    private void renderPoints(GuiGraphics guiGraphics, int centerX, int centerY) {
        for (Map.Entry<String, String> entry : ClientCaptureData.getAllPointOwners().entrySet()) {
            BlockPos pos = ClientCaptureData.getPointPosition(entry.getKey());
            if (pos != null) {
                renderPointMarker(guiGraphics, pos.getX(), pos.getZ(), centerX, centerY,
                        MapRenderUtil.getTeamColor(entry.getValue()), entry.getKey());
            }
        }

        if (ClientCaptureData.areAllPointsCapturedBySameTeam()) {
            for (Map.Entry<String, String> entry : ClientCaptureData.getAllFinalPointOwners().entrySet()) {
                BlockPos pos = ClientCaptureData.getFinalPointPosition(entry.getKey());
                if (pos != null) {
                    renderPointMarker(guiGraphics, pos.getX(), pos.getZ(), centerX, centerY, UiAccent.color(), entry.getKey());
                }
            }
        }
    }

    private void renderPlayerDot(GuiGraphics guiGraphics, double px, double pz, float yRot, int cx, int cy, int color, String name) {
        float screenX = (float) (cx + (px - mapX) * zoom);
        float screenY = (float) (cy + (pz - mapZ) * zoom);

        UiRender.arrow(guiGraphics, screenX, screenY, yRot, 6.0f, UiTheme.muted(color, 0.2f), UiPalette.panelDeep());
        labelAbove(guiGraphics, name, screenX, screenY, 6.0f, UiAccent.text());
    }

    private void renderMarker(GuiGraphics guiGraphics, double mx, double mz, int cx, int cy, int color,
                              String name, float age) {
        float screenX = (float) (cx + (mx - mapX) * zoom);
        float screenY = (float) (cy + (mz - mapZ) * zoom);
        int shown = UiTheme.muted(color, 0.2f);
        float pop = MarkerPings.pop(age);

        MarkerPings.ripple(guiGraphics, screenX, screenY, MARKER_DOT, shown, age);
        UiRender.dot(guiGraphics, screenX, screenY, MARKER_DOT * pop + 0.6f, UiPalette.panelDeep());
        UiRender.dot(guiGraphics, screenX, screenY, MARKER_DOT * pop, shown);
        labelAbove(guiGraphics, name, screenX, screenY, 2.6f, UiAccent.text());
    }

    private void renderWorldMarker(GuiGraphics guiGraphics, double mx, double mz, int cx, int cy, String name) {
        float screenX = (float) (cx + (mx - mapX) * zoom);
        float screenY = (float) (cy + (mz - mapZ) * zoom);

        UiRender.panel(guiGraphics, screenX - 3.6f, screenY - 3.6f, 7.2f, 7.2f, 2.2f, UiPalette.panelDeep());
        UiRender.panel(guiGraphics, screenX - 3.0f, screenY - 3.0f, 6.0f, 6.0f, 2.0f, UiAccent.color());
        labelAbove(guiGraphics, name, screenX, screenY, 3.6f, UiAccent.text());
    }

    private void renderPointMarker(GuiGraphics guiGraphics, double px, double pz, int cx, int cy, int color, String name) {
        float screenX = (float) (cx + (px - mapX) * zoom);
        float screenY = (float) (cy + (pz - mapZ) * zoom);

        UiRender.panel(guiGraphics, screenX - 4.1f, screenY - 4.1f, 8.2f, 8.2f, 2.7f, UiPalette.panelDeep());
        UiRender.panel(guiGraphics, screenX - 3.5f, screenY - 3.5f, 7.0f, 7.0f, 2.5f, UiTheme.muted(color, 0.25f));
        labelAbove(guiGraphics, name, screenX, screenY, 4.1f, UiAccent.text());
    }


    private String hintKey() {
        if (!canEdit()) return "minimap.screen.hint";
        if (armedLabel != null) return "minimap.screen.hint.dragging";
        return hoveredLabel == null ? "minimap.screen.hint.operator" : "minimap.screen.hint.label";
    }

    private void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY, int centerX, int centerY) {
        hoveredLabel = canEdit() && !actions.open() && !prompt.open()
                ? MapLabels.under(this.font, mouseX, mouseY, mapX, mapZ, zoom, centerX, centerY)
                : null;
        MapLabels.render(guiGraphics, this.font, mapX, mapZ, zoom, centerX, centerY, hoveredLabel, armedLabel);
    }

    private boolean canEdit() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.hasPermissions(OPERATOR_LEVEL);
    }

    private double worldXAt(double mouseX) {
        return mapX + (mouseX - this.width / 2.0) / zoom;
    }

    private double worldZAt(double mouseY) {
        return mapZ + (mouseY - this.height / 2.0) / zoom;
    }

    // WHY: на колесе раньше был один телепорт, и просто добавить к нему надписи некуда: окно
    // WHY: выбора спрашивает, что сделать с точкой, по которой щёлкнули
    private void openActions(double mouseX, double mouseY) {
        String id = MapLabels.under(this.font, mouseX, mouseY, mapX, mapZ, zoom,
                this.width / 2, this.height / 2);
        List<MapActionMenu.Item> items = new ArrayList<>();
        items.add(MapActionMenu.Item.of("minimap.map.action.teleport",
                () -> teleportTo(worldXAt(mouseX), worldZAt(mouseY))));

        if (id == null) {
            items.add(MapActionMenu.Item.of("minimap.map.action.new_label",
                    () -> askNewLabel(worldXAt(mouseX), worldZAt(mouseY))));
        } else {
            addLabelActions(items, id, mouseX, mouseY);
        }
        actions.show(this.font, mouseX, mouseY, this.width, this.height, items);
    }

    private void addLabelActions(List<MapActionMenu.Item> items, String id, double mouseX, double mouseY) {
        MapMark mark = ClientMarkData.byId(id);
        if (mark == null) return;

        items.add(MapActionMenu.Item.of("minimap.map.action.edit_line", () -> askLine(id)));
        items.add(MapActionMenu.Item.of("minimap.map.action.add_line", () -> askNewLine(id)));
        if (mark.lines().size() > 1) {
            items.add(MapActionMenu.Item.of("minimap.map.action.remove_line",
                    () -> send(MarkEditPacket.Action.REMOVE_LINE, id, 0, 0,
                            mark.lines().size() - 1, 0, "")));
        }
        items.add(MapActionMenu.Item.of("minimap.map.action.color", () -> openColors(id, mouseX, mouseY)));
        items.add(MapActionMenu.Item.of("minimap.map.action.delete",
                () -> send(MarkEditPacket.Action.DELETE, id, 0, 0, 0, 0, "")));
    }

    private void openColors(String id, double mouseX, double mouseY) {
        List<MapActionMenu.Item> items = new ArrayList<>();
        for (int index = 0; index < MarkPalette.COLORS.length; index++) {
            int color = MarkPalette.COLORS[index];
            items.add(MapActionMenu.Item.colored(MarkPalette.name(index), color,
                    () -> send(MarkEditPacket.Action.COLOR, id, 0, 0, 0, color, "")));
        }
        actions.show(this.font, mouseX, mouseY, this.width, this.height, items);
    }

    private void askNewLabel(double worldX, double worldZ) {
        prompt.ask(Component.translatable("minimap.map.prompt.new_label"), "",
                text -> send(MarkEditPacket.Action.CREATE, "", Mth.floor(worldX), Mth.floor(worldZ),
                        0, MapMark.DEFAULT_COLOR, text));
    }

    // WHY: текущий текст берётся в момент открытия окна, а не в момент сборки меню: снимок
    // WHY: меток приходит заново каждую правку, и объект из старого снимка уже не тот
    private void askLine(String id) {
        MapMark mark = ClientMarkData.byId(id);
        if (mark == null) return;

        prompt.ask(Component.translatable("minimap.map.prompt.edit_line"), mark.lines().get(0),
                text -> send(MarkEditPacket.Action.SET_LINE, id, 0, 0, 0, 0, text));
    }

    private void askNewLine(String id) {
        prompt.ask(Component.translatable("minimap.map.prompt.add_line"), "",
                text -> send(MarkEditPacket.Action.ADD_LINE, id, 0, 0, 0, 0, text));
    }

    private static void send(MarkEditPacket.Action action, String id, int x, int z,
                             int line, int color, String text) {
        com.persiki84.zones.network.PacketHandler.INSTANCE.sendToServer(
                new MarkEditPacket(action, id, x, z, line, color, text));
    }

    private void teleportTo(double worldX, double worldZ) {
        PacketHandler.INSTANCE.sendToServer(new MapTeleportPacket(worldX, worldZ));
        onClose();
    }

    // WHY: перетаскивание разрешено только после двойного щелчка: одиночный по карте ставит
    // WHY: личную метку, и надпись уезжала бы от любого промаха мимо неё
    private boolean armLabel(String id) {
        long now = System.currentTimeMillis();
        boolean again = id.equals(clickedLabel) && now - clickedAt < DOUBLE_CLICK_MS;
        clickedLabel = id;
        clickedAt = now;

        if (!again) return false;
        armedLabel = id;
        movedLabel = false;
        return true;
    }

    private void dragLabel(double mouseX, double mouseY) {
        movedLabel = true;
        MapLabels.hold(armedLabel, Mth.floor(worldXAt(mouseX)), Mth.floor(worldZAt(mouseY)));
    }

    private void dropLabel(double mouseX, double mouseY) {
        if (movedLabel) {
            int x = Mth.floor(worldXAt(mouseX));
            int z = Mth.floor(worldZAt(mouseY));
            MapLabels.hold(armedLabel, x, z);
            send(MarkEditPacket.Action.MOVE, armedLabel, x, z, 0, 0, "");
            armedLabel = null;
        }
        movedLabel = false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (armedLabel != null) {
                dragLabel(mouseX, mouseY);
                return true;
            }
            hasDragged = true;
            mapX -= dragX / zoom;
            mapZ -= dragY / zoom;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (actions.open() || prompt.open()) return true;

        targetZoom = (float) Math.max(0.1, Math.min(10.0, targetZoom + delta * 0.15 * targetZoom));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (prompt.open()) return true;
        if (actions.open()) {
            actions.click(mouseX, mouseY);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            if (canEdit()) openActions(mouseX, mouseY);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && grabLabel(mouseX, mouseY)) return true;

        armedLabel = null;
        hasDragged = false;
        clickedWidget = super.mouseClicked(mouseX, mouseY, button);
        return true;
    }

    private boolean grabLabel(double mouseX, double mouseY) {
        if (!canEdit()) return false;

        String id = MapLabels.under(this.font, mouseX, mouseY, mapX, mapZ, zoom,
                this.width / 2, this.height / 2);
        if (id == null) return false;

        hasDragged = false;
        clickedWidget = false;
        if (!id.equals(armedLabel)) armedLabel = null;
        armLabel(id);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (prompt.open() || actions.open()) return true;

        if (armedLabel != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dropLabel(mouseX, mouseY);
            return true;
        }

        if (clickedWidget) {
            clickedWidget = false;
            return super.mouseReleased(mouseX, mouseY, button);
        }

        if (!hasDragged && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            toggleMarker(mouseX, mouseY, button == GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            return true;
        }

        hasDragged = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void toggleMarker(double mouseX, double mouseY, boolean isTeam) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double clickWorldX = mapX + (mouseX - this.width / 2.0) / zoom;
        double clickWorldZ = mapZ + (mouseY - this.height / 2.0) / zoom;
        if (markerUnderCursor(mc, clickWorldX, clickWorldZ, isTeam)) {
            PacketHandler.INSTANCE.sendToServer(new MapMarkerUpdatePacket(0, 0, true, isTeam));
            return;
        }
        PacketHandler.INSTANCE.sendToServer(new MapMarkerUpdatePacket(clickWorldX, clickWorldZ, false, isTeam));
    }

    private boolean markerUnderCursor(Minecraft mc, double worldX, double worldZ, boolean isTeam) {
        for (MapMarkerSyncPacket.MarkerData marker : ClientMapData.getMarkers()) {
            if (!marker.playerId.equals(mc.player.getUUID()) || marker.isTeam != isTeam) continue;
            double dx = marker.x - worldX;
            double dz = marker.z - worldZ;
            double reach = MARKER_GRAB_PIXELS / zoom;
            return dx * dx + dz * dz < reach * reach;
        }
        return false;
    }

    @Override
    public boolean charTyped(char symbol, int modifiers) {
        if (prompt.open()) return prompt.charTyped(symbol);
        return super.charTyped(symbol, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (prompt.open()) return prompt.keyPressed(key);
        if (key != GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(key, scan, modifiers);

        if (actions.open()) {
            actions.close();
            return true;
        }
        if (armedLabel != null) {
            armedLabel = null;
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
