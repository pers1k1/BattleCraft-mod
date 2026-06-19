package com.persiki84.quarrymod.events;

import com.persiki84.quarrymod.QuarryMod;
import com.persiki84.quarrymod.data.QuarryBlockManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class QuarryEventHandler {

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            BlockPos pos = event.getPos();
            Player player = event.getPlayer();
            BlockState state = event.getState();
            String dimension = level.dimension().location().toString();

            if (QuarryMod.getInstance().getDataManager() == null) return;

            QuarryBlockManager manager = QuarryMod.getInstance().getDataManager().getBlockManager();

            if (manager.isQuarryBlock(pos, dimension)) {
                event.setCanceled(true);

                if (manager.isOnCooldown(pos, dimension)) {
                    long cooldown = manager.getRemainingCooldown(pos, dimension);
                    player.displayClientMessage(
                            Component.translatable("quarrymod.event.cooldown_remaining",
                                    Component.literal(String.valueOf(cooldown)).withStyle(ChatFormatting.WHITE)
                            ).withStyle(ChatFormatting.RED),
                            true
                    );
                    return;
                }

                ItemStack tool = player.getMainHandItem();
                if (!isValidTool(tool, state)) {
                    player.displayClientMessage(
                            Component.translatable("quarrymod.event.wrong_tool").withStyle(ChatFormatting.RED),
                            true
                    );
                    return;
                }

                ItemStack drop = manager.getDropForBlock(state.getBlock());
                if (!drop.isEmpty() && !player.isCreative()) {
                    player.getInventory().add(drop);
                }

                manager.handleBlockBreak(level, pos, state);

                event.setExpToDrop(0);
            }
        }
    }

    private boolean isValidTool(ItemStack tool, BlockState state) {
        if (state.is(Blocks.DIAMOND_ORE)) {
            return tool.getItem() == Items.IRON_PICKAXE ||
                    tool.getItem() == Items.DIAMOND_PICKAXE ||
                    tool.getItem() == Items.NETHERITE_PICKAXE;
        }

        return tool.getItem() == Items.STONE_PICKAXE ||
                tool.getItem() == Items.IRON_PICKAXE ||
                tool.getItem() == Items.DIAMOND_PICKAXE ||
                tool.getItem() == Items.NETHERITE_PICKAXE ||
                tool.getItem() == Items.GOLDEN_PICKAXE;
    }
}
