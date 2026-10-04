package com.persiki84.battlecraft.client.menu.hub;

import com.persiki84.battlecraft.menu.AnnounceMenuState;
import com.persiki84.battlecraft.menu.ConfigMenuState;
import com.persiki84.battlecraft.menu.ModuleMenuStates;
import com.persiki84.battlecraft.modules.ModuleId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

// WHY: раздел управления узнаётся по значку раньше, чем читается подпись, поэтому у каждого свой
// WHY: предмет; модуль берёт значок своего раздела, чтобы плитки в двух местах совпадали
enum HubSection {
    POINTS("battlecraft.menu.points", "capturepoints", "minecraft:beacon", ModuleId.CAPTURE_POINTS),
    ZONES("battlecraft.menu.zones", "zones", "minecraft:shield", ModuleId.ZONES),
    MARKS("battlecraft.menu.marks", "marks", "minecraft:compass", null),
    SHOP("battlecraft.menu.shop", ModuleMenuStates.SHOP, "minecraft:emerald", null),
    AIRDROP("battlecraft.menu.airdrop", ModuleMenuStates.AIRDROP, "minecraft:chest", ModuleId.AIRDROP),
    TEAMS("battlecraft.menu.teams", ModuleMenuStates.TEAMS, "minecraft:white_banner", null),
    RULES("battlecraft.menu.rules", ModuleMenuStates.GAME_RULES, "minecraft:book", null),
    MAP("battlecraft.menu.map", ModuleMenuStates.MAP, "minecraft:filled_map", ModuleId.MINIMAP),
    QUARRY("battlecraft.menu.quarry", ModuleMenuStates.QUARRY, "minecraft:iron_pickaxe", ModuleId.QUARRY),
    KILL_REWARD("battlecraft.menu.killreward", ModuleMenuStates.KILL_REWARD, "minecraft:diamond_sword",
            ModuleId.KILL_REWARD),
    SELL("battlecraft.menu.sell", ModuleMenuStates.SELL, "minecraft:gold_ingot", ModuleId.SELL),
    MODIFIERS("battlecraft.menu.modifiers", ModuleMenuStates.MODIFIERS, "minecraft:anvil", ModuleId.ITEM_MODIFIERS),
    IMMORTALITY("battlecraft.menu.immortality", ModuleMenuStates.IMMORTALITY, "minecraft:totem_of_undying",
            ModuleId.IMMORTALITY),
    KNOCKDOWN("battlecraft.menu.knockdown", ModuleMenuStates.KNOCKDOWN, "minecraft:bone", ModuleId.KNOCKDOWN),
    COMBAT("battlecraft.menu.combat", ModuleMenuStates.COMBAT, "minecraft:clock", ModuleId.COMBAT_TIMER),
    ANNOUNCE("battlecraft.menu.announce", AnnounceMenuState.MENU_ID, "minecraft:bell", null),
    CONFIGS("battlecraft.menu.configs", ConfigMenuState.MENU_ID, "minecraft:writable_book", null);

    private static final String DAMAGE_ICON = "minecraft:redstone";

    private final String label;
    private final String menuId;
    private final String icon;
    private final ModuleId module;

    HubSection(String label, String menuId, String icon, ModuleId module) {
        this.label = label;
        this.menuId = menuId;
        this.icon = icon;
        this.module = module;
    }

    String label() {
        return label;
    }

    String menuId() {
        return menuId;
    }

    ItemStack icon() {
        return stackOf(icon);
    }

    static HubSection of(ModuleId module) {
        for (HubSection section : values()) {
            if (section.module == module) return section;
        }
        return null;
    }

    static ItemStack iconOf(ModuleId module) {
        HubSection section = of(module);
        return section == null ? stackOf(DAMAGE_ICON) : section.icon();
    }

    static ItemStack stackOf(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Item item = key == null ? Items.AIR : BuiltInRegistries.ITEM.get(key);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }
}
