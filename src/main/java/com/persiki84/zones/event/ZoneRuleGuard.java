package com.persiki84.zones.event;

import com.persiki84.zones.Zone;
import com.persiki84.zones.ZoneLookup;
import com.persiki84.zones.ZoneRule;
import com.persiki84.zones.ZonesMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import com.persiki84.battlecraft.modules.ModuleId;
import com.persiki84.battlecraft.modules.ModuleSwitches;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = ZonesMod.MOD_ID)
public final class ZoneRuleGuard {
    private static final long DENIAL_COOLDOWN_MS = 1500L;

    private static final Map<UUID, Long> lastDenial = new ConcurrentHashMap<>();

    private ZoneRuleGuard() {}

    private static boolean muted() {
        return !ModuleSwitches.allows(ModuleId.ZONES);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide() || muted()) return;

        Player player = event.getPlayer();
        if (player != null && player.isCreative()) return;

        Zone zone = zoneBarring(ZoneRule.BLOCK_BREAK, player, event.getLevel(), event.getPos());
        if (zone == null) return;

        event.setCanceled(true);
        refuse(player, zone, "zones.rule.denied.block_break");
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide() || muted()) return;

        Entity placer = event.getEntity();
        if (placer instanceof Player player && player.isCreative()) return;

        Zone zone = zoneBarring(ZoneRule.BLOCK_PLACE, placer, event.getLevel(), event.getPos());
        if (zone == null) return;

        event.setCanceled(true);
        if (placer instanceof Player player) refuse(player, zone, "zones.rule.denied.block_place");
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBucket(FillBucketEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.isCreative() || muted()) return;
        if (!(event.getTarget() instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        boolean pouring = event.getEmptyBucket().getItem() instanceof BucketItem bucket
                && bucket.getFluid() != Fluids.EMPTY;
        ZoneRule rule = pouring ? ZoneRule.BLOCK_PLACE : ZoneRule.BLOCK_BREAK;
        Zone zone = zoneAround(rule, player, event.getLevel(), hit);
        if (zone == null) return;

        event.setCanceled(true);
        refuse(player, zone, pouring ? "zones.rule.denied.block_place" : "zones.rule.denied.block_break");
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIgnite(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getLevel().isClientSide || player.isCreative() || muted()) return;

        Item held = event.getItemStack().getItem();
        if (!(held instanceof FlintAndSteelItem) && !(held instanceof FireChargeItem)) return;

        Zone zone = zoneAround(ZoneRule.BLOCK_PLACE, player, event.getLevel(), event.getHitVec());
        if (zone == null) return;

        event.setCanceled(true);
        refuse(player, zone, "zones.rule.denied.block_place");
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide || muted()) return;

        Player player = event.getEntity();
        if (player.isCreative()) return;

        Zone zone = zoneForbiddingAt(ZoneRule.INTERACT, event.getLevel(), event.getPos());
        if (zone == null) return;

        event.setCanceled(true);
        refuse(player, zone, "zones.rule.denied.interact");
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onItemToss(ItemTossEvent event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide || player.isCreative() || muted()) return;

        Zone zone = ZoneLookup.forbidding(ZoneLookup.dimensionOf(player), ZoneRule.ITEM_DROP,
                player.getX(), player.getY(), player.getZ());
        if (zone == null) return;

        event.setCanceled(true);
        returnToOwner(player, event);
        refuse(player, zone, "zones.rule.denied.item_drop");
    }

    private static void returnToOwner(Player player, ItemTossEvent event) {
        if (!player.getInventory().add(event.getEntity().getItem())) {
            player.level().addFreshEntity(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onMobSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (muted()) return;

        if (ZoneLookup.forbids(ZoneLookup.dimensionOf(event.getLevel()), ZoneRule.MOB_SPAWN,
                event.getX(), event.getY(), event.getZ())) {
            event.setSpawnCancelled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide || muted()) return;

        ResourceLocation here = ZoneLookup.dimensionOf(event.getLevel());
        stripShieldedBlocks(here, event.getAffectedBlocks());
        stripShieldedEntities(here, event.getAffectedEntities());
    }

    private static void stripShieldedBlocks(ResourceLocation here, List<BlockPos> affected) {
        affected.removeIf(pos -> ZoneLookup.forbids(here, ZoneRule.EXPLOSIONS,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
    }

    private static void stripShieldedEntities(ResourceLocation here, List<Entity> affected) {
        affected.removeIf(entity -> ZoneLookup.forbids(here, ZoneRule.EXPLOSIONS,
                entity.getX(), entity.getY(), entity.getZ()));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || muted()) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        if (ZoneLookup.forbids(ZoneLookup.dimensionOf(player), ZoneRule.HUNGER,
                player.getX(), player.getY(), player.getZ())) {
            player.getFoodData().setExhaustion(0.0f);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        lastDenial.remove(event.getEntity().getUUID());
    }

    private static Zone zoneForbiddingAt(ZoneRule rule, LevelAccessor level, BlockPos pos) {
        return ZoneLookup.forbidding(ZoneLookup.dimensionOf(level), rule,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private static Zone zoneBarring(ZoneRule rule, Entity actor, LevelAccessor level, BlockPos pos) {
        return ZoneLookup.barring(rule, actor, ZoneLookup.dimensionOf(level),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private static Zone zoneAround(ZoneRule rule, Entity actor, LevelAccessor level, BlockHitResult hit) {
        Zone zone = zoneBarring(rule, actor, level, hit.getBlockPos());
        return zone != null ? zone : zoneBarring(rule, actor, level, hit.getBlockPos().relative(hit.getDirection()));
    }

    private static void refuse(Player player, Zone zone, String key) {
        if (!(player instanceof ServerPlayer server) || !offCooldown(server)) return;

        server.sendSystemMessage(Component.translatable(key,
                Component.literal(zone.id()).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.RED));
    }

    private static boolean offCooldown(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long previous = lastDenial.put(player.getUUID(), now);
        return previous == null || now - previous >= DENIAL_COOLDOWN_MS;
    }
}
