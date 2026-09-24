package com.persiki84.battlecraft.client.menu.panel.loot;

import com.persiki84.airdrop.loot.LootTable;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.shared.client.menu.MenuCommands;
import com.persiki84.shared.client.menu.MenuData;
import com.persiki84.shared.client.menu.gunsmith.GunBench;
import com.persiki84.shared.gunsmith.GunSmith;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class LootGunBench implements GunBench {
    private final String table;
    private final List<ItemStack> guns = new ArrayList<>();
    private final List<Integer> indices = new ArrayList<>();
    private List<LootSnapshot.Table> seen;

    public LootGunBench(String table) {
        this.table = table;
    }

    // WHY: список оружия пересобирается только на новом снимке: мастерская сравнивает стак по
    // WHY: ссылке, и сборка на каждый вызов заставляла бы её заново опрашивать слоты каждый кадр
    @Override
    public List<ItemStack> guns() {
        List<LootSnapshot.Table> now = LootSnapshot.all();
        if (now == seen) return guns;

        seen = now;
        guns.clear();
        indices.clear();
        LootSnapshot.Table found = LootSnapshot.find(table);
        if (found == null) return guns;

        LootTable loot = found.table();
        for (int index = 0; index < loot.entries().size(); index++) {
            ItemStack stack = LootSnapshot.stack(loot.entries().get(index));
            if (!GunSmith.isGun(stack)) continue;
            guns.add(stack);
            indices.add(index);
        }
        return guns;
    }

    public int positionOf(int entryIndex) {
        guns();
        return Math.max(0, indices.indexOf(entryIndex));
    }

    @Override
    public Component title() {
        return Component.literal(table);
    }

    @Override
    public void attach(int gun, String option) {
        run(gun, "attach \"" + option + "\"");
    }

    @Override
    public void detach(int gun, String slot) {
        run(gun, "detach " + slot);
    }

    private void run(int gun, String tail) {
        guns();
        if (gun < 0 || gun >= indices.size()) return;
        MenuCommands.run("airdrop loot " + table + " " + indices.get(gun) + " " + tail, ModuleMenuStates.LOOT);
    }

    @Override
    public void refresh() {
        MenuData.request(ModuleMenuStates.LOOT);
    }
}
