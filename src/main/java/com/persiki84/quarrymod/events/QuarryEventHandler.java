package com.persiki84.quarrymod.events;

import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import com.persiki84.quarrymod.QuarryMod;
import com.persiki84.quarrymod.block.QuarryBlocks;
import com.persiki84.quarrymod.data.QuarryBlockManager;
import com.persiki84.quarrymod.network.QuarryBroadcast;
import com.persiki84.shared.ActionGate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

public class QuarryEventHandler {
    private static final int EXPLOSION_CHUNK_REACH = 2;
    private static final int PISTON_CHUNK_REACH = 1;
    private static final String KNOCK_GATE = "quarryKnock";
    private static final int KNOCK_TICKS = 12;
    private static final float MINING_EXHAUSTION = 0.005F;

    // WHY: карьер выдаёт награду и отменяет ломание сам, поэтому слушает последним: запреты лобби,
    // WHY: зон и защиты точек обязаны успеть отменить событие раньше, иначе порядок регистрации решал,
    // WHY: копается ли руда внутри защищённой точки
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!ModuleSwitches.allows(ModuleId.QUARRY)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        QuarryBlockManager manager = manager();
        if (manager == null) return;

        BlockPos pos = event.getPos();
        String dimension = level.dimension().location().toString();
        if (!manager.isQuarryBlock(pos, dimension)) return;

        event.setCanceled(true);
        mine(level, manager, event, pos, dimension);
    }

    private void mine(ServerLevel level, QuarryBlockManager manager, BlockEvent.BreakEvent event,
                      BlockPos pos, String dimension) {
        Player player = event.getPlayer();
        BlockState state = event.getState();
        Block ore = state.getBlock();

        if (manager.isOnCooldown(pos, dimension)) {
            int left = (int) manager.getRemainingCooldown(pos, dimension);
            refuse(player, pos, ore, left, "quarrymod.event.cooldown_remaining", left);
            return;
        }
        if (!suitableTool(player.getMainHandItem(), state)) {
            refuse(player, pos, ore, 0, "quarrymod.event.wrong_tool");
            return;
        }
        if (!manager.claim(pos, dimension)) {
            refuse(player, pos, ore, 0, "quarrymod.event.taken");
            return;
        }

        award(player, manager.getDropForBlock(ore));
        wear(level, player, state, pos);
        manager.excavate(level, pos);
        event.setExpToDrop(0);
        announce(level, manager, pos, ore, dimension);
    }

    private void announce(ServerLevel level, QuarryBlockManager manager, BlockPos pos, Block ore, String dimension) {
        QuarryBroadcast.broken(level, pos, ore, manager.multiplier(ore),
                (int) manager.plannedCooldown(pos, dimension));
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.7F, 0.7F);
        level.playSound(null, pos, SoundEvents.SCULK_BLOCK_CHARGE, SoundSource.BLOCKS, 0.5F, 1.4F);
    }

    private void refuse(Player player, BlockPos pos, Block ore, int seconds, String message, Object... arguments) {
        player.displayClientMessage(text(message, arguments), true);
        if (player instanceof ServerPlayer served) {
            QuarryBroadcast.refused(served, pos, ore, seconds);
        }
    }

    private static Component text(String key, Object... arguments) {
        if (arguments.length == 0) return Component.translatable(key).withStyle(ChatFormatting.RED);

        return Component.translatable(key,
                Component.literal(String.valueOf(arguments[0])).withStyle(ChatFormatting.WHITE)
        ).withStyle(ChatFormatting.RED);
    }

    // WHY: выдача через инвентарь молча съедала добычу при полных слотах, а награда карьера
    // WHY: с множителем это десятки предметов: остаток обязан лечь под ноги, а не исчезнуть
    private void award(Player player, ItemStack drop) {
        if (drop.isEmpty() || player.isCreative()) return;

        ItemStack given = drop.copy();
        player.getInventory().add(given);
        if (!given.isEmpty()) player.drop(given, false);
    }

    // WHY: ломание отменено, и ваниль не тратит ни прочность, ни сытость: кирка копала карьер вечно
    private void wear(ServerLevel level, Player player, BlockState state, BlockPos pos) {
        if (player.isCreative()) return;

        player.getMainHandItem().mineBlock(level, state, pos, player);
        player.causeFoodExhaustion(MINING_EXHAUSTION);
    }

    // WHY: свой список кирок пропускал золотую на древние обломки и ломался о модовые инструменты,
    // WHY: поэтому право на добычу спрашивается у самой ваниль: тег блока против уровня инструмента
    private boolean suitableTool(ItemStack tool, BlockState state) {
        if (!state.requiresCorrectToolForDrops()) return !tool.isEmpty();
        return tool.isCorrectToolForDrops(state);
    }

    // WHY: выработка неразрушима, поэтому удар по ней не рождает ломания и игрок бил в пустоту
    // WHY: без единого отклика: стук отвечает вспышкой и оставшимся временем, но не чаще раза в чуть
    @SubscribeEvent
    public void onKnock(PlayerInteractEvent.LeftClickBlock event) {
        QuarryBlockManager manager = manager();
        if (manager == null || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();
        if (!QuarryBlocks.isExcavated(level.getBlockState(pos))) return;

        String dimension = level.dimension().location().toString();
        if (!manager.isQuarryBlock(pos, dimension)) return;
        if (!ActionGate.allow(player, KNOCK_GATE, KNOCK_TICKS)) return;

        Block ore = manager.originalBlock(pos, dimension);
        if (ore == null) return;

        int left = (int) manager.getRemainingCooldown(pos, dimension);
        refuse(player, pos, ore, left, "quarrymod.event.cooldown_remaining", left);
    }

    // WHY: карьерный блок, снесённый взрывом, уходил из мира навсегда: гранаты и тротил
    // WHY: разбирали шахту быстрее любой кирки и мимо всех откатов
    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        QuarryBlockManager manager = manager();
        if (manager == null || !(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos center = BlockPos.containing(event.getExplosion().getPosition());
        if (!manager.anyNear(center, EXPLOSION_CHUNK_REACH)) return;

        String dimension = level.dimension().location().toString();
        List<BlockPos> affected = event.getAffectedBlocks();
        affected.removeIf(pos -> manager.isQuarryBlock(pos, dimension));
    }

    // WHY: сдвинутый поршнем карьерный блок уезжал с учёта: в списке оставалась старая клетка,
    // WHY: а руда становилась обычной и добывалась без отката
    @SubscribeEvent
    public void onPiston(PistonEvent.Pre event) {
        QuarryBlockManager manager = manager();
        if (manager == null || !(event.getLevel() instanceof ServerLevel served)) return;
        if (!manager.anyNear(event.getPos(), PISTON_CHUNK_REACH)) return;

        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) return;

        String dimension = served.dimension().location().toString();
        if (moves(manager, resolver.getToPush(), dimension) || moves(manager, resolver.getToDestroy(), dimension)) {
            event.setCanceled(true);
        }
    }

    private boolean moves(QuarryBlockManager manager, List<BlockPos> positions, String dimension) {
        for (BlockPos pos : positions) {
            if (manager.isQuarryBlock(pos, dimension)) return true;
        }
        return false;
    }

    @SubscribeEvent
    public void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        resync(event.getEntity());
    }

    @SubscribeEvent
    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        resync(event.getEntity());
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        resync(event.getEntity());
    }

    @SubscribeEvent
    public void onQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        QuarryBroadcast.forget(event.getEntity().getUUID());
    }

    private void resync(Player player) {
        QuarryBlockManager manager = manager();
        if (manager == null || !(player instanceof ServerPlayer served)) return;

        QuarryBroadcast.sync(served, manager, true);
    }

    private static QuarryBlockManager manager() {
        QuarryMod mod = QuarryMod.getInstance();
        if (mod == null || mod.getDataManager() == null) return null;
        return mod.getDataManager().getBlockManager();
    }
}
