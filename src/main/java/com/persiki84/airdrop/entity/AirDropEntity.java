package com.persiki84.airdrop.entity;

import com.persiki84.airdrop.config.AirDropConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public class AirDropEntity extends Entity {

    private static final EntityDataAccessor<Float> FALL_SPEED =
            SynchedEntityData.defineId(AirDropEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> LANDED =
            SynchedEntityData.defineId(AirDropEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> OPENED =
            SynchedEntityData.defineId(AirDropEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> LANDED_TICK =
            SynchedEntityData.defineId(AirDropEntity.class, EntityDataSerializers.INT);
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

    public final AnimationState flyingAnimationState = new AnimationState();
    public final AnimationState openingAnimationState = new AnimationState();
    private boolean clientPrevOpened = false;
    private boolean warnedDespawn = false;

    public AirDropEntity(EntityType<? extends AirDropEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(FALL_SPEED, 0.15F);
        entityData.define(LANDED, false);
        entityData.define(OPENED, false);
        entityData.define(LANDED_TICK, 0);
        entityData.define(FLYING_ANIM_TICKS, 600);
        entityData.define(LEVELING, false);
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide) {
            this.setNoGravity(true);

            if (isOpened()) {
                com.persiki84.minimap.MapManager.removeWorldMarker(getId());
            } else {
                com.persiki84.minimap.MapManager.setWorldMarker(getId(), getX(), getZ(), "airdrop.map.marker");
            }

            if (!isLanded()) {
                int groundY = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        (int) Math.floor(getX()), (int) Math.floor(getZ()));
                setLeveling((getY() - groundY) <= 10.0);

                double fall = getFallSpeed();
                double yBefore = this.getY();
                this.move(MoverType.SELF, new Vec3(0.0, -fall, 0.0));
                double yAfter = this.getY();

                boolean blocked = yAfter > (yBefore - fall + 1.0E-6);
                if (blocked && (this.onGround() || this.verticalCollision)) {
                    this.setDeltaMovement(Vec3.ZERO);
                    setLanded(true);
                    entityData.set(LANDED_TICK, tickCount);
                }
            } else {
                int sinceLanded = tickCount - entityData.get(LANDED_TICK);
                int openDelay = AirDropConfig.SERVER.autoOpenDelayTicks.get();

                if (!isOpened() && sinceLanded >= openDelay) {
                    setOpened(true);
                }

                boolean isEmpty = inventory.isEmpty();
                int despawnTime = isEmpty
                        ? AirDropConfig.SERVER.despawnEmptySeconds.get() * 20
                        : AirDropConfig.SERVER.despawnFilledSeconds.get() * 20;

                if (sinceLanded >= despawnTime) {
                    if (!isEmpty) {
                        level().getServer().getPlayerList().broadcastSystemMessage(
                                Component.translatable("airdrop.despawn.time_up").withStyle(ChatFormatting.RED), false);
                    } else {
                        level().getServer().getPlayerList().broadcastSystemMessage(
                                Component.translatable("airdrop.despawn.empty").withStyle(ChatFormatting.GRAY), false);
                    }
                    this.discard();
                    return;
                }

                if (!isEmpty && !warnedDespawn) {
                    int warnTime = AirDropConfig.SERVER.notificationSecondsBeforeDespawn.get() * 20;
                    if (despawnTime - sinceLanded <= warnTime) {
                        int secondsLeft = (despawnTime - sinceLanded) / 20;
                        level().getServer().getPlayerList().broadcastSystemMessage(
                                Component.translatable("airdrop.despawn.warning", secondsLeft).withStyle(ChatFormatting.RED), false);
                        warnedDespawn = true;
                    }
                }
            }
            return;
        }

        if (!isLanded()) {
            if (!flyingAnimationState.isStarted()) flyingAnimationState.start(tickCount);
        } else {
            flyingAnimationState.stop();
        }

        boolean openedNow = isOpened();
        if (openedNow && !clientPrevOpened) {
            openingAnimationState.start(tickCount);
        }
        clientPrevOpened = openedNow;

        if (!openedNow) {
            for (int i = 0; i < 2; i++) {
                double ox = (this.random.nextDouble() - 0.5) * 0.5;
                double oz = (this.random.nextDouble() - 0.5) * 0.5;
                level().addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, getX() + ox, getY() + 1.3, getZ() + oz, 0.0, 0.07, 0.0);
            }
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() != null && !level().isClientSide) {
            com.persiki84.minimap.MapManager.removeWorldMarker(getId());
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
    public int getFlyingAnimTicks() { return entityData.get(FLYING_ANIM_TICKS); }
    public void setFlyingAnimTicks(int v) { entityData.set(FLYING_ANIM_TICKS, v); }
    public boolean isLeveling() { return entityData.get(LEVELING); }
    public void setLeveling(boolean v) { entityData.set(LEVELING, v); }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("FallSpeed")) setFallSpeed(tag.getFloat("FallSpeed"));
        if (tag.contains("Landed")) setLanded(tag.getBoolean("Landed"));
        if (tag.contains("Opened")) setOpened(tag.getBoolean("Opened"));
        if (tag.contains("LandedTick")) entityData.set(LANDED_TICK, tag.getInt("LandedTick"));
        if (tag.contains("FlyingAnimTicks")) setFlyingAnimTicks(tag.getInt("FlyingAnimTicks"));
        if (tag.contains("WarnedDespawn")) warnedDespawn = tag.getBoolean("WarnedDespawn");

        if (tag.contains("Inv")) {
            ListTag list = tag.getList("Inv", Tag.TAG_COMPOUND);
            inventory.clearContent();
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                int slot = itemTag.getByte("Slot") & 255;
                if (slot < inventory.getContainerSize()) {
                    inventory.setItem(slot, ItemStack.of(itemTag));
                }
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("FallSpeed", getFallSpeed());
        tag.putBoolean("Landed", isLanded());
        tag.putBoolean("Opened", isOpened());
        tag.putInt("LandedTick", entityData.get(LANDED_TICK));
        tag.putInt("FlyingAnimTicks", getFlyingAnimTicks());
        tag.putBoolean("WarnedDespawn", warnedDespawn);

        ListTag list = new ListTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putByte("Slot", (byte) i);
                stack.save(itemTag);
                list.add(itemTag);
            }
        }
        tag.put("Inv", list);
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

        if (isLanded()) {
            if (isOpened()) {
                player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x3, id, inv, inventory, 3) {
                            @Override
                            public ItemStack quickMoveStack(Player pPlayer, int pIndex) {
                                ItemStack itemstack = ItemStack.EMPTY;
                                net.minecraft.world.inventory.Slot slot = this.slots.get(pIndex);
                                if (slot != null && slot.hasItem()) {
                                    ItemStack itemstack1 = slot.getItem();
                                    itemstack = itemstack1.copy();
                                    if (pIndex < 3 * 9) {
                                        if (!this.moveItemStackTo(itemstack1, 3 * 9, this.slots.size(), true)) {
                                            return ItemStack.EMPTY;
                                        }
                                    } else {
                                        return ItemStack.EMPTY;
                                    }
                                    if (itemstack1.isEmpty()) slot.set(ItemStack.EMPTY);
                                    else slot.setChanged();
                                }
                                return itemstack;
                            }
                            @Override
                            public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType, Player player) {
                                if (slotId >= 0 && slotId < 27 && !getCarried().isEmpty()) return;
                                super.clicked(slotId, button, clickType, player);
                            }
                        },
                        Component.translatable("container.airdrop")
                ));
                return InteractionResult.CONSUME;
            }
            else {
                int sinceLanded = tickCount - entityData.get(LANDED_TICK);
                int totalDelay = AirDropConfig.SERVER.autoOpenDelayTicks.get();
                int ticksLeft = totalDelay - sinceLanded;
                if (ticksLeft > 0) {
                    player.sendSystemMessage(Component.translatable("airdrop.interact.opening", ticksLeft / 20).withStyle(ChatFormatting.YELLOW));
                }
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.PASS;
    }
}
