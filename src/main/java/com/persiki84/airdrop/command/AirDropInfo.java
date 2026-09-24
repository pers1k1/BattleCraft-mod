package com.persiki84.airdrop.command;

import com.mojang.brigadier.context.CommandContext;
import com.persiki84.airdrop.cache.LootCaches;
import com.persiki84.airdrop.config.AirDropConfig;
import com.persiki84.airdrop.loot.LootTables;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static com.persiki84.airdrop.config.AirDropLimits.PERCENT;
import static com.persiki84.airdrop.config.AirDropLimits.TICKS_PER_SECOND;

public final class AirDropInfo {

    private AirDropInfo() {}

    public static int info(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        AirDropConfig.Server config = AirDropConfig.SERVER;
        line(source, Component.translatable("airdrop.info.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        line(source, Component.translatable("airdrop.info.state",
                state(config.modEnabled.get()), state(config.autoSpawnEnabled.get()), state(config.matchOnly.get())));
        line(source, zone());
        line(source, Component.translatable("airdrop.info.timer", config.intervalSeconds.get(),
                Math.round(config.intervalSpawnChance.get() * PERCENT),
                config.flyingAnimTicks.get() / TICKS_PER_SECOND));
        String table = AirDropConfig.airdropTable();
        line(source, Component.translatable("airdrop.info.loot", table, LootTables.getOrEmpty(table).entries().size(),
                LootTables.names().size(), LootCaches.size()));
        return 1;
    }

    // WHY: при центре от спавна мира сохранённые X и Z ни на что не влияют, и показывать их
    // WHY: как действующий центр значит врать оператору
    private static Component zone() {
        AirDropConfig.Server config = AirDropConfig.SERVER;
        if (config.centerAtWorldSpawn.get()) {
            return Component.translatable("airdrop.info.zone_spawn", config.spawnRadius.get());
        }
        return Component.translatable("airdrop.info.zone", Math.round(config.centerX.get()),
                Math.round(config.centerZ.get()), config.spawnRadius.get());
    }

    private static Component state(boolean on) {
        return Component.translatable(on ? "airdrop.info.on" : "airdrop.info.off")
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static void line(CommandSourceStack source, Component text) {
        source.sendSuccess(() -> text, false);
    }
}
