package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.Names;
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
                action("quarrymod.menu.block_cooldown_reset", "quarrymod.menu.action.aimed",
                        () -> send(COMMAND + " cooldown reset")));
    }
}
