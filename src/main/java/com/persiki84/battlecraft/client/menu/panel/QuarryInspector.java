package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.HeadingRow;
import com.persiki84.shared.client.menu.MenuRow;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.studio.StudioStack;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

// WHY: справа то, что правят у выбранного: у блока на карте свой откат и снятие, у вида блока
// WHY: откат и множитель, в «Общем» общий откат и прежние действия по прицелу
final class QuarryInspector {
    private static final int ROW = 22;
    private static final int MAX_SECONDS = 3600;
    private static final int MAX_MULTIPLIER = 64;
    private static final String DROP = "drop-quarry";

    private final QuarryStudioScreen screen;
    private final Map<String, MenuRow> made = new HashMap<>();
    private final Map<String, HeadingRow> headings = new HashMap<>();
    private final ModuleSwitch module = new ModuleSwitch(com.persiki84.battlecraft.modules.ModuleId.QUARRY);
    private int aimedCooldown = 60;

    QuarryInspector(QuarryStudioScreen screen) {
        this.screen = screen;
    }

    private static <T extends AbstractWidget> T sized(T widget, int width) {
        widget.setWidth(width);
        return widget;
    }

    private MenuRow row(String key, Supplier<MenuRow> maker) {
        return made.computeIfAbsent(key, unused -> maker.get());
    }

    private void heading(StudioStack stack, int x, int width, String key) {
        HeadingRow heading = headings.computeIfAbsent(key,
                unused -> new HeadingRow(0, 0, 10, ROW, Component.translatable(key)));
        stack.add(sized(heading, width), x, ROW / 3);
    }

    private void add(StudioStack stack, int x, int width, AbstractWidget widget) {
        stack.add(sized(widget, width), x);
    }

    void build(StudioStack stack, int x, int width) {
        String node = screen.node();
        if (QuarryStudioScreen.MAP.equals(node) && screen.block() != null) buildBlock(stack, x, width);
        else if (QuarryStudioScreen.TYPES.equals(node) && screen.rule() != null) buildType(stack, x, width);
        else buildCommon(stack, x, width);
    }

    private void buildBlock(StudioStack stack, int x, int width) {
        heading(stack, x, width, "studio.quarry.group.block");
        add(stack, x, width, row("own", () -> new NumberRow(0, 0, 10, ROW,
                Component.translatable("quarrymod.menu.block_cooldown"), this::ownCooldown,
                value -> blockLater("cooldown set " + value), 1, MAX_SECONDS, 5).hint("studio.quarry.own.hint")));
        add(stack, x, width, row("reset", () -> action("studio.quarry.reset", "capturepoints.menu.action.reset",
                () -> blockNow("cooldown reset"))));
        add(stack, x, width, row("tp", () -> action("studio.menu.tp", "studio.points.action.go", this::teleport)));
        add(stack, x, width, row("remove", () -> new ActionRow(0, 0, 10, ROW, Component.translatable("studio.quarry.remove"),
                () -> Component.translatable(screen.isArmed(DROP) ? "studio.sure" : "studio.delete"),
                this::pressRemove).alerting()));
    }

    private int ownCooldown() {
        CompoundTag block = screen.block();
        if (block == null) return 0;
        int own = block.getInt("cooldown");
        return own > 0 ? own : QuarryStudioScreen.state().getInt("globalCooldown");
    }

    private void blockLater(String tail) {
        CompoundTag block = screen.block();
        if (block != null) screen.later("block:" + QuarryStudioScreen.key(block), "at " + QuarryStudioScreen.key(block) + " " + tail);
    }

    private void blockNow(String tail) {
        CompoundTag block = screen.block();
        if (block != null) screen.at(QuarryStudioScreen.key(block), tail);
    }

    private void teleport() {
        CompoundTag block = screen.block();
        if (block != null) screen.teleportTo(block);
    }

    private void pressRemove() {
        CompoundTag block = screen.block();
        if (block != null && screen.confirmed(DROP)) screen.removeBlock(QuarryStudioScreen.key(block));
    }

    private void buildType(StudioStack stack, int x, int width) {
        heading(stack, x, width, "studio.quarry.group.type");
        add(stack, x, width, row("typecooldown", () -> new NumberRow(0, 0, 10, ROW,
                Component.translatable("studio.quarry.type.cooldown"), () -> read(rule -> rule.getInt("cooldown")),
                value -> typeLater("cooldown " + value), -1, MAX_SECONDS, 5)
                .floorLabel(Component.translatable("quarrymod.menu.type.global")).hint("studio.quarry.type.cooldown.hint")));
        add(stack, x, width, row("multiplier", () -> new NumberRow(0, 0, 10, ROW,
                Component.translatable("studio.quarry.type.multiplier"),
                () -> Math.max(1, read(rule -> rule.getInt("multiplier"))), value -> typeLater("multiplier " + value),
                1, MAX_MULTIPLIER, 1).hint("studio.quarry.type.multiplier.hint")));
    }

    private int read(ToIntFunction<CompoundTag> reader) {
        CompoundTag rule = screen.rule();
        return rule == null ? 0 : reader.applyAsInt(rule);
    }

    private void typeLater(String tail) {
        CompoundTag rule = screen.rule();
        if (rule != null) {
            String block = rule.getString("block");
            screen.later("type:" + block + ":" + tail.substring(0, tail.indexOf(' ')), "type " + block + " " + tail);
        }
    }

    private void buildCommon(StudioStack stack, int x, int width) {
        heading(stack, x, width, "studio.quarry.group.common");
        module.place(stack, x, width);
        add(stack, x, width, row("global", () -> new NumberRow(0, 0, 10, ROW,
                Component.translatable("quarrymod.menu.global"),
                () -> QuarryStudioScreen.state().getInt("globalCooldown"),
                value -> screen.later("global", "cooldown global " + value), 1, MAX_SECONDS, 5)
                .hint("quarrymod.menu.global.hint")));
        heading(stack, x, width, "studio.quarry.group.aimed");
        add(stack, x, width, row("add", () -> action("quarrymod.menu.add", "quarrymod.menu.action.aimed",
                () -> screen.now("add"))));
        add(stack, x, width, row("removeaimed", () -> action("quarrymod.menu.remove", "quarrymod.menu.action.aimed",
                () -> screen.now("remove"))));
        add(stack, x, width, row("info", () -> action("quarrymod.menu.info", "quarrymod.menu.action.aimed",
                () -> screen.now("info"))));
        add(stack, x, width, row("aimedcooldown", () -> new NumberRow(0, 0, 10, ROW,
                Component.translatable("quarrymod.menu.block_cooldown"), () -> aimedCooldown,
                value -> aimedCooldown = value, 1, MAX_SECONDS, 5)));
        add(stack, x, width, row("aimedset", () -> action("quarrymod.menu.block_cooldown_set",
                "quarrymod.menu.action.aimed", () -> screen.now("cooldown set " + aimedCooldown))));
        add(stack, x, width, row("aimedreset", () -> action("quarrymod.menu.block_cooldown_reset",
                "quarrymod.menu.action.aimed", () -> screen.now("cooldown reset"))));
    }

    void request(int ticks) {
        module.request(ticks);
    }

    private static ActionRow action(String label, String value, Runnable run) {
        ActionRow row = new ActionRow(0, 0, 10, ROW, Component.translatable(label), () -> Component.translatable(value), run);
        row.hint(label + ".hint");
        return row;
    }
}
