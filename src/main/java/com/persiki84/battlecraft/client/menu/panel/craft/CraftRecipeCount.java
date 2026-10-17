package com.persiki84.battlecraft.client.menu.panel.craft;

import com.persiki84.battlecraft.craft.CraftBan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.crafting.Recipe;

// WHY: число считается по рецептам клиента, у которых есть образец результата. Особые рецепты
// WHY: (стрелы с зельем, фейерверк) собирают результат из сетки, и сосчитать их заранее нечем,
// WHY: хотя запрет закрывает и их. Считается один раз на выбор, не в кадре
final class CraftRecipeCount {
    private String countedKey;
    private int counted;

    int of(CraftTile tile) {
        if (tile == null) return 0;
        if (tile.key().equals(countedKey)) return counted;
        countedKey = tile.key();
        counted = count(tile.ban());
        return counted;
    }

    private static int count(CraftBan ban) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return 0;

        RegistryAccess access = level.registryAccess();
        int found = 0;
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            if (resultMatches(ban, recipe, access)) found++;
        }
        return found;
    }

    // WHY: образец результата у чужого рецепта может собираться из данных, которых на клиенте нет,
    // WHY: и бросить исключение: такой рецепт просто не попадает в счёт, экран от него не падает
    private static boolean resultMatches(CraftBan ban, Recipe<?> recipe, RegistryAccess access) {
        try {
            return ban.matches(recipe.getResultItem(access));
        } catch (RuntimeException unreadable) {
            return false;
        }
    }
}
