package com.persiki84.battlecraft.client.menu.panel.mode;

import com.persiki84.battlecraft.mode.ModeId;
import com.persiki84.battlecraft.mode.ModeMenuState;
import com.persiki84.shared.client.menu.MenuCommands;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.gunsmith.GunBench;
import com.persiki84.shared.gunsmith.GunSmith;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

// WHY: стартовый набор правится той же мастерской, что лут и магазин. Список оружия собирается
// WHY: только на новом снимке: мастерская сравнивает стак по ссылке и иначе опрашивала бы слоты каждый кадр
final class ModeGunBench implements GunBench {
    private final ModeId mode;
    private final List<ItemStack> guns = new ArrayList<>();
    private final List<Integer> indices = new ArrayList<>();
    private CompoundTag seen;

    ModeGunBench(ModeId mode) {
        this.mode = mode;
    }

    @Override
    public List<ItemStack> guns() {
        CompoundTag state = MenuData.state(ModeMenuState.MENU_ID);
        if (state == seen) return guns;

        seen = state;
        guns.clear();
        indices.clear();
        for (KitTile tile : ModeView.of(state).settings(mode).kit()) {
            ItemStack stack = tile.entry().stack();
            if (!GunSmith.isGun(stack)) continue;
            guns.add(stack);
            indices.add(tile.index());
        }
        return guns;
    }

    int positionOf(int entryIndex) {
        guns();
        return indices.indexOf(entryIndex);
    }

    @Override
    public Component title() {
        return Component.translatable(mode.translationKey());
    }

    @Override
    public void attach(int gun, String option) {
        run(gun, index -> ModeOrders.kitAttach(mode, index, option));
    }

    @Override
    public void detach(int gun, String slot) {
        run(gun, index -> ModeOrders.kitDetach(mode, index, slot));
    }

    private void run(int gun, IntFunction<String> command) {
        guns();
        if (gun < 0 || gun >= indices.size()) return;
        MenuCommands.run(command.apply(indices.get(gun)), ModeMenuState.MENU_ID);
    }

    @Override
    public void refresh() {
        MenuData.request(ModeMenuState.MENU_ID);
    }
}
