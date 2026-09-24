package com.persiki84.shared.client.menu.gunsmith;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

// WHY: мастерская обвесов одна на лут и на магазин: откуда оружие и какой командой правится,
// WHY: знает владелец данных, а показ и выбор обвеса живут в одном экране
public interface GunBench {
    List<ItemStack> guns();

    Component title();

    void attach(int gun, String option);

    void detach(int gun, String slot);

    void refresh();
}
