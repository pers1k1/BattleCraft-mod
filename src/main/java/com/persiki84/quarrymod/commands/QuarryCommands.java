package com.persiki84.quarrymod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.persiki84.quarrymod.QuarryMod;
import com.persiki84.quarrymod.data.QuarryBlock;
import com.persiki84.quarrymod.data.QuarryBlockKey;
import com.persiki84.quarrymod.data.QuarryBlockManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public class QuarryCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("quarry")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("add")
                        .executes(QuarryCommands::addQuarryBlock))
                .then(Commands.literal("remove")
                        .executes(QuarryCommands::removeQuarryBlock))
                .then(Commands.literal("info")
                        .executes(QuarryCommands::getBlockInfo))
                .then(Commands.literal("list")
                        .executes(QuarryCommands::listQuarryBlocks))
                .then(Commands.literal("cooldown")
                        .executes(QuarryCommands::getCooldownInfo)
                        .then(Commands.literal("global")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                        .executes(QuarryCommands::setGlobalCooldown)))
                        .then(Commands.literal("set")
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                        .executes(QuarryCommands::setBlockCooldown)))
                        .then(Commands.literal("reset")
                                .executes(QuarryCommands::resetBlockCooldown)))
        );
    }

    private static int getCooldownInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        QuarryBlockManager manager = QuarryMod.getInstance().getDataManager().getBlockManager();

        source.sendSuccess(() -> Component.translatable("quarrymod.command.cooldown.header").withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.translatable("quarrymod.command.cooldown.global",
                Component.literal(String.valueOf(manager.getGlobalCooldown())).withStyle(ChatFormatting.WHITE)
        ).withStyle(ChatFormatting.GRAY), false);

        if (source.getEntity() instanceof ServerPlayer player) {
            BlockHitResult result = getPlayerLookingAt(player);
            if (result.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = result.getBlockPos();
                String dimension = player.level().dimension().location().toString();
                if (manager.isQuarryBlock(pos, dimension)) {
                    long custom = manager.getCustomCooldown(pos, dimension);
                    if (custom != -1) {
                        source.sendSuccess(() -> Component.translatable("quarrymod.command.cooldown.custom",
                                Component.literal(String.valueOf(custom)).withStyle(ChatFormatting.YELLOW)
                        ).withStyle(ChatFormatting.GRAY), false);
                    } else {
                        source.sendSuccess(() -> Component.translatable("quarrymod.command.cooldown.using_global",
                                Component.literal(String.valueOf(manager.getGlobalCooldown())).withStyle(ChatFormatting.WHITE)
                        ).withStyle(ChatFormatting.GRAY), false);
                    }
                }
            }
        }

        return 1;
    }

    private static int setGlobalCooldown(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int seconds = IntegerArgumentType.getInteger(context, "seconds");
        QuarryBlockManager manager = QuarryMod.getInstance().getDataManager().getBlockManager();

        if (manager.setGlobalCooldown(seconds)) {
            source.sendSuccess(() -> Component.translatable("quarrymod.command.cooldown.global_set",
                    Component.literal(String.valueOf(seconds)).withStyle(ChatFormatting.WHITE)
            ).withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendFailure(Component.translatable("quarrymod.command.cooldown.error_set").withStyle(ChatFormatting.RED));
        }

        return 1;
    }

    private static int setBlockCooldown(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (source.getEntity() instanceof ServerPlayer player) {
            BlockHitResult result = getPlayerLookingAt(player);

            if (result.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = result.getBlockPos();
                String dimension = player.level().dimension().location().toString();
                QuarryBlockManager manager = QuarryMod.getInstance().getDataManager().getBlockManager();

                if (manager.isQuarryBlock(pos, dimension)) {
                    int seconds = IntegerArgumentType.getInteger(context, "seconds");

                    if (manager.setCustomCooldown(pos, dimension, seconds)) {
                        source.sendSuccess(() -> Component.translatable("quarrymod.command.cooldown.block_set",
                                Component.literal(pos.toShortString()).withStyle(ChatFormatting.WHITE),
                                Component.literal(String.valueOf(seconds)).withStyle(ChatFormatting.WHITE)
                        ).withStyle(ChatFormatting.GREEN), false);
                    } else {
                        source.sendFailure(Component.translatable("quarrymod.command.cooldown.error_set").withStyle(ChatFormatting.RED));
                    }
                } else {
                    source.sendFailure(Component.translatable("quarrymod.command.block.not_quarry").withStyle(ChatFormatting.RED));
                }
            } else {
                source.sendFailure(Component.translatable("quarrymod.command.block.must_look").withStyle(ChatFormatting.RED));
            }
        }

        return 1;
    }

    private static int resetBlockCooldown(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (source.getEntity() instanceof ServerPlayer player) {
            BlockHitResult result = getPlayerLookingAt(player);

            if (result.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = result.getBlockPos();
                String dimension = player.level().dimension().location().toString();
                QuarryBlockManager manager = QuarryMod.getInstance().getDataManager().getBlockManager();

                if (manager.isQuarryBlock(pos, dimension)) {
                    manager.removeCustomCooldown(pos, dimension);
                    source.sendSuccess(() -> Component.translatable("quarrymod.command.cooldown.reset",
                            Component.literal(pos.toShortString()).withStyle(ChatFormatting.WHITE)
                    ).withStyle(ChatFormatting.GREEN), false);
                } else {
                    source.sendFailure(Component.translatable("quarrymod.command.block.not_quarry").withStyle(ChatFormatting.RED));
                }
            } else {
                source.sendFailure(Component.translatable("quarrymod.command.block.must_look").withStyle(ChatFormatting.RED));
            }
        }

        return 1;
    }

    private static int addQuarryBlock(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (source.getEntity() instanceof ServerPlayer player) {
            BlockHitResult result = getPlayerLookingAt(player);

            if (result.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = result.getBlockPos();
                BlockState state = player.level().getBlockState(pos);
                String dimension = player.level().dimension().location().toString();
                QuarryBlockManager manager = QuarryMod.getInstance().getDataManager().getBlockManager();

                if (manager.isValidQuarryBlock(state.getBlock())) {
                    if (!manager.isQuarryBlock(pos, dimension)) {
                        manager.addQuarryBlock(player.level(), pos);
                        source.sendSuccess(() -> Component.translatable("quarrymod.command.block.added",
                                Component.literal(pos.toShortString()).withStyle(ChatFormatting.WHITE)
                        ).withStyle(ChatFormatting.GREEN), false);
                    } else {
                        source.sendFailure(Component.translatable("quarrymod.command.block.already_quarry").withStyle(ChatFormatting.RED));
                    }
                } else {
                    source.sendFailure(Component.translatable("quarrymod.command.block.invalid").withStyle(ChatFormatting.RED));
                }
            } else {
                source.sendFailure(Component.translatable("quarrymod.command.block.must_look").withStyle(ChatFormatting.RED));
            }
        }

        return 1;
    }

    private static int removeQuarryBlock(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (source.getEntity() instanceof ServerPlayer player) {
            BlockHitResult result = getPlayerLookingAt(player);

            if (result.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = result.getBlockPos();
                String dimension = player.level().dimension().location().toString();
                QuarryBlockManager manager = QuarryMod.getInstance().getDataManager().getBlockManager();

                if (manager.isQuarryBlock(pos, dimension)) {
                    manager.removeQuarryBlock(pos, dimension);
                    source.sendSuccess(() -> Component.translatable("quarrymod.command.block.removed",
                            Component.literal(pos.toShortString()).withStyle(ChatFormatting.WHITE)
                    ).withStyle(ChatFormatting.GREEN), false);
                } else {
                    source.sendFailure(Component.translatable("quarrymod.command.block.not_quarry").withStyle(ChatFormatting.RED));
                }
            }
        }

        return 1;
    }

    private static int getBlockInfo(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (source.getEntity() instanceof ServerPlayer player) {
            BlockHitResult result = getPlayerLookingAt(player);

            if (result.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = result.getBlockPos();
                BlockState state = player.level().getBlockState(pos);
                String dimension = player.level().dimension().location().toString();
                QuarryBlockManager manager = QuarryMod.getInstance().getDataManager().getBlockManager();

                source.sendSuccess(() -> Component.translatable("quarrymod.command.info.header").withStyle(ChatFormatting.YELLOW), false);
                source.sendSuccess(() -> Component.translatable("quarrymod.command.info.position",
                        Component.literal(pos.toShortString()).withStyle(ChatFormatting.WHITE)
                ).withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.translatable("quarrymod.command.info.block_name",
                        Component.literal(state.getBlock().getName().getString()).withStyle(ChatFormatting.WHITE)
                ).withStyle(ChatFormatting.GRAY), false);

                if (manager.isQuarryBlock(pos, dimension)) {
                    source.sendSuccess(() -> Component.translatable("quarrymod.command.info.status_quarry").withStyle(ChatFormatting.GRAY), false);

                    long custom = manager.getCustomCooldown(pos, dimension);
                    if (custom != -1) {
                        source.sendSuccess(() -> Component.translatable("quarrymod.command.info.cooldown_custom",
                                Component.literal(String.valueOf(custom)).withStyle(ChatFormatting.YELLOW)
                        ).withStyle(ChatFormatting.GRAY), false);
                    } else {
                        source.sendSuccess(() -> Component.translatable("quarrymod.command.info.cooldown_global",
                                Component.literal(String.valueOf(manager.getGlobalCooldown())).withStyle(ChatFormatting.WHITE)
                        ).withStyle(ChatFormatting.GRAY), false);
                    }

                    if (manager.isOnCooldown(pos, dimension)) {
                        long cooldown = manager.getRemainingCooldown(pos, dimension);
                        source.sendSuccess(() -> Component.translatable("quarrymod.command.info.cooldown_remaining",
                                Component.literal(String.valueOf(cooldown)).withStyle(ChatFormatting.YELLOW)
                        ).withStyle(ChatFormatting.GRAY), false);
                    } else {
                        source.sendSuccess(() -> Component.translatable("quarrymod.command.info.cooldown_ready").withStyle(ChatFormatting.GRAY), false);
                    }
                } else {
                    source.sendSuccess(() -> Component.translatable("quarrymod.command.info.status_normal").withStyle(ChatFormatting.GRAY), false);
                }
            }
        }

        return 1;
    }

    private static int listQuarryBlocks(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        QuarryBlockManager manager = QuarryMod.getInstance().getDataManager().getBlockManager();
        Map<QuarryBlockKey, QuarryBlock> blocks = manager.getAllQuarryBlocks();

        if (blocks.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("quarrymod.command.list.empty").withStyle(ChatFormatting.RED), false);
        } else {
            source.sendSuccess(() -> Component.translatable("quarrymod.command.list.header",
                    Component.literal(String.valueOf(blocks.size())).withStyle(ChatFormatting.WHITE)
            ).withStyle(ChatFormatting.YELLOW), false);

            Map<String, Integer> blockCounts = new HashMap<>();

            for (Map.Entry<QuarryBlockKey, QuarryBlock> entry : blocks.entrySet()) {
                QuarryBlockKey key = entry.getKey();
                QuarryBlock block = entry.getValue();
                String blockName = block.getOriginalState().getBlock().getName().getString();
                blockCounts.put(blockName, blockCounts.getOrDefault(blockName, 0) + 1);

                long custom = manager.getCustomCooldown(key.pos(), key.dimension());
                Component cooldownComp = custom != -1
                        ? Component.literal(" [" + custom + "s]").withStyle(ChatFormatting.YELLOW)
                        : Component.empty();

                source.sendSuccess(() -> Component.translatable("quarrymod.command.list.entry",
                        Component.literal(blockName),
                        Component.literal(key.pos().toShortString()).withStyle(ChatFormatting.WHITE),
                        Component.literal(key.dimension()).withStyle(ChatFormatting.DARK_GRAY),
                        cooldownComp
                ).withStyle(ChatFormatting.GRAY), false);
            }

            source.sendSuccess(() -> Component.translatable("quarrymod.command.list.stats_header").withStyle(ChatFormatting.YELLOW), false);
            for (Map.Entry<String, Integer> entry : blockCounts.entrySet()) {
                source.sendSuccess(() -> Component.translatable("quarrymod.command.list.stat_entry",
                        Component.literal(entry.getKey()),
                        Component.literal(String.valueOf(entry.getValue())).withStyle(ChatFormatting.WHITE)
                ).withStyle(ChatFormatting.GRAY), false);
            }

            source.sendSuccess(() -> Component.translatable("quarrymod.command.list.global_cooldown",
                    Component.literal(String.valueOf(manager.getGlobalCooldown())).withStyle(ChatFormatting.WHITE)
            ).withStyle(ChatFormatting.GRAY), false);
        }

        return 1;
    }

    private static BlockHitResult getPlayerLookingAt(ServerPlayer player) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 reachVec = eyePos.add(lookVec.x * 5, lookVec.y * 5, lookVec.z * 5);

        return player.level().clip(new ClipContext(
                eyePos, reachVec,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));
    }
}
