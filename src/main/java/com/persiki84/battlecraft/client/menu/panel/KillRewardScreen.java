package com.persiki84.battlecraft.client.menu.panel;

import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.Names;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.MenuFeedback;
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
        rows.add(reading("killreward.menu.item", () -> Names.item(MenuData.state(menuId()).getString("rewardItem"))));
        rows.add(reading("killreward.menu.held", KillRewardScreen::heldLabel));
        rows.add(action("killreward.menu.pick", "killreward.menu.action.pick", this::pickHeld));
        rows.add(number("killreward.menu.amount", () -> MenuData.state(menuId()).getInt("rewardAmount"),
                value -> send(COMMAND + " setamount " + value), 1, MAX_AMOUNT, 1));
        rows.add(toggle("killreward.menu.team_kills", () -> MenuData.state(menuId()).getBoolean("rewardTeamKills"),
                value -> send(COMMAND + " teamkills " + value)));
        return rows;
    }

    private void pickHeld() {
        String id = heldItem();
        if (id.isEmpty()) {
            MenuFeedback.show(Component.translatable("killreward.menu.empty_hand"), true);
            return;
        }
        send(COMMAND + " setitem " + id);
    }

    private static Component heldLabel() {
        String id = heldItem();
        return id.isEmpty() ? Component.translatable("killreward.menu.empty_hand") : Names.item(id);
    }

}
