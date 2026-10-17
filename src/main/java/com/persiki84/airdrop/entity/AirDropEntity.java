package com.persiki84.airdrop.entity;

import com.persiki84.airdrop.AirDropMod;
import com.persiki84.airdrop.config.AirDropConfig;
import com.persiki84.battlecraft.BattleCraftManager;
import com.persiki84.minimap.MapManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

import static com.persiki84.airdrop.config.AirDropLimits.TICKS_PER_SECOND;

public class AirDropEntity extends Entity implements GeoEntity {
    private static final RawAnimation DEPLOY_AND_FLY = RawAnimation.begin().thenPlay("deploy").thenLoop("flying");
    private static final RawAnimation STEADY = RawAnimation.begin().thenLoop("steady");
    private static final RawAnimation CHUTE_LANDING = RawAnimation.begin().thenPlayAndHold("land");
    private static final RawAnimation CHUTE_DOWN = RawAnimation.begin().thenPlayAndHold("landed");
    private static final RawAnimation LID_OPENING = RawAnimation.begin().thenPlayAndHold("opening");
    private static final RawAnimation LID_OPEN = RawAnimation.begin().thenPlayAndHold("opened");
    private static final RawAnimation BEACON = RawAnimation.begin().thenLoop("beacon");
    private static final RawAnimation LAMP_OFF = RawAnimation.begin().thenPlayAndHold("lamp_off");
    private static final int CHUTE_BLEND_TICKS = 8;
    private static final float CRUISE_FALL_SPEED = 0.15F;
    private static final double LEVELING_HEIGHT = 10.0;
    private static final int SUPPORT_CHECK_TICKS = 10;
    private static final long NEVER = -1L;
    private static final long NO_CHUNK = Long.MIN_VALUE;

    private static int mapGeneration;

    private static final EntityDataAccessor<Float> FALL_SPEED =
            SynchedEntityData.defineId(AirDropEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> LANDED =
            SynchedEntityData.defineId(AirDropEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> OPENED =
            SynchedEntityData.defineId(AirDropEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> LOOTED =
            SynchedEntityData.defineId(AirDropEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> FLYING_ANIM_TICKS =
            SynchedEntityData.defineId(AirDropEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> LEVELING =
            SynchedEntityData.defineId(AirDropEntity.class, EntityDataSerializers.BOOLEAN);

    private final SimpleContainer inventory = new SimpleContainer(27) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return false;
        }
    };

    private final AnimatableInstanceCache animations = GeckoLibUtil.createInstanceCache(this);
    private boolean chuteSeen;
    private boolean landedWhenSeen;
    private boolean lidSeen;
    private boolean openedWhenSeen;
    private boolean effectsSeen;
    private boolean landedLastTick;
    private boolean deployed;
    private boolean warnedDespawn;
    private boolean matchSpawned;
    private boolean marked;
    private int markedGeneration = mapGeneration;
    private long landedAt = NEVER;
    private long emptiedAt = NEVER;
    private long heldChunk = NO_CHUNK;

    public AirDropEntity(EntityType<? extends AirDropEntity> type, Level level) {
        super(type, level);
    }

    // WHY: конец матча чистит все метки карты разом, мимо ящиков: уцелевший ящик считал свою
    // WHY: метку стоящей и пропадал с карты до конца жизни. Смена поколения заставляет поставить её снова
    public static void mapCleared() {
        mapGeneration++;
    }

    public static int discardAll(MinecraftServer server) {
        List<Entity> drops = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof AirDropEntity) drops.add(entity);
            }
        }
        drops.forEach(Entity::discard);
        return drops.size();
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(FALL_SPEED, 0.15F);
        entityData.define(LANDED, false);
        entityData.define(OPENED, false);
        entityData.define(LOOTED, false);
        entityData.define(FLYING_ANIM_TICKS, 600);
        entityData.define(LEVELING, false);
    }

    public void matchSpawned(boolean value) {
        matchSpawned = value;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        serverTick();
    }

    private void serverTick() {
        setNoGravity(true);
        if (leftoverOfMatch()) {
            discard();
            return;
        }
        holdChunk();
        announceDeploy();
        watchLoot();
        traceOnMap();
        if (!isLanded()) {
            descend();
        } else {
            rest();
        }
    }

    // WHY: ящик матча, доживший до лобби, это бесплатная добыча до старта следующего матча; он же
    // WHY: мог лежать в невыгруженном чанке и проснуться в лобби после перезапуска сервера
    private boolean leftoverOfMatch() {
        return matchSpawned && AirDropConfig.SERVER.clearOnMatchEnd.get()
                && !BattleCraftManager.getInstance().matchRunning();
    }

    // WHY: точка сброса случайна и почти всегда далеко от игроков, а сущность в непрогруженном
    // WHY: чанке не тикает: ящик висел в воздухе на высоте старта, не садился и не исчезал
    private void holdChunk() {
        long chunk = ChunkPos.asLong(blockPosition());
        if (chunk == heldChunk) return;

        releaseChunk();
        ServerLevel server = (ServerLevel) level();
        ChunkPos pos = new ChunkPos(chunk);
        // WHY: false здесь значит «тикет уже стоит» - он пережил перезапуск вместе с ящиком, и
        // WHY: повторять запрос каждый тик незачем: чанк держится тем же владельцем
        ForgeChunkManager.forceChunk(server, AirDropMod.MOD_ID, this, pos.x, pos.z, true, true);
        heldChunk = chunk;
        DropTickets.claim(server, getUUID(), chunk);
    }

    private void releaseChunk() {
        if (heldChunk == NO_CHUNK || !(level() instanceof ServerLevel server)) return;

        ChunkPos pos = new ChunkPos(heldChunk);
        ForgeChunkManager.forceChunk(server, AirDropMod.MOD_ID, this, pos.x, pos.z, false, true);
        heldChunk = NO_CHUNK;
    }

    private void watchLoot() {
        boolean empty = inventory.isEmpty();
        if (empty && emptiedAt == NEVER && isOpened()) emptiedAt = now();
        if (!empty) emptiedAt = NEVER;
        if (entityData.get(LOOTED) != (empty && isOpened())) entityData.set(LOOTED, empty && isOpened());
    }

    // WHY: метка ставится при смене состояния, а не каждый тик: каждая установка помечала весь
    // WHY: список меток грязным, и он уходил всем игрокам на каждой рассылке, пока ящик жив.
    // WHY: Точка на карте это те же координаты, что скрывает тихое объявление, поэтому без
    // WHY: объявления координат метки нет
    private void traceOnMap() {
        if (markedGeneration != mapGeneration) {
            markedGeneration = mapGeneration;
            marked = false;
        }
        boolean wanted = !isLooted() && AirDropConfig.SERVER.announceCoords.get();
        if (wanted == marked) return;

        marked = wanted;
        if (wanted) {
            MapManager.setWorldMarker(getId(), getX(), getZ(), "airdrop.map.marker");
        } else {
            MapManager.removeWorldMarker(getId());
        }
    }

    private void descend() {
        int groundY = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (int) Math.floor(getX()), (int) Math.floor(getZ()));
        setLeveling((getY() - groundY) <= LEVELING_HEIGHT);

        double fall = getFallSpeed();
        double before = getY();
        move(MoverType.SELF, new Vec3(0.0, -fall, 0.0));

        boolean blocked = getY() > before - fall + 1.0E-6;
        if (blocked && (onGround() || verticalCollision)) {
            setDeltaMovement(Vec3.ZERO);
            setLanded(true);
            if (landedAt == NEVER) {
                landedAt = now();
                play(SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 2.0F, 0.55F);
                play(SoundEvents.GENERIC_BIG_FALL, 2.0F, 0.7F);
                play(SoundEvents.WOOL_BREAK, 1.5F, 0.6F);
            }
        }
    }

    // WHY: ящик, поднятый из сохранения уже на земле, раскрываться не должен: звук раскрытия
    // WHY: принадлежит только первому тику живого сброса
    private void announceDeploy() {
        if (deployed) return;

        deployed = true;
        if (isLanded()) return;
        play(SoundEvents.PHANTOM_FLAP, 3.0F, 0.55F);
        play(SoundEvents.ARMOR_EQUIP_ELYTRA, 3.0F, 0.8F);
    }

    private void play(SoundEvent sound, float volume, float pitch) {
        level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.NEUTRAL, volume, pitch);
    }

    private void rest() {
        long since = now() - landedAt;
        if (!isOpened() && since >= AirDropConfig.SERVER.autoOpenDelayTicks.get()) setOpened(true);
        if (tickCount % SUPPORT_CHECK_TICKS == 0 && unsupported()) {
            setLanded(false);
            return;
        }
        despawnWhenDue(since);
    }

    // WHY: блок под севшим ящиком могли сломать или взорвать, и ящик оставался висеть в воздухе
    private boolean unsupported() {
        return level().noCollision(this, getBoundingBox().move(0.0, -0.1, 0.0));
    }

    private void despawnWhenDue(long sinceLanded) {
        if (emptiedAt != NEVER) {
            if (now() - emptiedAt >= AirDropConfig.SERVER.despawnEmptySeconds.get() * (long) TICKS_PER_SECOND) {
                broadcast("airdrop.despawn.empty", ChatFormatting.GRAY);
                discard();
            }
            return;
        }
        long lifetime = AirDropConfig.SERVER.despawnFilledSeconds.get() * (long) TICKS_PER_SECOND;
        if (sinceLanded >= lifetime) {
            broadcast("airdrop.despawn.time_up", ChatFormatting.RED);
            discard();
            return;
        }
        warnBeforeDespawn(lifetime - sinceLanded);
    }

    private void warnBeforeDespawn(long left) {
        int warnSeconds = AirDropConfig.SERVER.notificationSecondsBeforeDespawn.get();
        if (warnedDespawn || warnSeconds <= 0 || left > warnSeconds * (long) TICKS_PER_SECOND) return;

        warnedDespawn = true;
        long seconds = (left + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
        level().getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("airdrop.despawn.warning", seconds).withStyle(ChatFormatting.RED), false);
    }

    private void broadcast(String key, ChatFormatting color) {
        level().getServer().getPlayerList().broadcastSystemMessage(Component.translatable(key).withStyle(color), false);
    }

    private long now() {
        return level().getGameTime();
    }

    // WHY: пыль поднимает только посадка, увиденная своими глазами: ящик, пришедший клиенту уже
    // WHY: лежащим, не имеет права взрываться облаком при каждом входе в зону видимости
    private void clientTick() {
        boolean landed = isLanded();
        if (effectsSeen && landed && !landedLastTick) AirDropEffects.raiseDust(this);
        effectsSeen = true;
        landedLastTick = landed;
        if (!isLooted()) AirDropEffects.burnFlare(this, landed);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "chute", CHUTE_BLEND_TICKS, this::chuteState));
        controllers.add(new AnimationController<>(this, "lid", 0, this::lidState));
        controllers.add(new AnimationController<>(this, "lamp", 0, this::lampState));
    }

    // WHY: тот, кто подошёл к уже севшему ящику, должен увидеть лежащий купол, а не повтор посадки
    private PlayState chuteState(AnimationState<AirDropEntity> state) {
        if (!chuteSeen) {
            chuteSeen = true;
            landedWhenSeen = isLanded();
        }
        if (isLanded()) {
            state.setControllerSpeed(1.0F);
            return state.setAndContinue(landedWhenSeen ? CHUTE_DOWN : CHUTE_LANDING);
        }
        landedWhenSeen = false;
        if (isLeveling()) {
            state.setControllerSpeed(1.0F);
            return state.setAndContinue(STEADY);
        }
        boolean cruising = state.isCurrentAnimationStage("flying");
        state.setControllerSpeed(cruising ? Mth.clamp(getFallSpeed() / CRUISE_FALL_SPEED, 0.75F, 2.5F) : 1.0F);
        return state.setAndContinue(DEPLOY_AND_FLY);
    }

    private PlayState lidState(AnimationState<AirDropEntity> state) {
        if (!lidSeen) {
            lidSeen = true;
            openedWhenSeen = isOpened();
        }
        if (!isOpened()) {
            openedWhenSeen = false;
            return PlayState.STOP;
        }
        return state.setAndContinue(openedWhenSeen ? LID_OPEN : LID_OPENING);
    }

    private PlayState lampState(AnimationState<AirDropEntity> state) {
        return state.setAndContinue(isLooted() ? LAMP_OFF : BEACON);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animations;
    }

    // WHY: выгрузка чанка не уничтожает ящик: метка на карте и тикет снимаются только тогда, когда
    // WHY: ящик уходит насовсем, иначе он пропадал с карты при каждой выгрузке
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy()) {
            MapManager.removeWorldMarker(getId());
            releaseChunk();
        }
        super.remove(reason);
    }

    public SimpleContainer getInventory() { return inventory; }
    public float getFallSpeed() { return entityData.get(FALL_SPEED); }
    public void setFallSpeed(float v) { entityData.set(FALL_SPEED, v); }
    public boolean isLanded() { return entityData.get(LANDED); }
    public void setLanded(boolean v) { entityData.set(LANDED, v); }
    public boolean isOpened() { return entityData.get(OPENED); }
    public void setOpened(boolean v) { entityData.set(OPENED, v); }
    public boolean isLooted() { return entityData.get(LOOTED); }
    public int getFlyingAnimTicks() { return entityData.get(FLYING_ANIM_TICKS); }
    public void setFlyingAnimTicks(int v) { entityData.set(FLYING_ANIM_TICKS, v); }
    public boolean isLeveling() { return entityData.get(LEVELING); }
    public void setLeveling(boolean v) { entityData.set(LEVELING, v); }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("FallSpeed")) setFallSpeed(tag.getFloat("FallSpeed"));
        if (tag.contains("Landed")) setLanded(tag.getBoolean("Landed"));
        if (tag.contains("Opened")) setOpened(tag.getBoolean("Opened"));
        if (tag.contains("FlyingAnimTicks")) setFlyingAnimTicks(tag.getInt("FlyingAnimTicks"));
        warnedDespawn = tag.getBoolean("WarnedDespawn");
        matchSpawned = tag.getBoolean("MatchSpawned");
        deployed = tag.getBoolean("Deployed") || isLanded();
        landedAt = readStamp(tag);
        emptiedAt = tag.contains("EmptiedAt") ? tag.getLong("EmptiedAt") : NEVER;
        readInventory(tag.getList("Inv", Tag.TAG_COMPOUND));
    }

    // WHY: до 23.09.2026 время лежания копилось счётчиком тиков LandedAge; он переводится в метку
    // WHY: игрового времени, иначе старые ящики после обновления жили бы заново полный срок
    private long readStamp(CompoundTag tag) {
        if (tag.contains("LandedAt")) return tag.getLong("LandedAt");
        if (tag.contains("LandedAge") && isLanded()) return level().getGameTime() - tag.getInt("LandedAge");
        return isLanded() ? level().getGameTime() : NEVER;
    }

    private void readInventory(ListTag list) {
        inventory.clearContent();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag itemTag = list.getCompound(i);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot < inventory.getContainerSize()) inventory.setItem(slot, ItemStack.of(itemTag));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("FallSpeed", getFallSpeed());
        tag.putBoolean("Landed", isLanded());
        tag.putBoolean("Opened", isOpened());
        tag.putInt("FlyingAnimTicks", getFlyingAnimTicks());
        tag.putBoolean("WarnedDespawn", warnedDespawn);
        tag.putBoolean("MatchSpawned", matchSpawned);
        tag.putBoolean("Deployed", deployed);
        if (landedAt != NEVER) tag.putLong("LandedAt", landedAt);
        if (emptiedAt != NEVER) tag.putLong("EmptiedAt", emptiedAt);
        tag.put("Inv", writeInventory());
    }

    private ListTag writeInventory() {
        ListTag list = new ListTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;

            CompoundTag itemTag = new CompoundTag();
            itemTag.putByte("Slot", (byte) i);
            stack.save(itemTag);
            list.add(itemTag);
        }
        return list;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!isLanded()) return InteractionResult.PASS;

        if (isOpened()) {
            player.openMenu(new SimpleMenuProvider((id, inv, p) -> new AirDropMenu(id, inv, inventory, this),
                    Component.translatable("container.airdrop")));
            return InteractionResult.CONSUME;
        }
        long left = AirDropConfig.SERVER.autoOpenDelayTicks.get() - (now() - landedAt);
        if (left > 0) {
            player.displayClientMessage(Component.translatable("airdrop.interact.opening",
                    (left + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND).withStyle(ChatFormatting.YELLOW), true);
        }
        return InteractionResult.CONSUME;
    }
}
