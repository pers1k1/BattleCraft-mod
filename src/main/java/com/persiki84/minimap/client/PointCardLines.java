package com.persiki84.minimap.client;

import com.persiki84.capturepoints.client.ClientCaptureData;
import com.persiki84.capturepoints.network.PointPerks;
import com.persiki84.sellmod.client.ClientSellData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

// WHY: строки окна точки собираются один раз на показ: что даёт точка, решено на сервере и
// WHY: пришло вместе с её владельцем, а окно в кадре только рисует готовое
final class PointCardLines {
    private static final int SECONDS_PER_MINUTE = 60;

    record Line(Component label, Component value, int tint) {}

    private PointCardLines() {}

    static List<Line> of(String name, boolean last) {
        List<Line> lines = new ArrayList<>();
        String owner = last ? ClientCaptureData.getAllFinalPointOwners().get(name)
                : ClientCaptureData.getAllPointOwners().get(name);
        lines.add(owned(owner));
        PointPerks perks = ClientCaptureData.getPerks(name);
        lines.add(line("role", role(name, last, perks)));
        if (perks == null) return lines;

        lines.add(line("score", last ? text("score.final") : perks.score() > 0
                ? text("score.value", perks.score()) : text("none")));
        if (!last) lines.add(line("income", income(perks)));
        lines.add(line("reward", perks.rewardAmount() > 0 && !perks.reward().isEmpty()
                ? text("pieces", perks.rewardAmount(), perks.reward().getHoverName()) : text("none")));
        if (!perks.buff().isEmpty()) lines.add(line("buff", buff(perks)));
        return lines;
    }

    private static Line owned(String owner) {
        if (owner == null) return line("owner", text("owner.none"));
        return new Line(text("owner"), Component.literal(owner), MapRenderUtil.getTeamColor(owner));
    }

    private static Component role(String name, boolean last, PointPerks perks) {
        Component kind = text(last ? "role.final" : ClientCaptureData.isPointRequired(name)
                ? "role.required" : "role.optional");
        if (perks == null) return kind;
        return text("role.value", kind, perks.captureSeconds());
    }

    private static Component income(PointPerks perks) {
        if (perks.incomeAmount() <= 0) return text("none");
        Component amount = perks.currencyIncome() ? ClientSellData.amountText(perks.incomeAmount())
                : text("pieces", perks.incomeAmount(), perks.income().getHoverName());
        return text("income.value", amount, every(perks.incomeSeconds()),
                Component.translatable(perks.payout().translationKey()));
    }

    private static Component every(int seconds) {
        if (seconds % SECONDS_PER_MINUTE == 0) return text("every.minutes", seconds / SECONDS_PER_MINUTE);
        return text("every.seconds", seconds);
    }

    private static Component buff(PointPerks perks) {
        ResourceLocation id = ResourceLocation.tryParse(perks.buff());
        MobEffect effect = id == null ? null : ForgeRegistries.MOB_EFFECTS.getValue(id);
        Component shown = effect == null ? Component.literal(perks.buff()) : effect.getDisplayName();
        return text("buff.value", shown, Component.translatable("enchantment.level." + (perks.buffLevel() + 1)));
    }

    private static Line line(String key, Component value) {
        return new Line(text(key), value, 0);
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("capturepoints.card." + key, arguments);
    }
}
