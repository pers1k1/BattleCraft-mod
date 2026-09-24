package com.persiki84.airdrop.cache;

import com.persiki84.airdrop.AirDropMod;
import com.persiki84.airdrop.config.AirDropConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class CacheGuard {

    // WHY: сломанный тайник рассыпает лут и пропадает из матча насовсем, поэтому ломать его может
    // WHY: только оператор в творческом режиме, и такое ломание снимает тайник из списка
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!AirDropMod.enabled() || !(event.getLevel() instanceof Level level)) return;

        LootCache cache = CacheContainers.covering(level, event.getPos());
        if (cache == null) return;

        Player player = event.getPlayer();
        if (player.isCreative() && player.hasPermissions(2)) {
            LootCaches.remove(cache.id());
            player.displayClientMessage(Component.translatable("airdrop.cache.removed_by_break", cache.id())
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        event.setCanceled(true);
        player.displayClientMessage(Component.translatable("airdrop.cache.unbreakable")
                .withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        if (!AirDropMod.enabled()) return;

        Level level = event.getLevel();
        event.getAffectedBlocks().removeIf(pos -> CacheContainers.covering(level, pos) != null);
    }

    // WHY: воронка под тайником с пополнением по таймеру это ферма без игрока: она выкачивает
    // WHY: каждое пополнение, пока хозяин стоит на базе
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!AirDropMod.enabled() || !(event.getLevel() instanceof Level level)) return;
        if (event.getEntity() instanceof Player player && player.isCreative()) return;

        String refusal = refusal(level, event.getPos(), event.getPlacedBlock());
        if (refusal == null) return;

        event.setCanceled(true);
        if (event.getEntity() instanceof Player player) {
            player.displayClientMessage(Component.translatable(refusal).withStyle(ChatFormatting.RED), true);
        }
    }

    // WHY: свой сундук вплотную к тайнику-одиночке склеивается с ним в двойной: лут начинал
    // WHY: раскладываться и в чужую половину, а защита тайника накрывала её целиком
    private static String refusal(Level level, BlockPos pos, BlockState placed) {
        if (placed.getBlock() instanceof HopperBlock && CacheContainers.covering(level, pos.above()) != null) {
            return "airdrop.cache.no_hopper";
        }
        if (!(placed.getBlock() instanceof ChestBlock) || placed.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return null;
        }
        BlockPos twin = pos.relative(ChestBlock.getConnectedDirection(placed));
        return LootCaches.at(level, twin) != null ? "airdrop.cache.no_twin" : null;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onOpen(PlayerInteractEvent.RightClickBlock event) {
        if (!AirDropMod.enabled() || !(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        LootCache cache = CacheContainers.covering(level, pos);
        if (cache == null) return;

        Player player = event.getEntity();
        boolean running = CacheTicker.running();
        if (!running && locked(player)) {
            event.setCanceled(true);
            if (event.getHand() == InteractionHand.MAIN_HAND) {
                player.displayClientMessage(Component.translatable("airdrop.cache.locked")
                        .withStyle(ChatFormatting.YELLOW), true);
            }
            return;
        }
        CacheTicker.settle(level, cache, CacheTicker.clock(level.getServer()), running);
    }

    private static boolean locked(Player player) {
        return AirDropConfig.SERVER.cacheLockOutsideMatch.get() && !player.isCreative() && !player.isSpectator();
    }
}
