package com.persiki84.capturepoints.client.menu;

import com.persiki84.battlecraft.rules.MarkerRange;
import com.persiki84.capturepoints.capture.CaptureCommandRunner;
import com.persiki84.capturepoints.capture.CaptureMode;
import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.capturepoints.capture.CooldownScope;
import com.persiki84.capturepoints.capture.RewardSplit;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.FieldRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.ItemRow;
import com.persiki84.shared.client.menu.MenuFeedback;
import com.persiki84.shared.client.menu.MenuRow;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.PickRow;
import com.persiki84.shared.client.menu.ToggleRow;
import com.persiki84.shared.client.menu.pick.ItemShelf;
import com.persiki84.shared.client.menu.studio.StudioStack;
import com.persiki84.shared.zone.ZoneShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// WHY: свойства точки лежат одной колонкой группами, как в студии магазина: чтобы поправить точку,
// WHY: не нужно ходить по вкладкам. Строки созданы один раз и только переставляются: их анимации
// WHY: живут в самих строках
final class PointInspector {
    private static final int ROW = 22;
    private static final int MAX_RADIUS = 100;
    private static final int MAX_HEIGHT = 256;
    private static final int MAX_SECONDS = 3600;
    private static final int MAX_AMOUNT = 64;
    private static final int MAX_PERCENT = 2000;
    private static final int MAX_LEVEL = 6;
    private static final int RANGE_STEP = 50;
    private static final int EFFECT_LIMIT = 64;
    private static final String BUFF = "buff";
    private static final String DROP = "drop-point";
    private static final Component SURE = Component.translatable("studio.sure");
    private static final Component DELETE = Component.translatable("studio.delete");

    private final PointStudioScreen screen;
    private final PointForms forms;
    private final Map<String, MenuRow> made = new HashMap<>();
    private final Map<String, HeadingRow> headings = new HashMap<>();
    private final Map<Integer, ActionRow> commandRows = new HashMap<>();
    private final FieldRow buffRow;
    private final FieldRow commandField;
    private PickRow ownerRow;
    private List<String> ownerTeams = List.of();
    private String filledFor;
    private boolean filling;
    private int buffLevel = 1;

    PointInspector(PointStudioScreen screen) {
        this.screen = screen;
        this.forms = new PointForms(screen);
        buffRow = new FieldRow(0, 0, 10, ROW, Component.translatable("capturepoints.menu.buff_id"),
                Component.translatable("capturepoints.menu.buff_id.placeholder"), "", EFFECT_LIMIT, this::buffTyped);
        buffRow.hint("studio.points.buff.hint");
        commandField = new FieldRow(0, 0, 10, ROW, Component.translatable("capturepoints.menu.command"),
                Component.translatable("capturepoints.menu.command.placeholder"), "", CaptureCommandRunner.MAX_LENGTH,
                value -> { });
        commandField.hint("capturepoints.menu.command.hint");
    }

    String typedName() {
        return forms.typedName();
    }

    ZoneShape newShape() {
        return forms.newShape();
    }

    int newRadius() {
        return forms.newRadius();
    }

    void beginCreate() {
        forms.beginCreate();
    }

    boolean submit() {
        if (screen.creating()) return forms.submitCreate();
        if (!commandField.capturing()) return false;
        addCommand();
        return true;
    }

    void build(StudioStack stack, int x, int width) {
        if (screen.creating()) {
            forms.buildCreate(stack, x, width);
            return;
        }
        if (screen.commonChosen()) {
            forms.buildCommon(stack, x, width);
            return;
        }
        PointEntry entry = screen.entry();
        if (entry == null) return;
        if (screen.shelfFor() != null) {
            buildShelf(stack, x, width, entry);
            return;
        }
        fill();
        buildArea(stack, x, width);
        buildCapture(stack, x, width);
        buildShow(stack, x, width, entry);
        buildBonuses(stack, x, width, entry);
        buildCommands(stack, x, width, entry);
        buildActions(stack, x, width, entry);
    }

    private static <T extends AbstractWidget> T sized(T widget, int width) {
        widget.setWidth(width);
        return widget;
    }

    private void add(StudioStack stack, int x, int width, AbstractWidget widget) {
        stack.add(sized(widget, width), x);
    }

    private void heading(StudioStack stack, int x, int width, String key) {
        HeadingRow heading = headings.computeIfAbsent(key,
                unused -> new HeadingRow(0, 0, 10, ROW, Component.translatable(key)));
        stack.add(sized(heading, width), x, ROW / 3);
    }

    private MenuRow row(String key, java.util.function.Supplier<MenuRow> maker) {
        return made.computeIfAbsent(key, unused -> maker.get());
    }

    private void buildArea(StudioStack stack, int x, int width) {
        heading(stack, x, width, "studio.points.group.area");
        add(stack, x, width, row("shape", () -> new PickRow(0, 0, 10, ROW,
                Component.translatable("capturepoints.menu.shape"), shapeLabels(),
                () -> current(entry -> entry.shape().ordinal()),
                picked -> command("setshape", ZoneShape.values()[picked].id()))));
        add(stack, x, width, number("radius", "capturepoints.menu.radius", "size", 1, MAX_RADIUS, 1, "setradius"));
        add(stack, x, width, height(true));
        add(stack, x, width, height(false));
        add(stack, x, width, row("resetheight", () -> new ActionRow(0, 0, 10, ROW,
                Component.translatable("capturepoints.menu.reset_height"),
                () -> Component.translatable("capturepoints.menu.action.reset"), () -> command("resetheight", null))));
    }

    private MenuRow height(boolean up) {
        String field = up ? "heightUp" : "heightDown";
        return row(field, () -> new NumberRow(0, 0, 10, ROW,
                Component.translatable(up ? "capturepoints.menu.height_up" : "capturepoints.menu.height_down"),
                () -> current(entry -> entry.number(field)), value -> applyHeight(up, value), 0, MAX_HEIGHT, 1));
    }

    private void applyHeight(boolean up, int value) {
        PointEntry entry = screen.entry();
        if (entry == null) return;
        int other = entry.number(up ? "heightDown" : "heightUp");
        screen.later("height", entry.root() + " setheight " + entry.quoted() + " " + (up ? value : other) + " "
                + (up ? other : value));
    }

    private void buildCapture(StudioStack stack, int x, int width) {
        heading(stack, x, width, "studio.points.group.capture");
        add(stack, x, width, number("time", "capturepoints.menu.capture_time", "captureTime", 1, MAX_SECONDS, 5,
                "setcapturetime"));
        add(stack, x, width, number("cooldown", "capturepoints.menu.cooldown", "cooldown", 0, MAX_SECONDS, 5,
                "setcooldown"));
        add(stack, x, width, pick("mode", "capturepoints.menu.mode", CaptureMode.values(), CaptureMode::translationKey,
                entry -> CaptureMode.byId(entry.tag().getString("mode")).ordinal(),
                index -> command("setmode", CaptureMode.values()[index].id())));
        add(stack, x, width, pick("split", "capturepoints.menu.split", RewardSplit.values(),
                RewardSplit::translationKey, entry -> RewardSplit.byId(entry.tag().getString("split")).ordinal(),
                index -> command("setrewardsplit", RewardSplit.values()[index].id())));
        add(stack, x, width, number("speed", "capturepoints.menu.capture_speed", "captureSpeed", 1, MAX_PERCENT, 10,
                "setcapturespeed"));
        add(stack, x, width, number("rollback", "capturepoints.menu.rollback_speed", "rollbackSpeed", 1, MAX_PERCENT,
                10, "setrollback"));
        add(stack, x, width, number("pressured", "capturepoints.menu.pressured_rollback", "pressuredRollbackSpeed", 1,
                MAX_PERCENT, 10, "setpressuredrollback"));
        add(stack, x, width, number("owned", "capturepoints.menu.owned_rollback", "ownedRollbackSpeed", 1,
                MAX_PERCENT, 10, "setownedrollback"));
        add(stack, x, width, number("teamcooldown", "capturepoints.menu.team_cooldown", "teamCooldown", 0,
                MAX_SECONDS, 5, "setteamcooldown"));
        add(stack, x, width, pick("scope", "capturepoints.menu.cooldown_scope", CooldownScope.values(),
                CooldownScope::translationKey,
                entry -> CooldownScope.byId(entry.tag().getString("cooldownScope")).ordinal(),
                index -> command("setteamcooldownscope", CooldownScope.values()[index].id())));
    }

    private void buildShow(StudioStack stack, int x, int width, PointEntry entry) {
        heading(stack, x, width, "studio.points.group.show");
        add(stack, x, width, ownerRow());
        if (!entry.last()) add(stack, x, width, toggle("required", "capturepoints.menu.required", "setrequired"));
        add(stack, x, width, toggle("shownInHud", "capturepoints.menu.shown_in_hud", "sethud"));
        add(stack, x, width, toggle("hiddenInside", "capturepoints.menu.hidden_inside", "sethideinside"));
        NumberRow range = (NumberRow) number("range", "capturepoints.menu.marker_range", "markerRange",
                CapturePoint.KIND_RANGE, MarkerRange.MAX_BLOCKS, RANGE_STEP, "setmarkerrange");
        range.floorLabel(Component.translatable("battlecraft.markers.kind"));
        add(stack, x, width, range);
    }

    // WHY: команды скорборда меняются, пока экран открыт: строка владельца пересоздаётся только на
    // WHY: смене их списка, иначе её переход обрывался бы на каждом снимке
    private PickRow ownerRow() {
        List<String> teams = teamNames();
        if (ownerRow != null && teams.equals(ownerTeams)) return ownerRow;
        ownerTeams = teams;
        List<Component> options = new ArrayList<>();
        options.add(Component.translatable("capturepoints.menu.owner_none"));
        for (String team : teams) {
            options.add(Component.literal(team));
        }
        ownerRow = new PickRow(0, 0, 10, ROW, Component.translatable("capturepoints.menu.owner"), options,
                () -> current(entry -> ownerTeams.indexOf(entry.owner()) + 1), this::applyOwner);
        ownerRow.hint("capturepoints.menu.owner.hint");
        return ownerRow;
    }

    private void applyOwner(int picked) {
        if (picked == 0) {
            command("clearowner", null);
            return;
        }
        command("setowner", ownerTeams.get(picked - 1));
    }

    private static List<String> teamNames() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return List.of();
        return new ArrayList<>(minecraft.level.getScoreboard().getTeamNames());
    }

    private void buildBonuses(StudioStack stack, int x, int width, PointEntry entry) {
        heading(stack, x, width, "capturepoints.menu.group.reward");
        add(stack, x, width, itemRow("rewardItem", "capturepoints.menu.reward_item", PointEntry::reward,
                PointStudioScreen.REWARD));
        add(stack, x, width, amountRow("rewardAmount", "capturepoints.menu.reward", true));
        if (entry.last()) return;
        heading(stack, x, width, "capturepoints.menu.group.income");
        add(stack, x, width, itemRow("incomeItem", "capturepoints.menu.income_item", PointEntry::income,
                PointStudioScreen.INCOME));
        add(stack, x, width, amountRow("incomeAmount", "capturepoints.menu.income", false));
        add(stack, x, width, number("interval", "capturepoints.menu.income_interval", "incomeInterval", 1,
                MAX_SECONDS, 10, "setincomeinterval"));
        buildBuff(stack, x, width);
    }

    private MenuRow itemRow(String key, String label, Function<PointEntry, ItemStack> stack, String shelf) {
        return row(key, () -> new ItemRow(0, 0, 10, ROW, Component.translatable(label),
                () -> {
                    PointEntry entry = screen.entry();
                    return entry == null ? ItemStack.EMPTY : stack.apply(entry);
                },
                () -> Component.translatable("studio.points.pick_item"), () -> screen.openShelf(shelf))
                .hint("studio.points.item.hint"));
    }

    MenuRow amountRow(String field, String label, boolean reward) {
        return row("amount:" + field, () -> new NumberRow(0, 0, 10, ROW, Component.translatable(label),
                () -> current(entry -> entry.number(field)), value -> applyAmount(reward, value), 0, MAX_AMOUNT, 1)
                .hint(label + ".hint"));
    }

    private void applyAmount(boolean reward, int amount) {
        PointEntry entry = screen.entry();
        if (entry == null) return;
        ItemStack item = reward ? entry.reward() : entry.income();
        if (reward && amount <= 0) {
            screen.later("reward", entry.root() + " removereward " + entry.quoted());
            return;
        }
        if (item.isEmpty()) {
            MenuFeedback.show(Component.translatable("studio.points.error.no_item"), true);
            return;
        }
        String verb = reward ? " setreward " : " setincome ";
        screen.later(reward ? "reward" : "income", entry.root() + verb + entry.quoted() + " "
                + ItemShelf.spec(item) + " " + amount);
    }

    private void buildBuff(StudioStack stack, int x, int width) {
        heading(stack, x, width, "capturepoints.menu.group.buff");
        add(stack, x, width, buffRow);
        add(stack, x, width, row("bufflevel", () -> new NumberRow(0, 0, 10, ROW,
                Component.translatable("capturepoints.menu.buff_level"), () -> buffLevel, this::levelPicked,
                1, MAX_LEVEL, 1)));
        add(stack, x, width, row("buffclear", () -> new ActionRow(0, 0, 10, ROW,
                Component.translatable("capturepoints.menu.buff_clear"),
                () -> Component.translatable("capturepoints.menu.action.clear"), this::clearBuff)));
    }

    // WHY: эффект уходит на сервер, только когда набранное стало существующим эффектом: иначе каждая
    // WHY: буква по дороге к «minecraft:speed» отвечала бы ошибкой в ленте
    private void buffTyped(String text) {
        if (filling) return;
        ResourceLocation id = ResourceLocation.tryParse(text.trim());
        if (id == null || !BuiltInRegistries.MOB_EFFECT.containsKey(id)) return;
        sendBuff(id.toString());
    }

    private void levelPicked(int level) {
        buffLevel = level;
        PointEntry entry = screen.entry();
        if (entry != null && !entry.tag().getString("buff").isEmpty()) sendBuff(entry.tag().getString("buff"));
    }

    private void sendBuff(String effect) {
        PointEntry entry = screen.entry();
        if (entry == null) return;
        screen.later(BUFF, entry.root() + " setbuff " + entry.quoted() + " \"" + effect + "\" " + (buffLevel - 1));
    }

    private void clearBuff() {
        command("clearbuff", null);
        filling = true;
        try {
            buffRow.box().setValue("");
        } finally {
            filling = false;
        }
    }

    // WHY: команд у точки несколько, и вслепую их не настроить: каждая видна целиком и снимается
    // WHY: своей строкой, а поле ниже только добавляет новую, Enter в нём тоже добавляет
    private void buildCommands(StudioStack stack, int x, int width, PointEntry entry) {
        heading(stack, x, width, "capturepoints.menu.group.command");
        ListTag commands = entry.tag().getList("commands", Tag.TAG_STRING);
        for (int index = 0; index < commands.size(); index++) {
            ActionRow line = commandRow(index);
            line.note(Component.literal(commands.getString(index)));
            add(stack, x, width, line);
        }
        if (commands.size() < CapturePoint.MAX_COMMANDS) {
            add(stack, x, width, commandField);
            add(stack, x, width, row("commandadd", () -> new ActionRow(0, 0, 10, ROW,
                    Component.translatable("capturepoints.menu.command_apply"),
                    () -> Component.translatable("capturepoints.menu.action.add"), this::addCommand)));
        }
    }

    private ActionRow commandRow(int index) {
        return commandRows.computeIfAbsent(index, unused -> new ActionRow(0, 0, 10, ROW,
                Component.translatable("capturepoints.menu.command_line", index + 1),
                () -> Component.translatable("capturepoints.menu.action.remove"),
                () -> command("removecommand", String.valueOf(index + 1))));
    }

    private void addCommand() {
        String command = commandField.value().trim();
        if (command.isEmpty()) return;
        command("addcommand", command);
        commandField.box().setValue("");
    }

    private void buildActions(StudioStack stack, int x, int width, PointEntry entry) {
        heading(stack, x, width, "studio.points.group.actions");
        add(stack, x, width, action("tp", "studio.menu.tp", "studio.points.action.go",
                () -> withEntry(screen::teleport)));
        add(stack, x, width, action("here", "studio.menu.move_here", "studio.points.action.here",
                () -> withEntry(screen::moveHere)));
        if (!entry.last()) {
            add(stack, x, width, action("resetcooldown", "capturepoints.menu.reset_cooldown",
                    "capturepoints.menu.action.reset", () -> withEntry(point ->
                            screen.now("resetcapturecooldown " + point.quoted()))));
        }
        add(stack, x, width, row("delete", () -> new ActionRow(0, 0, 10, ROW,
                Component.translatable("capturepoints.menu.remove"),
                () -> screen.isArmed(DROP) ? SURE : DELETE, this::pressDelete).alerting()));
    }

    private MenuRow action(String key, String label, String value, Runnable run) {
        return row("action:" + key, () -> new ActionRow(0, 0, 10, ROW, Component.translatable(label),
                () -> Component.translatable(value), run));
    }

    private void withEntry(java.util.function.Consumer<PointEntry> run) {
        PointEntry entry = screen.entry();
        if (entry != null) run.accept(entry);
    }

    private void pressDelete() {
        PointEntry entry = screen.entry();
        if (entry != null && screen.confirmed(DROP)) screen.remove(entry);
    }

    private MenuRow number(String key, String label, String field, int minimum, int maximum, int step, String verb) {
        return row("number:" + key, () -> new NumberRow(0, 0, 10, ROW, Component.translatable(label),
                () -> current(entry -> entry.number(field)),
                value -> later(key, verb, String.valueOf(value)), minimum, maximum, step).hint(label + ".hint"));
    }

    private <T> MenuRow pick(String key, String label, T[] values, Function<T, String> translation,
                             java.util.function.ToIntFunction<PointEntry> reader,
                             java.util.function.IntConsumer apply) {
        return row("pick:" + key, () -> {
            List<Component> options = new ArrayList<>();
            for (T value : values) {
                options.add(Component.translatable(translation.apply(value)));
            }
            return new PickRow(0, 0, 10, ROW, Component.translatable(label), options,
                    () -> current(reader), apply);
        });
    }

    private MenuRow toggle(String field, String label, String verb) {
        return row("toggle:" + field, () -> new ToggleRow(0, 0, 10, ROW, Component.translatable(label),
                () -> current(entry -> entry.flag(field) ? 1 : 0) == 1,
                value -> command(verb, String.valueOf(value))).hint(label + ".hint"));
    }

    private int current(java.util.function.ToIntFunction<PointEntry> reader) {
        PointEntry entry = screen.entry();
        return entry == null ? 0 : reader.applyAsInt(entry);
    }

    private static List<Component> shapeLabels() {
        List<Component> options = new ArrayList<>();
        for (ZoneShape shape : ZoneShape.values()) {
            options.add(Component.translatable("zones.shape." + shape.id()));
        }
        return options;
    }

    private void command(String verb, String tail) {
        PointEntry entry = screen.entry();
        if (entry == null) return;
        screen.now(entry.root() + " " + verb + " " + entry.quoted() + (tail == null ? "" : " " + tail));
    }

    private void later(String key, String verb, String tail) {
        PointEntry entry = screen.entry();
        if (entry == null) return;
        screen.later(key, entry.root() + " " + verb + " " + entry.quoted() + " " + tail);
    }

    private void buildShelf(StudioStack stack, int x, int width, PointEntry entry) {
        boolean reward = PointStudioScreen.REWARD.equals(screen.shelfFor());
        heading(stack, x, width, reward ? "capturepoints.menu.group.reward" : "capturepoints.menu.group.income");
        add(stack, x, width, reward ? amountRow("rewardAmount", "capturepoints.menu.reward", true)
                : amountRow("incomeAmount", "capturepoints.menu.income", false));
        add(stack, x, width, action("shelfdone", "studio.points.shelf.back", "studio.mode.done", screen::closeShelf));
    }

    // WHY: поле эффекта заполняется из снимка только при смене точки или когда по нему нет
    // WHY: отложенной правки: иначе пришедший до отправки снимок вернул бы под пальцы старый текст
    void fill() {
        PointEntry entry = screen.entry();
        String subject = entry == null ? "" : entry.key();
        boolean fresh = !subject.equals(filledFor);
        filledFor = subject;
        if (entry == null || buffRow.capturing() || !fresh && screen.pending(BUFF)) return;
        filling = true;
        try {
            String effect = entry.tag().getString("buff");
            if (!buffRow.value().equals(effect)) buffRow.box().setValue(effect);
            if (fresh) buffLevel = Math.max(1, entry.number("buffAmplifier") + 1);
        } finally {
            filling = false;
        }
    }
}
