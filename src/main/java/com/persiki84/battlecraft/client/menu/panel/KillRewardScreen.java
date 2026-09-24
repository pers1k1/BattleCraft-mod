package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.Names;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.ActionRow;
import com.persiki84.shared.client.menu.pick.ItemPickerScreen;
import com.persiki84.shared.client.menu.PanelScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class KillRewardScreen extends PanelScreen {
    private static final String COMMAND = "killreward";
    private static final int MAX_AMOUNT = 64;

    public KillRewardScreen() {
        super(Component.translatable("killreward.menu.title"));
    }

    @Override
    protected String menuId() {
        return ModuleMenuStates.KILL_REWARD;
    }

    @Override
    protected List<Page> pages() {
        return List.of(new Page(Component.translatable("killreward.menu.tab.reward"), this::rows));
    }

    private List<AbstractWidget> rows() {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(toggle("killreward.menu.enabled", () -> MenuData.state(menuId()).getBoolean("modEnabled"),
                value -> send(COMMAND + (value ? " enable" : " disable"))));
        rows.add(itemRow());
        rows.add(number("killreward.menu.amount", () -> MenuData.state(menuId()).getInt("rewardAmount"),
                value -> send(COMMAND + " setamount " + value), 1, MAX_AMOUNT, 1));
        rows.add(toggle("killreward.menu.team_kills", () -> MenuData.state(menuId()).getBoolean("rewardTeamKills"),
                value -> send(COMMAND + " teamkills " + value)));
        return rows;
    }

    // WHY: предмет награды выбирается глазами из инвентаря или всего реестра, с количеством сразу:
    // WHY: держать его в руке перед нажатием строки меню было неочевидно и неудобно
    private ActionRow itemRow() {
        ActionRow row = new ActionRow(rowsLeft(), 0, rowsWidth(), ROW_HEIGHT,
                Component.translatable("killreward.menu.item"),
                () -> Names.item(MenuData.state(menuId()).getString("rewardItem")), this::pickReward);
        row.hint("killreward.menu.item.hint");
        return row;
    }

    private void pickReward() {
        int amount = Math.max(1, MenuData.state(menuId()).getInt("rewardAmount"));
        ItemPickerScreen.open(Component.translatable("killreward.menu.pick.title"), this,
                ItemPickerScreen.Options.counted(Component.translatable("killreward.menu.amount"), 1, MAX_AMOUNT, amount),
                choice -> {
                    send(COMMAND + " setitem " + choice.itemId());
                    send(COMMAND + " setamount " + choice.amount());
                });
    }
}
