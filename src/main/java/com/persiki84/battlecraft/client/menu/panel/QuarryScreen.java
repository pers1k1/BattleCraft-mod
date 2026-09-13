package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.Names;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.NumberRow;
import com.persiki84.shared.client.menu.PanelScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class QuarryScreen extends PanelScreen {
    private static final String COMMAND = "quarry";
    private static final int MAX_SECONDS = 3600;
    private static final int GLOBAL_COOLDOWN = -1;
    private static final int MIN_MULTIPLIER = 1;
    private static final int MAX_MULTIPLIER = 64;
    private static final int DEFAULT_BLOCK_COOLDOWN = 60;

    private int aimedCooldown = DEFAULT_BLOCK_COOLDOWN;

    public QuarryScreen() {
        super(Component.translatable("quarrymod.menu.title"));
    }

    @Override
    protected String menuId() {
        return ModuleMenuStates.QUARRY;
    }

    @Override
    protected List<Page> pages() {
        return List.of(
                new Page(Component.translatable("quarrymod.menu.tab.cooldown"), this::cooldownRows),
                new Page(Component.translatable("quarrymod.menu.tab.blocks"), this::blockRows),
                new Page(Component.translatable("quarrymod.menu.tab.list"), this::listRows),
                new Page(Component.translatable("quarrymod.menu.tab.types"), this::typeRows));
    }

    private static List<CompoundTag> rules() {
        ListTag stored = MenuData.state(ModuleMenuStates.QUARRY)
                .getList(ModuleMenuStates.QUARRY_RULES, Tag.TAG_COMPOUND);
        List<CompoundTag> rules = new ArrayList<>();
        for (int index = 0; index < stored.size(); index++) {
            rules.add(stored.getCompound(index));
        }
        return rules;
    }

    private static CompoundTag rule(String block) {
        for (CompoundTag entry : rules()) {
            if (entry.getString("block").equals(block)) return entry;
        }
        return new CompoundTag();
    }

    private List<AbstractWidget> typeRows() {
        List<AbstractWidget> rows = new ArrayList<>();
        for (CompoundTag entry : rules()) {
            String block = entry.getString("block");
            rows.add(cooldownRow(block));
            rows.add(multiplierRow(block));
        }
        if (rows.isEmpty()) rows.add(reading("quarrymod.menu.type.empty", Component::empty));
        return rows;
    }

    private NumberRow cooldownRow(String block) {
        NumberRow row = new NumberRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("quarrymod.menu.type.cooldown", Names.block(block)),
                () -> rule(block).getInt("cooldown"),
                value -> send(COMMAND + " type " + block + " cooldown " + value),
                GLOBAL_COOLDOWN, MAX_SECONDS, 5);
        return row.floorLabel(Component.translatable("quarrymod.menu.type.global"));
    }

    private NumberRow multiplierRow(String block) {
        return new NumberRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("quarrymod.menu.type.multiplier", Names.block(block)),
                () -> Math.max(1, rule(block).getInt("multiplier")),
                value -> send(COMMAND + " type " + block + " multiplier " + value),
                MIN_MULTIPLIER, MAX_MULTIPLIER, 1);
    }

    private List<AbstractWidget> cooldownRows() {
        return List.of(
                number("quarrymod.menu.global", () -> MenuData.state(menuId()).getInt("globalCooldown"),
                        value -> send(COMMAND + " cooldown global " + value), 1, MAX_SECONDS, 5),
                reading("quarrymod.menu.count",
                        () -> Component.literal(String.valueOf(MenuData.state(menuId()).getInt("blocks")))));
    }

    private List<AbstractWidget> blockRows() {
        return List.of(
                action("quarrymod.menu.add", "quarrymod.menu.action.aimed", () -> send(COMMAND + " add")),
                action("quarrymod.menu.remove", "quarrymod.menu.action.aimed", () -> send(COMMAND + " remove")),
                action("quarrymod.menu.info", "quarrymod.menu.action.aimed", () -> send(COMMAND + " info")),
                number("quarrymod.menu.block_cooldown", () -> aimedCooldown, value -> aimedCooldown = value,
                        1, MAX_SECONDS, 5),
                action("quarrymod.menu.block_cooldown_set", "quarrymod.menu.action.aimed",
                        () -> send(COMMAND + " cooldown set " + aimedCooldown)),
                action("quarrymod.menu.block_cooldown_reset", "quarrymod.menu.action.aimed",
                        () -> send(COMMAND + " cooldown reset")));
    }

    private static List<CompoundTag> blocks() {
        ListTag stored = MenuData.state(ModuleMenuStates.QUARRY)
                .getList(ModuleMenuStates.QUARRY_BLOCKS, Tag.TAG_COMPOUND);
        List<CompoundTag> blocks = new ArrayList<>();
        for (int index = 0; index < stored.size(); index++) {
            blocks.add(stored.getCompound(index));
        }
        return blocks;
    }

    private List<AbstractWidget> listRows() {
        List<CompoundTag> blocks = blocks();
        if (blocks.isEmpty()) return List.of(reading("quarrymod.menu.list.empty", Component::empty));

        List<AbstractWidget> rows = new ArrayList<>();
        for (CompoundTag block : blocks) {
            rows.add(blockRow(block));
        }

        int total = MenuData.state(menuId()).getInt("blocks");
        if (total > blocks.size()) {
            rows.add(reading("quarrymod.menu.list.more",
                    () -> Component.literal(String.valueOf(total - blocks.size()))));
        }
        return rows;
    }

    private AbstractWidget blockRow(CompoundTag block) {
        ActionRow row = new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Names.block(block.getString("block")), () -> position(block), () -> {});
        row.note(blockNote(block));
        row.active = false;
        return row;
    }

    private static Component blockNote(CompoundTag block) {
        Component dimension = dimensionLabel(block.getString("dimension"));
        int cooldown = block.getInt("cooldown");
        if (cooldown <= 0) return dimension;

        return Component.translatable("quarrymod.menu.list.custom", dimension, cooldown);
    }

    private static Component position(CompoundTag block) {
        return Component.literal(block.getInt("x") + " " + block.getInt("y") + " " + block.getInt("z"));
    }

    private static Component dimensionLabel(String dimension) {
        int mark = dimension.indexOf(':');
        return Component.literal(mark < 0 ? dimension : dimension.substring(mark + 1));
    }
}
