package com.persiki84.sellmod.client;

import com.persiki84.shared.client.ui.Smooth;
import com.persiki84.shared.client.ui.UiAccent;
import com.persiki84.shared.client.ui.UiAnim;
import com.persiki84.shared.client.ui.UiFrame;
import com.persiki84.shared.client.ui.UiGlass;
import com.persiki84.shared.client.ui.UiMetrics;
import com.persiki84.shared.client.ui.UiRender;
import com.persiki84.shared.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

// WHY: получатель выбирается лицом, а не ником в списке: игроки в сети лежат стеклянными плитками,
// WHY: тиммейты первыми. Плитки едут на новое место при поиске и пересортировке, а не встают кадром,
// WHY: и новая плитка проявляется на месте с лёгким подъёмом, без раздувания соседей
final class TransferGrid {
    static final float TILE_WIDTH = 46.0f;
    static final float TILE_HEIGHT = 50.0f;
    static final float TILE_GAP = 5.0f;

    private static final float RADIUS = 7.0f;
    private static final float LEAD = 3.0f;
    private static final float RIM_REACH = 2.0f;
    private static final float FACE = 18.0f;
    private static final float FACE_TOP = 8.0f;
    private static final float FACE_FRAME = 1.5f;
    private static final float NAME_TOP = 31.0f;
    private static final float NAME_HEIGHT = 10.0f;
    private static final float NAME_SCALE = 0.58f;
    private static final float TAG_TOP = 41.0f;
    private static final float TAG_HEIGHT = 6.0f;
    private static final float TAG_SCALE = 0.46f;
    private static final float HOVER_SPEED = 16.0f;
    private static final float CHOICE_SPEED = 13.0f;
    private static final float GLIDE_SPEED = 17.0f;
    private static final float SCROLL_SPEED = 19.0f;
    private static final float APPEAR_MS = 200.0f;
    private static final float STAGGER_MS = 16.0f;
    private static final float APPEAR_LIFT = 5.0f;
    private static final float RIM = 1.2f;
    private static final float RIM_ALPHA = 0.75f;
    private static final int FACE_SHADE = 0x55000000;
    private static final Component MATE = Component.translatable("sellmod.transfer.mate");

    record Person(UUID id, String name, boolean mate) {}

    private static final class Slot {
        private final Smooth x = new Smooth(0.0f, GLIDE_SPEED);
        private final Smooth y = new Smooth(0.0f, GLIDE_SPEED);
        private final Smooth hover = new Smooth(0.0f, HOVER_SPEED);
        private final Smooth choice = new Smooth(0.0f, CHOICE_SPEED);
        private final Component name;
        private long bornAt;

        private Slot(String nick) {
            this.name = Component.literal(nick);
        }
    }

    private static final Comparator<Person> ORDER = Comparator.comparing((Person person) -> !person.mate())
            .thenComparing(Person::name, String.CASE_INSENSITIVE_ORDER);

    private final Map<UUID, Slot> slots = new HashMap<>();
    private final Smooth glide = new Smooth(0.0f, SCROLL_SPEED);
    private List<Person> everyone = List.of();
    private List<Person> shown = List.of();
    private String query = "";
    private long signature = Long.MIN_VALUE;
    private float left;
    private float top;
    private float width;
    private float height;
    private int scroll;
    private UUID chosen;

    void place(float gridLeft, float gridTop, float gridWidth, float gridHeight) {
        left = gridLeft;
        top = gridTop;
        width = gridWidth;
        height = gridHeight;
    }

    // WHY: список игроков пересобирается только когда сменился состав или команды: сверка идёт
    // WHY: подписью из идентификаторов и команд, а не сравнением списков
    void refresh() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || Minecraft.getInstance().level == null) return;

        Collection<PlayerInfo> online = connection.getOnlinePlayers();
        long seen = sign(online);
        if (seen == signature) return;

        signature = seen;
        everyone = collect(online);
        filter();
    }

    private static long sign(Collection<PlayerInfo> online) {
        long total = online.size();
        for (PlayerInfo info : online) {
            PlayerTeam team = info.getTeam();
            total = total * 31L + info.getProfile().getId().hashCode() + (team == null ? 0 : team.getName().hashCode());
        }
        return total;
    }

    private static List<Person> collect(Collection<PlayerInfo> online) {
        Minecraft minecraft = Minecraft.getInstance();
        UUID self = minecraft.player == null ? null : minecraft.player.getUUID();
        PlayerTeam own = minecraft.player == null ? null : minecraft.level.getScoreboard()
                .getPlayersTeam(minecraft.player.getScoreboardName());
        List<Person> people = new ArrayList<>();
        for (PlayerInfo info : online) {
            UUID id = info.getProfile().getId();
            if (id == null || id.equals(self)) continue;
            PlayerTeam team = info.getTeam();
            boolean mate = own != null && team != null && own.getName().equals(team.getName());
            people.add(new Person(id, info.getProfile().getName(), mate));
        }
        people.sort(ORDER);
        return people;
    }

    void search(String text) {
        query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        scroll = 0;
        filter();
    }

    private void filter() {
        List<Person> kept = new ArrayList<>();
        for (Person person : everyone) {
            if (query.isEmpty() || person.name().toLowerCase(Locale.ROOT).contains(query)) kept.add(person);
        }
        shown = kept;
        settleSlots();
        scroll = Math.min(scroll, lastRow());
    }

    // WHY: место плитки едет к новому номеру, а пришедшая плитка встаёт на своё место сразу и только
    // WHY: проявляется: выезд из угла сетки читался бы как перестановка, которой не было
    private void settleSlots() {
        long now = System.currentTimeMillis();
        int born = 0;
        for (int index = 0; index < shown.size(); index++) {
            Person person = shown.get(index);
            Slot slot = slots.get(person.id());
            if (slot == null) {
                slot = new Slot(person.name());
                slots.put(person.id(), slot);
                slot.x.snap(columnX(index));
                slot.y.snap(rowY(index));
                slot.bornAt = now + (long) (born++ * STAGGER_MS);
            }
        }
    }

    boolean empty() {
        return shown.isEmpty();
    }

    boolean searching() {
        return !query.isEmpty();
    }

    boolean nobodyOnline() {
        return everyone.isEmpty();
    }

    Person person(UUID id) {
        if (id == null) return null;
        for (int index = 0; index < everyone.size(); index++) {
            if (everyone.get(index).id().equals(id)) return everyone.get(index);
        }
        return null;
    }

    void choose(UUID id) {
        chosen = id;
    }

    private int columns() {
        return Math.max(1, (int) ((width - UiMetrics.PAD * 2.0f + TILE_GAP) / (TILE_WIDTH + TILE_GAP)));
    }

    private int rows() {
        return Math.max(1, (int) ((height - LEAD + TILE_GAP) / (TILE_HEIGHT + TILE_GAP)));
    }

    private int lastRow() {
        return Math.max(0, (shown.size() + columns() - 1) / columns() - rows());
    }

    private float columnX(int index) {
        float used = columns() * (TILE_WIDTH + TILE_GAP) - TILE_GAP;
        return (width - used) / 2.0f + index % columns() * (TILE_WIDTH + TILE_GAP);
    }

    private float rowY(int index) {
        return LEAD + index / columns() * (TILE_HEIGHT + TILE_GAP);
    }

    void scrollBy(int step) {
        scroll = Math.max(0, Math.min(lastRow(), scroll + step));
    }

    boolean over(double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    UUID pick(double mouseX, double mouseY) {
        if (!over(mouseX, mouseY)) return null;
        float offset = glide.get();
        for (Person person : shown) {
            Slot slot = slots.get(person.id());
            float x = left + slot.x.get();
            float y = top + slot.y.get() - offset;
            if (mouseX >= x && mouseX < x + TILE_WIDTH && mouseY >= y && mouseY < y + TILE_HEIGHT) return person.id();
        }
        return null;
    }

    // WHY: рамка выбранной плитки выходит за её край, а крайние плитки могут стоять вплотную к краю
    void render(GuiGraphics graphics, int mouseX, int mouseY) {
        float delta = UiFrame.delta();
        float offset = glide.to(scroll * (TILE_HEIGHT + TILE_GAP), delta);
        UiRender.clip(graphics, left - RIM_REACH, top, width + RIM_REACH * 2.0f, height);
        try {
            for (int index = 0; index < shown.size(); index++) {
                paintSlot(graphics, shown.get(index), index, offset, mouseX, mouseY, delta);
            }
            graphics.flush();
        } finally {
            graphics.disableScissor();
        }
    }

    private void paintSlot(GuiGraphics graphics, Person person, int index, float offset, int mouseX, int mouseY,
                           float delta) {
        Slot slot = slots.get(person.id());
        float x = left + slot.x.to(columnX(index), delta);
        float y = top + slot.y.to(rowY(index), delta) - offset;
        if (y + TILE_HEIGHT < top || y > top + height) return;

        float appear = UiAnim.easeOut((System.currentTimeMillis() - slot.bornAt) / APPEAR_MS);
        boolean over = over(mouseX, mouseY) && mouseX >= x && mouseX < x + TILE_WIDTH && mouseY >= y
                && mouseY < y + TILE_HEIGHT;
        float focus = slot.hover.to(over ? 1.0f : 0.0f, delta);
        float picked = slot.choice.to(person.id().equals(chosen) ? 1.0f : 0.0f, delta);
        float lifted = y + (1.0f - appear) * APPEAR_LIFT - focus;
        UiGlass.panel(graphics, x, lifted, TILE_WIDTH, TILE_HEIGHT, RADIUS, appear, 0.35f * picked + focus * 0.5f);
        if (picked > 0.01f) {
            UiRender.rim(graphics, x, lifted, TILE_WIDTH, TILE_HEIGHT, RADIUS, RIM,
                    UiTheme.alpha(UiAccent.color(), RIM_ALPHA * picked * appear));
        }
        paintFace(graphics, person, slot, x, lifted, appear, Math.max(picked, focus));
    }

    private void paintFace(GuiGraphics graphics, Person person, Slot slot, float x, float y, float appear,
                           float lit) {
        float faceX = x + (TILE_WIDTH - FACE) / 2.0f;
        float faceY = y + FACE_TOP;
        UiRender.panel(graphics, faceX - FACE_FRAME, faceY - FACE_FRAME, FACE + FACE_FRAME * 2.0f,
                FACE + FACE_FRAME * 2.0f, FACE_FRAME * 2.0f, UiTheme.alpha(FACE_SHADE, appear));
        PlayerHeads.face(graphics, person.id(), faceX, faceY, FACE, appear);
        int ink = UiTheme.mix(UiAccent.text(), UiTheme.WHITE, lit * 0.5f);
        UiRender.textTrackedFit(graphics, font(), slot.name, x + TILE_WIDTH / 2.0f, y + NAME_TOP, NAME_HEIGHT,
                TILE_WIDTH - 6.0f, NAME_SCALE, 0.0f, UiTheme.alpha(ink, appear), false);
        if (person.mate()) {
            UiRender.textTrackedFit(graphics, font(), MATE, x + TILE_WIDTH / 2.0f, y + TAG_TOP, TAG_HEIGHT,
                    TILE_WIDTH - 6.0f, TAG_SCALE, 0.3f, UiTheme.alpha(UiAccent.color(), appear), false);
        }
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}
