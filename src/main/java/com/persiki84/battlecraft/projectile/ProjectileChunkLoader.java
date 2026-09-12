package com.persiki84.battlecraft.projectile;

import com.persiki84.battlecraft.BattleCraftMod;
import com.persiki84.battlecraft.rules.GameRule;
import com.persiki84.battlecraft.rules.GameRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = BattleCraftMod.MOD_ID)
public final class ProjectileChunkLoader {
    private static final Set<String> ESCORTED_MODS = Set.of("tacz", "superbwarfare");
    private static final String EXPLOSIVE_MARKER = "com.atsuishio.superbwarfare.entity.projectile.ExplosiveProjectile";
    private static final int MAX_ESCORTED = 48;
    private static final int MAX_AGE_TICKS = 400;
    private static final int LOOKAHEAD_TICKS = 3;
    private static final double BULLET_SPEED = 3.0;

    private static final Map<UUID, Escort> escorts = new HashMap<>();

    private static Class<?> explosiveMarker;
    private static boolean markerProbed;

    private ProjectileChunkLoader() {}

    public static void register() {
        ForgeChunkManager.setForcedChunkLoadingCallback(BattleCraftMod.MOD_ID, (level, helper) ->
                new ArrayList<>(helper.getEntityTickets().keySet()).forEach(helper::removeAllTickets));
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!GameRules.allows(GameRule.ESCORT_PROJECTILES)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof Projectile projectile) || !escorted(projectile)) return;
        if (escorts.size() >= MAX_ESCORTED) return;

        escorts.put(projectile.getUUID(), new Escort(level));
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;

        Iterator<Map.Entry<UUID, Escort>> iterator = escorts.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Escort> entry = iterator.next();
            if (entry.getValue().level != level) continue;

            if (!advance(entry.getKey(), entry.getValue(), level)) {
                release(entry.getKey(), entry.getValue());
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        escorts.entrySet().removeIf(entry -> {
            if (entry.getValue().level != level) return false;
            release(entry.getKey(), entry.getValue());
            return true;
        });
    }

    private static boolean advance(UUID id, Escort escort, ServerLevel level) {
        Entity projectile = level.getEntity(id);
        if (projectile == null || !projectile.isAlive() || ++escort.age > MAX_AGE_TICKS) return false;
        if (tooFastToStrand(projectile)) return false;

        hold(id, escort, level, wanted(projectile));
        return true;
    }

    private static boolean tooFastToStrand(Entity projectile) {
        return !explosive(projectile) && projectile.getDeltaMovement().length() > BULLET_SPEED;
    }

    private static boolean explosive(Entity projectile) {
        if (!markerProbed) {
            markerProbed = true;
            try {
                explosiveMarker = Class.forName(EXPLOSIVE_MARKER);
            } catch (Throwable error) {
                explosiveMarker = null;
            }
        }
        return explosiveMarker != null && explosiveMarker.isInstance(projectile);
    }

    private static List<ChunkPos> wanted(Entity projectile) {
        Vec3 ahead = projectile.position().add(projectile.getDeltaMovement().scale(LOOKAHEAD_TICKS));
        ChunkPos now = projectile.chunkPosition();
        ChunkPos next = new ChunkPos((int) Math.floor(ahead.x) >> 4, (int) Math.floor(ahead.z) >> 4);
        return now.equals(next) ? List.of(now) : List.of(now, next);
    }

    private static void hold(UUID id, Escort escort, ServerLevel level, List<ChunkPos> wanted) {
        for (ChunkPos chunk : wanted) {
            if (escort.held.add(chunk)) {
                ForgeChunkManager.forceChunk(level, BattleCraftMod.MOD_ID, id, chunk.x, chunk.z, true, true);
            }
        }
        escort.held.removeIf(chunk -> {
            if (wanted.contains(chunk)) return false;
            ForgeChunkManager.forceChunk(level, BattleCraftMod.MOD_ID, id, chunk.x, chunk.z, false, true);
            return true;
        });
    }

    private static void release(UUID id, Escort escort) {
        for (ChunkPos chunk : escort.held) {
            ForgeChunkManager.forceChunk(escort.level, BattleCraftMod.MOD_ID, id, chunk.x, chunk.z, false, true);
        }
        escort.held.clear();
    }

    private static boolean escorted(Projectile projectile) {
        var key = ForgeRegistries.ENTITY_TYPES.getKey(projectile.getType());
        return key != null && ESCORTED_MODS.contains(key.getNamespace());
    }

    private static final class Escort {
        private final ServerLevel level;
        private final Set<ChunkPos> held = new HashSet<>();
        private int age;

        private Escort(ServerLevel level) {
            this.level = level;
        }
    }
}
