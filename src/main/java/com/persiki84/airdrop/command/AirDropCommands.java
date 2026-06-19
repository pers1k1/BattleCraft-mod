package com.persiki84.airdrop.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.persiki84.airdrop.config.AirDropConfig;
import com.persiki84.airdrop.entity.AirDropEntity;
import com.persiki84.airdrop.entity.ModEntities;
import com.persiki84.airdrop.loot.AirDropLootManager;
import com.persiki84.airdrop.scheduler.AirDropScheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.CompoundTagArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AirDropCommands {

    private AirDropCommands() {}

    private static final ResourceLocation GLOBAL_TABLE = new ResourceLocation("airdrop", "global");

    public static void register(CommandDispatcher<CommandSourceStack> d, CommandBuildContext buildContext) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("airdrop")
                .requires(s -> s.hasPermission(2));

        root.then(Commands.literal("info")
                .executes(ctx -> {
                    boolean modEnabled = AirDropConfig.SERVER.modEnabled.get();
                    boolean spawnEnabled = AirDropConfig.SERVER.autoSpawnEnabled.get();
                    double x = AirDropConfig.SERVER.centerX.get();
                    double z = AirDropConfig.SERVER.centerZ.get();
                    int r = AirDropConfig.SERVER.spawnRadius.get();
                    int interval = AirDropConfig.SERVER.intervalSeconds.get();
                    double chance = AirDropConfig.SERVER.intervalSpawnChance.get();
                    int lootSize = AirDropLootManager.getTable(GLOBAL_TABLE).size();
                    int fallTime = AirDropConfig.SERVER.flyingAnimTicks.get() / 20;

                    var src = ctx.getSource();
                    src.sendSuccess(() -> Component.translatable("airdrop.info.header").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
                    src.sendSuccess(() -> Component.translatable("airdrop.info.mod_status")
                            .append(Component.translatable(modEnabled ? "airdrop.info.enabled" : "airdrop.info.disabled").withStyle(modEnabled ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
                    src.sendSuccess(() -> Component.translatable("airdrop.info.auto_spawn")
                            .append(Component.translatable(spawnEnabled ? "airdrop.info.active" : "airdrop.info.inactive").withStyle(spawnEnabled ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
                    src.sendSuccess(() -> Component.translatable("airdrop.info.zone").withStyle(ChatFormatting.YELLOW)
                            .append(Component.translatable("airdrop.info.zone_detail", (int)x, (int)z, r).withStyle(ChatFormatting.WHITE)), false);
                    src.sendSuccess(() -> Component.translatable("airdrop.info.timer").withStyle(ChatFormatting.AQUA)
                            .append(Component.translatable("airdrop.info.timer_detail", interval, (int)(chance * 100)).withStyle(ChatFormatting.WHITE)), false);
                    src.sendSuccess(() -> Component.translatable("airdrop.info.flight_time").withStyle(ChatFormatting.AQUA)
                            .append(Component.translatable("airdrop.info.flight_time_value", fallTime).withStyle(ChatFormatting.WHITE)), false);
                    src.sendSuccess(() -> Component.translatable("airdrop.info.loot_items").withStyle(ChatFormatting.LIGHT_PURPLE)
                            .append(Component.literal(String.valueOf(lootSize)).withStyle(ChatFormatting.WHITE)), false);
                    return 1;
                }));

        root.then(Commands.literal("kill_all")
                .executes(ctx -> {
                    var server = ctx.getSource().getServer();
                    int count = 0;
                    for (ServerLevel level : server.getAllLevels()) {
                        List<Entity> toRemove = new ArrayList<>();
                        for (Entity entity : level.getAllEntities()) {
                            if (entity instanceof AirDropEntity) {
                                toRemove.add(entity);
                            }
                        }
                        for (Entity e : toRemove) {
                            e.discard();
                            count++;
                        }
                    }
                    final int deletedCount = count;
                    ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.kill_all.success", deletedCount).withStyle(ChatFormatting.RED), true);
                    return 1;
                }));

        root.then(Commands.literal("toggle")
                .then(Commands.literal("mod")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean val = BoolArgumentType.getBool(ctx, "enabled");
                                    AirDropConfig.SERVER.modEnabled.set(val);
                                    AirDropConfig.SERVER.modEnabled.save();
                                    ctx.getSource().sendSuccess(() -> Component.translatable(val ? "airdrop.toggle.mod_enabled" : "airdrop.toggle.mod_disabled").withStyle(val ? ChatFormatting.GREEN : ChatFormatting.RED), true);
                                    return 1;
                                })))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean val = BoolArgumentType.getBool(ctx, "enabled");
                                    AirDropConfig.SERVER.autoSpawnEnabled.set(val);
                                    AirDropConfig.SERVER.autoSpawnEnabled.save();
                                    ctx.getSource().sendSuccess(() -> Component.translatable(val ? "airdrop.toggle.spawn_enabled" : "airdrop.toggle.spawn_disabled").withStyle(val ? ChatFormatting.GREEN : ChatFormatting.RED), true);
                                    return 1;
                                }))));

        root.then(Commands.literal("reload")
                .executes(ctx -> {
                    AirDropLootManager.reload(FMLPaths.CONFIGDIR.get());
                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.airdrop.loot_reloaded").withStyle(ChatFormatting.GREEN), true);
                    return 1;
                }));

        root.then(Commands.literal("now")
                .executes(ctx -> {
                    if (!AirDropConfig.SERVER.modEnabled.get()) {
                        ctx.getSource().sendFailure(Component.translatable("airdrop.command.mod_disabled"));
                        return 0;
                    }
                    var level = ctx.getSource().getLevel();
                    BlockPos landing = AirDropScheduler.spawn(level, null);
                    if (landing != null) {
                        final BlockPos finalLanding = landing;
                        ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.now.success").withStyle(ChatFormatting.GREEN)
                                .append(Component.translatable("airdrop.now.landing_pos", finalLanding.getX(), finalLanding.getY(), finalLanding.getZ()).withStyle(ChatFormatting.GOLD)), true);
                    } else {
                        ctx.getSource().sendFailure(Component.translatable("airdrop.now.failed"));
                    }
                    return 1;
                }));

        root.then(Commands.literal("spawn")
                .executes(ctx -> spawnManual(ctx.getSource(), null))
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> spawnManual(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos")))));

        root.then(Commands.literal("config")
                .then(Commands.literal("center")
                        .then(Commands.literal("here")
                                .executes(ctx -> {
                                    var pos = ctx.getSource().getPosition();
                                    int x = (int) Math.floor(pos.x);
                                    int z = (int) Math.floor(pos.z);
                                    AirDropConfig.SERVER.centerX.set((double) x);
                                    AirDropConfig.SERVER.centerZ.set((double) z);
                                    AirDropConfig.SERVER.centerX.save();
                                    AirDropConfig.SERVER.centerZ.save();
                                    ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.config.center_set", x, z).withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                }))
                        .then(Commands.argument("x", IntegerArgumentType.integer(-30000000, 30000000))
                                .then(Commands.argument("z", IntegerArgumentType.integer(-30000000, 30000000))
                                        .executes(ctx -> {
                                            int x = IntegerArgumentType.getInteger(ctx, "x");
                                            int z = IntegerArgumentType.getInteger(ctx, "z");
                                            AirDropConfig.SERVER.centerX.set((double) x);
                                            AirDropConfig.SERVER.centerZ.set((double) z);
                                            AirDropConfig.SERVER.centerX.save();
                                            AirDropConfig.SERVER.centerZ.save();
                                            ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.config.center_set", x, z).withStyle(ChatFormatting.GREEN), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("radius")
                        .then(Commands.argument("blocks", IntegerArgumentType.integer(10, 30000))
                                .executes(ctx -> {
                                    int r = IntegerArgumentType.getInteger(ctx, "blocks");
                                    AirDropConfig.SERVER.spawnRadius.set(r);
                                    AirDropConfig.SERVER.spawnRadius.save();
                                    ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.config.radius_set", r).withStyle(ChatFormatting.AQUA), true);
                                    return 1;
                                })))
                .then(Commands.literal("interval")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 86400))
                                .executes(ctx -> {
                                    int s = IntegerArgumentType.getInteger(ctx, "seconds");
                                    AirDropConfig.SERVER.intervalSeconds.set(s);
                                    AirDropConfig.SERVER.intervalSeconds.save();
                                    ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.config.interval_set", s).withStyle(ChatFormatting.AQUA), true);
                                    return 1;
                                })))
                .then(Commands.literal("chance")
                        .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                .executes(ctx -> {
                                    int p = IntegerArgumentType.getInteger(ctx, "percent");
                                    AirDropConfig.SERVER.intervalSpawnChance.set(p / 100.0);
                                    AirDropConfig.SERVER.intervalSpawnChance.save();
                                    ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.config.chance_set", p).withStyle(ChatFormatting.AQUA), true);
                                    return 1;
                                })))
                .then(Commands.literal("flight_time")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 300))
                                .executes(ctx -> {
                                    int s = IntegerArgumentType.getInteger(ctx, "seconds");
                                    AirDropConfig.SERVER.flyingAnimTicks.set(s * 20);
                                    AirDropConfig.SERVER.flyingAnimTicks.save();
                                    ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.config.flight_time_set", s).withStyle(ChatFormatting.AQUA), true);
                                    return 1;
                                })))
                .then(Commands.literal("open_delay")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 300))
                                .executes(ctx -> {
                                    int s = IntegerArgumentType.getInteger(ctx, "seconds");
                                    AirDropConfig.SERVER.autoOpenDelayTicks.set(s * 20);
                                    AirDropConfig.SERVER.autoOpenDelayTicks.save();
                                    ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.config.open_delay_set", s).withStyle(ChatFormatting.AQUA), true);
                                    return 1;
                                })))
                .then(Commands.literal("despawn_empty")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 86400))
                                .executes(ctx -> {
                                    int s = IntegerArgumentType.getInteger(ctx, "seconds");
                                    AirDropConfig.SERVER.despawnEmptySeconds.set(s);
                                    AirDropConfig.SERVER.despawnEmptySeconds.save();
                                    ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.config.despawn_empty_set", s).withStyle(ChatFormatting.AQUA), true);
                                    return 1;
                                })))
                .then(Commands.literal("despawn_filled")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 86400))
                                .executes(ctx -> {
                                    int s = IntegerArgumentType.getInteger(ctx, "seconds");
                                    AirDropConfig.SERVER.despawnFilledSeconds.set(s);
                                    AirDropConfig.SERVER.despawnFilledSeconds.save();
                                    ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.config.despawn_filled_set", s).withStyle(ChatFormatting.AQUA), true);
                                    return 1;
                                })))
                .then(Commands.literal("warn_time")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 3600))
                                .executes(ctx -> {
                                    int s = IntegerArgumentType.getInteger(ctx, "seconds");
                                    AirDropConfig.SERVER.notificationSecondsBeforeDespawn.set(s);
                                    AirDropConfig.SERVER.notificationSecondsBeforeDespawn.save();
                                    ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.config.warn_time_set", s).withStyle(ChatFormatting.AQUA), true);
                                    return 1;
                                }))));

        root.then(Commands.literal("loot")
                .then(Commands.literal("list")
                        .executes(ctx -> {
                            List<AirDropLootManager.LootEntry> list = AirDropLootManager.getTable(GLOBAL_TABLE);
                            if (list.isEmpty()) {
                                ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.loot.empty").withStyle(ChatFormatting.RED), false);
                                return 1;
                            }
                            ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.loot.list_header", list.size()).withStyle(ChatFormatting.GOLD), false);
                            for (int i = 0; i < list.size(); i++) {
                                AirDropLootManager.LootEntry e = list.get(i);
                                final int idx = i;
                                int percent = (int)(e.chance() * 100);
                                ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.loot.entry_index", idx).withStyle(ChatFormatting.YELLOW)
                                        .append(Component.translatable(BuiltInRegistries.ITEM.get(e.itemId()).getDescriptionId()).withStyle(ChatFormatting.WHITE))
                                        .append(Component.translatable("airdrop.loot.count_range", e.min(), e.max()).withStyle(ChatFormatting.GRAY))
                                        .append(Component.translatable("airdrop.loot.chance", percent).withStyle(ChatFormatting.GREEN))
                                        .append(e.nbt() != null ? Component.translatable("airdrop.loot.nbt_tag").withStyle(ChatFormatting.DARK_PURPLE) : Component.empty()), false);
                            }
                            return 1;
                        }))
                .then(Commands.literal("add")
                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                .then(Commands.argument("min", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("max", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("percent", IntegerArgumentType.integer(1, 100))
                                                        .executes(ctx -> {
                                                            ItemInput input = ItemArgument.getItem(ctx, "item");
                                                            ItemStack one = input.createItemStack(1, false);
                                                            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(one.getItem());
                                                            String nbtString = null;
                                                            if (one.hasTag()) {
                                                                nbtString = one.getTag().toString();
                                                            }
                                                            int min = IntegerArgumentType.getInteger(ctx, "min");
                                                            int max = IntegerArgumentType.getInteger(ctx, "max");
                                                            int percent = IntegerArgumentType.getInteger(ctx, "percent");

                                                            if (max < min) {
                                                                ctx.getSource().sendFailure(Component.translatable("airdrop.loot.error_max_min").withStyle(ChatFormatting.RED));
                                                                return 0;
                                                            }

                                                            List<AirDropLootManager.LootEntry> list = new ArrayList<>(AirDropLootManager.getTable(GLOBAL_TABLE));
                                                            list.add(new AirDropLootManager.LootEntry(itemId, min, max, percent / 100.0F, nbtString));
                                                            AirDropLootManager.setTable(GLOBAL_TABLE, list);
                                                            AirDropLootManager.saveTable(GLOBAL_TABLE, FMLPaths.CONFIGDIR.get());

                                                            ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.loot.added").withStyle(ChatFormatting.GREEN)
                                                                    .append(one.getHoverName())
                                                                    .append(Component.translatable("airdrop.loot.count_range", min, max))
                                                                    .append(Component.translatable("airdrop.loot.chance", percent))
                                                                    .append(one.hasTag() ? Component.translatable("airdrop.loot.nbt_tag") : Component.empty()), true);
                                                            return 1;
                                                        }))))))
                .then(Commands.literal("clear")
                        .executes(ctx -> {
                            AirDropLootManager.setTable(GLOBAL_TABLE, List.of());
                            AirDropLootManager.saveTable(GLOBAL_TABLE, FMLPaths.CONFIGDIR.get());
                            ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.loot.cleared").withStyle(ChatFormatting.RED), true);
                            return 1;
                        }))
                .then(Commands.literal("remove")
                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    int idx = IntegerArgumentType.getInteger(ctx, "index");
                                    List<AirDropLootManager.LootEntry> list = new ArrayList<>(AirDropLootManager.getTable(GLOBAL_TABLE));

                                    if (idx < 0 || idx >= list.size()) {
                                        ctx.getSource().sendFailure(Component.translatable("airdrop.loot.invalid_index_range", list.size() - 1).withStyle(ChatFormatting.RED));
                                        return 0;
                                    }

                                    AirDropLootManager.LootEntry removed = list.remove(idx);
                                    AirDropLootManager.setTable(GLOBAL_TABLE, list);
                                    AirDropLootManager.saveTable(GLOBAL_TABLE, FMLPaths.CONFIGDIR.get());

                                    ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.loot.removed", idx).withStyle(ChatFormatting.GREEN)
                                            .append(Component.translatable(BuiltInRegistries.ITEM.get(removed.itemId()).getDescriptionId()).withStyle(ChatFormatting.WHITE)), true);
                                    return 1;
                                })))
                .then(Commands.literal("edit")
                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                .then(Commands.literal("count")
                                        .then(Commands.argument("min", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("max", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> {
                                                            int idx = IntegerArgumentType.getInteger(ctx, "index");
                                                            int min = IntegerArgumentType.getInteger(ctx, "min");
                                                            int max = IntegerArgumentType.getInteger(ctx, "max");

                                                            if (max < min) {
                                                                ctx.getSource().sendFailure(Component.translatable("airdrop.loot.error_max_min").withStyle(ChatFormatting.RED));
                                                                return 0;
                                                            }
                                                            List<AirDropLootManager.LootEntry> list = new ArrayList<>(AirDropLootManager.getTable(GLOBAL_TABLE));
                                                            if (idx < 0 || idx >= list.size()) {
                                                                ctx.getSource().sendFailure(Component.translatable("airdrop.loot.invalid_index").withStyle(ChatFormatting.RED));
                                                                return 0;
                                                            }
                                                            AirDropLootManager.LootEntry old = list.get(idx);
                                                            list.set(idx, new AirDropLootManager.LootEntry(old.itemId(), min, max, old.chance(), old.nbt()));
                                                            AirDropLootManager.setTable(GLOBAL_TABLE, list);
                                                            AirDropLootManager.saveTable(GLOBAL_TABLE, FMLPaths.CONFIGDIR.get());
                                                            ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.loot.updated_count", idx).withStyle(ChatFormatting.GREEN)
                                                                    .append(Component.translatable("airdrop.loot.count_label", min, max).withStyle(ChatFormatting.WHITE)), true);
                                                            return 1;
                                                        }))))
                                .then(Commands.literal("chance")
                                        .then(Commands.argument("percent", IntegerArgumentType.integer(1, 100))
                                                .executes(ctx -> {
                                                    int idx = IntegerArgumentType.getInteger(ctx, "index");
                                                    int percent = IntegerArgumentType.getInteger(ctx, "percent");
                                                    List<AirDropLootManager.LootEntry> list = new ArrayList<>(AirDropLootManager.getTable(GLOBAL_TABLE));
                                                    if (idx < 0 || idx >= list.size()) {
                                                        ctx.getSource().sendFailure(Component.translatable("airdrop.loot.invalid_index").withStyle(ChatFormatting.RED));
                                                        return 0;
                                                    }
                                                    AirDropLootManager.LootEntry old = list.get(idx);
                                                    list.set(idx, new AirDropLootManager.LootEntry(old.itemId(), old.min(), old.max(), percent / 100.0F, old.nbt()));
                                                    AirDropLootManager.setTable(GLOBAL_TABLE, list);
                                                    AirDropLootManager.saveTable(GLOBAL_TABLE, FMLPaths.CONFIGDIR.get());
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.loot.updated_chance", idx, percent).withStyle(ChatFormatting.GREEN), true);
                                                    return 1;
                                                })))
                                .then(Commands.literal("nbt")
                                        .then(Commands.argument("nbt_data", CompoundTagArgument.compoundTag())
                                                .executes(ctx -> {
                                                    int idx = IntegerArgumentType.getInteger(ctx, "index");
                                                    CompoundTag nbtTag = CompoundTagArgument.getCompoundTag(ctx, "nbt_data");
                                                    List<AirDropLootManager.LootEntry> list = new ArrayList<>(AirDropLootManager.getTable(GLOBAL_TABLE));
                                                    if (idx < 0 || idx >= list.size()) {
                                                        ctx.getSource().sendFailure(Component.translatable("airdrop.loot.invalid_index").withStyle(ChatFormatting.RED));
                                                        return 0;
                                                    }
                                                    String nbtString = nbtTag.toString();
                                                    AirDropLootManager.LootEntry old = list.get(idx);
                                                    list.set(idx, new AirDropLootManager.LootEntry(old.itemId(), old.min(), old.max(), old.chance(), nbtString));
                                                    AirDropLootManager.setTable(GLOBAL_TABLE, list);
                                                    AirDropLootManager.saveTable(GLOBAL_TABLE, FMLPaths.CONFIGDIR.get());
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.loot.updated_nbt", idx).withStyle(ChatFormatting.GREEN)
                                                            .append(Component.translatable("airdrop.loot.nbt_tag").withStyle(ChatFormatting.DARK_PURPLE)), true);
                                                    return 1;
                                                })))
                                .then(Commands.literal("clear_nbt")
                                        .executes(ctx -> {
                                            int idx = IntegerArgumentType.getInteger(ctx, "index");
                                            List<AirDropLootManager.LootEntry> list = new ArrayList<>(AirDropLootManager.getTable(GLOBAL_TABLE));
                                            if (idx < 0 || idx >= list.size()) {
                                                ctx.getSource().sendFailure(Component.translatable("airdrop.loot.invalid_index").withStyle(ChatFormatting.RED));
                                                return 0;
                                            }
                                            AirDropLootManager.LootEntry old = list.get(idx);
                                            list.set(idx, new AirDropLootManager.LootEntry(old.itemId(), old.min(), old.max(), old.chance(), null));
                                            AirDropLootManager.setTable(GLOBAL_TABLE, list);
                                            AirDropLootManager.saveTable(GLOBAL_TABLE, FMLPaths.CONFIGDIR.get());
                                            ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.loot.cleared_nbt", idx).withStyle(ChatFormatting.YELLOW), true);
                                            return 1;
                                        }))))
                .then(Commands.literal("duplicate")
                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    int idx = IntegerArgumentType.getInteger(ctx, "index");
                                    List<AirDropLootManager.LootEntry> list = new ArrayList<>(AirDropLootManager.getTable(GLOBAL_TABLE));
                                    if (idx < 0 || idx >= list.size()) {
                                        ctx.getSource().sendFailure(Component.translatable("airdrop.loot.invalid_index").withStyle(ChatFormatting.RED));
                                        return 0;
                                    }
                                    AirDropLootManager.LootEntry original = list.get(idx);
                                    list.add(new AirDropLootManager.LootEntry(original.itemId(), original.min(), original.max(), original.chance(), original.nbt()));
                                    AirDropLootManager.setTable(GLOBAL_TABLE, list);
                                    AirDropLootManager.saveTable(GLOBAL_TABLE, FMLPaths.CONFIGDIR.get());
                                    final int newIdx = list.size() - 1;
                                    ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.loot.duplicated", idx, newIdx).withStyle(ChatFormatting.AQUA), true);
                                    return 1;
                                })))
                .then(Commands.literal("swap")
                        .then(Commands.argument("index1", IntegerArgumentType.integer(0))
                                .then(Commands.argument("index2", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            int idx1 = IntegerArgumentType.getInteger(ctx, "index1");
                                            int idx2 = IntegerArgumentType.getInteger(ctx, "index2");
                                            List<AirDropLootManager.LootEntry> list = new ArrayList<>(AirDropLootManager.getTable(GLOBAL_TABLE));
                                            if (idx1 < 0 || idx1 >= list.size() || idx2 < 0 || idx2 >= list.size()) {
                                                ctx.getSource().sendFailure(Component.translatable("airdrop.loot.invalid_index").withStyle(ChatFormatting.RED));
                                                return 0;
                                            }
                                            Collections.swap(list, idx1, idx2);
                                            AirDropLootManager.setTable(GLOBAL_TABLE, list);
                                            AirDropLootManager.saveTable(GLOBAL_TABLE, FMLPaths.CONFIGDIR.get());
                                            ctx.getSource().sendSuccess(() -> Component.translatable("airdrop.loot.swapped", idx1, idx2).withStyle(ChatFormatting.LIGHT_PURPLE), true);
                                            return 1;
                                        })))));

        d.register(root);
    }

    private static int spawnManual(CommandSourceStack src, BlockPos posOrNull) {
        if (!AirDropConfig.SERVER.modEnabled.get()) {
            src.sendFailure(Component.translatable("airdrop.command.mod_disabled"));
            return 0;
        }

        var level = src.getLevel();
        double baseX = (posOrNull != null ? posOrNull.getX() + 0.5 : src.getPosition().x);
        double baseZ = (posOrNull != null ? posOrNull.getZ() + 0.5 : src.getPosition().z);

        int spawnY = AirDropConfig.SERVER.maxSpawnY.get();

        BlockPos landingPos = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos((int)baseX, 0, (int)baseZ)
        );

        double distance = spawnY - landingPos.getY();
        if (distance <= 0) distance = 10;

        BlockPos spawnPos = new BlockPos((int)baseX, spawnY, (int)baseZ);
        AirDropEntity entity = new AirDropEntity(ModEntities.AIRDROP.get(), level);
        entity.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

        entity.setYRot(level.random.nextFloat() * 360f);

        int animTicks = AirDropConfig.SERVER.flyingAnimTicks.get();
        if (animTicks < 20) animTicks = 20;

        double calculatedSpeed = distance / (double) animTicks;
        entity.setFallSpeed((float) calculatedSpeed);
        entity.setFlyingAnimTicks(animTicks);

        AirDropLootManager.fillInventory(level.random, entity.getInventory(), new ResourceLocation("airdrop", "global"));

        level.addFreshEntity(entity);

        src.sendSuccess(() -> Component.translatable("airdrop.spawn.success").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
}
