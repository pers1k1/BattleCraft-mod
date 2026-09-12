package com.persiki84.capturepoints.capture;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CaptureRewards {
    private static final Map<UUID, List<ItemStack>> pending = new HashMap<>();

    private CaptureRewards() {}

    public static void pay(MinecraftServer server, CaptureSession session, String team) {
        CapturePoint point = session.getPoint();
        ItemStack template = point.getReward();
        int amount = point.getRewardAmount();
        if (server == null || template == null || template.isEmpty() || amount <= 0) return;

        List<CaptureLedger.Entry> winners = session.getLedger().shareOf(team);
        if (winners.isEmpty()) return;

        int[] parts = portions(point.getRewardSplit(), amount, winners);
        float total = totalWeight(winners);
        for (int index = 0; index < winners.size(); index++) {
            if (parts[index] <= 0) continue;
            hand(server, point, winners.get(index), template, parts[index], total, point.getRewardSplit());
        }
    }

    private static int[] portions(RewardSplit split, int amount, List<CaptureLedger.Entry> winners) {
        int[] parts = new int[winners.size()];
        if (split == RewardSplit.EACH) {
            java.util.Arrays.fill(parts, amount);
            return parts;
        }
        return divide(amount, winners, split == RewardSplit.MERIT);
    }

    private static int[] divide(int amount, List<CaptureLedger.Entry> winners, boolean byWeight) {
        int size = winners.size();
        int[] parts = new int[size];
        double[] leftover = new double[size];
        double total = byWeight ? totalWeight(winners) : size;
        int handed = 0;

        for (int index = 0; index < size; index++) {
            double share = byWeight ? winners.get(index).weight() / total : 1.0 / size;
            double exact = amount * share;
            parts[index] = (int) Math.floor(exact);
            leftover[index] = exact - parts[index];
            handed += parts[index];
        }
        spread(parts, leftover, amount - handed);
        return parts;
    }

    private static void spread(int[] parts, double[] leftover, int remainder) {
        for (int given = 0; given < remainder; given++) {
            int best = -1;
            for (int index = 0; index < leftover.length; index++) {
                if (leftover[index] > 0.0 && (best < 0 || leftover[index] > leftover[best])) best = index;
            }
            if (best < 0) return;

            parts[best]++;
            leftover[best] = 0.0;
        }
    }

    private static float totalWeight(List<CaptureLedger.Entry> winners) {
        float total = 0.0f;
        for (CaptureLedger.Entry entry : winners) {
            total += entry.weight();
        }
        return total <= 0.0f ? 1.0f : total;
    }

    private static void hand(MinecraftServer server, CapturePoint point, CaptureLedger.Entry entry,
                             ItemStack template, int count, float total, RewardSplit split) {
        ItemStack payout = template.copy();
        payout.setCount(count);

        ServerPlayer player = server.getPlayerList().getPlayer(entry.playerId());
        if (player == null || !player.isAlive()) {
            park(entry.playerId(), payout);
            return;
        }

        deliver(player, payout);
        player.sendSystemMessage(message(point, payout, entry, total, split));
    }

    private static Component message(CapturePoint point, ItemStack payout, CaptureLedger.Entry entry,
                                     float total, RewardSplit split) {
        Component prize = Component.literal(payout.getCount() + "x " + payout.getHoverName().getString())
                .withStyle(ChatFormatting.GOLD);
        Component where = Component.literal(point.getName()).withStyle(ChatFormatting.YELLOW);

        if (split != RewardSplit.MERIT) {
            return Component.translatable("capturepoints.reward.paid", where, prize).withStyle(ChatFormatting.GREEN);
        }
        int percent = Math.round(entry.weight() / total * 100.0f);
        return Component.translatable("capturepoints.reward.paid_share", where, prize, percent)
                .withStyle(ChatFormatting.GREEN);
    }

    private static void park(UUID playerId, ItemStack payout) {
        pending.computeIfAbsent(playerId, id -> new ArrayList<>()).add(payout);
    }

    public static void flush(ServerPlayer player) {
        List<ItemStack> owed = pending.remove(player.getUUID());
        if (owed == null) return;

        for (ItemStack stack : owed) {
            deliver(player, stack);
        }
    }

    private static void deliver(ServerPlayer player, ItemStack stack) {
        ItemStack given = stack.copy();
        if (!player.getInventory().add(given)) {
            player.drop(given, false);
        }
    }

    public static void save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, List<ItemStack>> owed : pending.entrySet()) {
            for (ItemStack stack : owed.getValue()) {
                CompoundTag row = new CompoundTag();
                row.putUUID("player", owed.getKey());
                row.put("item", stack.save(new CompoundTag()));
                list.add(row);
            }
        }
        tag.put("pendingRewards", list);
    }

    public static void load(CompoundTag tag) {
        pending.clear();
        ListTag list = tag.getList("pendingRewards", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag row = list.getCompound(index);
            ItemStack stack = ItemStack.of(row.getCompound("item"));
            if (!stack.isEmpty()) park(row.getUUID("player"), stack);
        }
    }
}
