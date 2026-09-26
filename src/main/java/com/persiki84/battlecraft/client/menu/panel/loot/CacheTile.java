package com.persiki84.battlecraft.client.menu.panel.loot;

import com.persiki84.airdrop.cache.CacheTier;
import com.persiki84.shared.client.menu.studio.StudioTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public record CacheTile(int id, BlockPos pos, String dimension, String table, CacheTier tier, String refill,
                        int refillSeconds, boolean missing, boolean empty, ItemStack icon, Component name,
                        Component corner, Component plate) implements StudioTile {

    public static List<CacheTile> of(CompoundTag state) {
        ListTag stored = state.getList("caches", Tag.TAG_COMPOUND);
        List<CacheTile> tiles = new ArrayList<>();
        for (int index = 0; index < stored.size(); index++) {
            tiles.add(one(stored.getCompound(index)));
        }
        return tiles;
    }

    private static CacheTile one(CompoundTag cache) {
        int id = cache.getInt("id");
        BlockPos pos = BlockPos.of(cache.getLong("pos"));
        CacheTier tier = CacheTier.of(cache.getString("tier"));
        return new CacheTile(id, pos, cache.getString("dimension"), cache.getString("table"), tier,
                cache.getString("refill"), cache.getInt("refillSeconds"), cache.getBoolean("missing"),
                cache.getBoolean("empty"), icon(cache.getString("block")),
                Component.translatable("studio.cache.name", id),
                Component.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ()),
                Component.literal(cache.getString("table")));
    }

    // WHY: иконка идёт от блока контейнера, который сервер видит на месте тайника; строка из снимка
    // WHY: проверяется реестром, кривой или пустой идентификатор даёт обычный сундук, а не падение
    private static ItemStack icon(String block) {
        ResourceLocation key = block.isEmpty() ? null : ResourceLocation.tryParse(block);
        Item item = key == null ? Items.AIR : BuiltInRegistries.ITEM.get(key);
        return new ItemStack(item == Items.AIR ? Items.CHEST : item);
    }

    @Override
    public boolean faded() {
        return missing;
    }

    @Override
    public Component mark() {
        if (missing) return Component.translatable("studio.cache.missing_mark");
        return empty ? Component.translatable("studio.cache.empty_mark") : Component.empty();
    }
}
