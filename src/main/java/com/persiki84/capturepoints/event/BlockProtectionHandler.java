package com.persiki84.capturepoints.event;

import com.persiki84.capturepoints.CapturePointsMod;
import com.persiki84.capturepoints.capture.CapturePoint;
import com.persiki84.capturepoints.capture.CapturePointManager;
import com.persiki84.capturepoints.capture.FinalCapturePoint;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = CapturePointsMod.MOD_ID)
public class BlockProtectionHandler {
    private static final int OPERATOR_LEVEL = 2;

    private static boolean protectionEnabled = false;
    private static boolean playerPlacedBreakable = false;
    private static boolean placementDenied = false;

    public static void setProtectionEnabled(boolean enabled) {
        if (protectionEnabled == enabled) return;

        protectionEnabled = enabled;
        CapturePointManager.persist();
    }

    public static boolean isProtectionEnabled() {
        return protectionEnabled;
    }

    public static void setPlayerPlacedBreakable(boolean enabled) {
        if (playerPlacedBreakable == enabled) return;

        playerPlacedBreakable = enabled;
        if (enabled) placementDenied = false;
        CapturePointManager.persist();
    }

    public static boolean isPlayerPlacedBreakable() {
        return playerPlacedBreakable;
    }

    public static void setPlacementDenied(boolean enabled) {
        if (placementDenied == enabled) return;

        placementDenied = enabled;
        if (enabled) {
            playerPlacedBreakable = false;
            PlacedBlocks.clear();
        }
        CapturePointManager.persist();
    }

    public static boolean isPlacementDenied() {
        return placementDenied;
    }

    // WHY: сеттеры сохраняют файл, а при чтении точки ещё не загружены: запись посреди загрузки
    // WHY: оставляла на диске points.dat без единой точки
    public static void restore(boolean protection, boolean placedBreakable, boolean denyPlacement) {
        protectionEnabled = protection;
        playerPlacedBreakable = placedBreakable;
        placementDenied = denyPlacement;
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!protectionEnabled || !(event.getPlayer() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();
        if (buildsFreely(player)) {
            PlacedBlocks.forget(event.getLevel(), pos);
            return;
        }
        CapturePoint point = pointAt(event.getLevel(), pos);
        if (point == null) return;

        if (playerPlacedBreakable && PlacedBlocks.forget(event.getLevel(), pos)) return;

        event.setCanceled(true);
        player.sendSystemMessage(refusal(point, "cannot_break", "cannot_break_final"));
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        Entity placer = event.getEntity();
        if (!(placer instanceof ServerPlayer player) || buildsFreely(player)) return;

        BlockPos pos = event.getPos();
        CapturePoint point = pointAt(event.getLevel(), pos);
        if (point == null) return;

        if (placementDenied) {
            event.setCanceled(true);
            player.sendSystemMessage(refusal(point, "cannot_place", "cannot_place_final"));
            return;
        }

        PlacedBlocks.remember(event.getLevel(), pos);
    }

    // WHY: взрыв ломал защищённые блоки точки мимо запрета ломать руками; LOWEST, чтобы после всех
    // WHY: чужих вычёркиваний забыть ровно те поставленные игроками блоки, что реально разрушатся
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!protectionEnabled || event.getLevel().isClientSide) return;

        Level level = event.getLevel();
        List<BlockPos> affected = event.getAffectedBlocks();
        affected.removeIf(pos -> shieldedFromBlast(level, pos));
        for (BlockPos pos : affected) {
            PlacedBlocks.forget(level, pos);
        }
    }

    private static boolean shieldedFromBlast(Level level, BlockPos pos) {
        if (pointAt(level, pos) == null) return false;
        return !(playerPlacedBreakable && PlacedBlocks.remembers(level, pos));
    }

    // WHY: оператор в творческом строит саму карту: запрет мешал ему править точку, а его блоки
    // WHY: не должны числиться поставленными игроками, иначе их разрешено ломать всем
    private static boolean buildsFreely(ServerPlayer player) {
        return player.isCreative() && player.hasPermissions(OPERATOR_LEVEL);
    }

    private static Component refusal(CapturePoint point, String key, String finalKey) {
        boolean last = point instanceof FinalCapturePoint;
        Component name = Component.literal(point.getName()).withStyle(ChatFormatting.YELLOW);
        MutableComponent message =
                Component.translatable("capturepoints.protection." + (last ? finalKey : key), name);
        return last
                ? message.withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                : message.withStyle(ChatFormatting.RED);
    }

    // WHY: сверялись одни X/Z, и точка Верхнего мира запрещала строить по тем же координатам в
    // WHY: Нижнем и в Энде; столб по высоте оставлен намеренно, он защищает постройку точки целиком
    private static CapturePoint pointAt(LevelAccessor level, BlockPos pos) {
        ResourceKey<Level> here = level instanceof Level world ? world.dimension() : null;
        Vec3 center = Vec3.atCenterOf(pos);

        for (CapturePoint point : CapturePointManager.getAllPoints()) {
            if (covers(point, here, center)) return point;
        }
        for (FinalCapturePoint point : CapturePointManager.getAllFinalPoints()) {
            if (covers(point, here, center)) return point;
        }
        return null;
    }

    private static boolean covers(CapturePoint point, ResourceKey<Level> here, Vec3 center) {
        if (here != null && !here.equals(point.getDimension())) return false;
        return point.getArea().containsHorizontally(center.x, center.z);
    }
}
