package com.persiki84.capturepoints.treasury;

import com.persiki84.capturepoints.capture.CapturePointManager;
import com.persiki84.capturepoints.capture.IncomePayout;
import com.persiki84.capturepoints.network.PacketHandler;
import com.persiki84.capturepoints.network.TreasurySyncPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Team;
import net.minecraftforge.network.PacketDistributor;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// WHY: казна живёт на сервере, а не блоком в мире: воронка, вагонетка-воронка, поршень и взрыв до
// WHY: неё не дотягиваются, положить в неё игрок не может ничего, потому что пути внутрь нет, кроме
// WHY: дохода точек. Брать может только своя команда, и сервер сверяет это на каждом взятии
public final class TeamTreasury {
    public static final int SLOTS = 54;

    private static final String TAG = "treasury";
    private static final String TEAM = "team";
    private static final String ITEMS = "items";
    private static final Map<String, List<ItemStack>> vaults = new HashMap<>();

    private TeamTreasury() {}

    public static List<ItemStack> of(String team) {
        List<ItemStack> vault = team == null ? null : vaults.get(team);
        return vault == null ? List.of() : vault;
    }

    // WHY: доход сливается в стопки того же предмета до их предела. Мест SLOTS, излишек сверх них
    // WHY: пропадает: на долгом матче казна росла бы без конца вместе со снимком и файлом
    public static int deposit(String team, ItemStack income) {
        if (team == null || income.isEmpty()) return 0;

        List<ItemStack> vault = vaults.computeIfAbsent(team, unused -> new ArrayList<>());
        ItemStack rest = income.copy();
        mergeInto(vault, rest);
        while (!rest.isEmpty() && vault.size() < SLOTS) {
            vault.add(rest.split(rest.getMaxStackSize()));
        }
        return income.getCount() - rest.getCount();
    }

    private static void mergeInto(List<ItemStack> vault, ItemStack rest) {
        for (ItemStack held : vault) {
            if (rest.isEmpty()) return;
            if (!ItemStack.isSameItemSameTags(held, rest)) continue;

            int moved = Math.min(held.getMaxStackSize() - held.getCount(), rest.getCount());
            if (moved <= 0) continue;
            held.grow(moved);
            rest.shrink(moved);
        }
    }

    // WHY: сверяется предмет вместе с его данными: у соседних стопок бывает один id и разные зелья
    // WHY: или чары, и по одному id игрок унёс бы не ту стопку, что выбрал
    public static ItemStack peek(String team, int slot, ItemStack expected) {
        List<ItemStack> vault = team == null ? null : vaults.get(team);
        if (vault == null || slot < 0 || slot >= vault.size()) return ItemStack.EMPTY;

        ItemStack held = vault.get(slot);
        return ItemStack.isSameItemSameTags(held, expected) ? held.copy() : ItemStack.EMPTY;
    }

    // WHY: стопка берётся по месту, и сервер сверяет, что там всё ещё тот предмет: двое из команды
    // WHY: жмут разом, первый уносит стопку, и второй иначе забрал бы соседнюю. В руки идёт ровно то,
    // WHY: что влезло в инвентарь, остаток остаётся в казне: брошенное под ноги подобрал бы враг
    public static int take(ServerPlayer player, String team, int slot, ItemStack expected, int count) {
        List<ItemStack> vault = vaults.get(team);
        if (vault == null || slot < 0 || slot >= vault.size() || count <= 0) return 0;

        ItemStack held = vault.get(slot);
        if (!ItemStack.isSameItemSameTags(held, expected)) return 0;

        ItemStack piece = held.copyWithCount(Math.min(count, held.getCount()));
        int wanted = piece.getCount();
        player.getInventory().add(piece);
        int taken = wanted - piece.getCount();
        held.shrink(taken);
        if (held.isEmpty()) vault.remove(slot);
        return taken;
    }

    public static void forget() {
        vaults.clear();
    }

    public static void clearAll(MinecraftServer server) {
        if (vaults.isEmpty()) return;
        vaults.clear();
        CapturePointManager.persist();
        syncAll(server);
    }

    public static String teamOf(ServerPlayer player) {
        Team team = player.getTeam();
        return team == null ? null : team.getName();
    }

    public static void syncTeam(MinecraftServer server, String team) {
        if (server == null || team == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (team.equals(teamOf(player))) syncTo(player);
        }
    }

    public static void syncAll(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncTo(player);
        }
    }

    public static void syncTo(ServerPlayer player) {
        boolean enabled = CapturePointManager.incomePayout() == IncomePayout.TREASURY;
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new TreasurySyncPacket(enabled, List.copyOf(of(teamOf(player)))));
    }

    public static void save(CompoundTag tag) {
        ListTag teams = new ListTag();
        for (Map.Entry<String, List<ItemStack>> vault : vaults.entrySet()) {
            ListTag items = new ListTag();
            for (ItemStack stack : vault.getValue()) {
                items.add(stack.save(new CompoundTag()));
            }
            CompoundTag row = new CompoundTag();
            row.putString(TEAM, vault.getKey());
            row.put(ITEMS, items);
            teams.add(row);
        }
        tag.put(TAG, teams);
    }

    public static void load(CompoundTag tag) {
        vaults.clear();
        ListTag teams = tag.getList(TAG, Tag.TAG_COMPOUND);
        for (int index = 0; index < teams.size(); index++) {
            CompoundTag row = teams.getCompound(index);
            ListTag items = row.getList(ITEMS, Tag.TAG_COMPOUND);
            for (int item = 0; item < items.size(); item++) {
                deposit(row.getString(TEAM), ItemStack.of(items.getCompound(item)));
            }
        }
    }
}
