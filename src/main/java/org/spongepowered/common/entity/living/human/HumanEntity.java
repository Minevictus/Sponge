/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.entity.living.human;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.Lifecycle;
import net.kyori.adventure.text.Component;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddPlayerPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Team;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.profile.property.ProfileProperty;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.scoreboard.TeamMember;
import org.spongepowered.common.SpongeCommon;
import org.spongepowered.common.accessor.network.protocol.game.ClientboundAddPlayerPacketAccessor;
import org.spongepowered.common.accessor.network.protocol.game.ClientboundPlayerInfoPacketAccessor;
import org.spongepowered.common.accessor.world.entity.LivingEntityAccessor;
import org.spongepowered.common.accessor.world.entity.player.PlayerAccessor;
import org.spongepowered.common.config.SpongeGameConfigs;
import org.spongepowered.common.launch.Launch;
import org.spongepowered.common.util.Constants;
import org.spongepowered.common.util.SpongeTicks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public final class HumanEntity extends PathfinderMob implements TeamMember, RangedAttackMob {
    public static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registry.ENTITY_TYPE_REGISTRY, new ResourceLocation("sponge", "human"));
    public static final EntityType<HumanEntity> TYPE = Registry.ENTITY_TYPE.register(
        KEY,
        EntityType.Builder.of(HumanEntity::new, MobCategory.MISC)
            .noSave()
            .clientTrackingRange(Constants.Entity.Player.TRACKING_RANGE)
            .build("sponge:human"),
        Lifecycle.stable()
    );
    public static final AttributeSupplier ATTRIBUTES = Mob.createMobAttributes()
        .add(Attributes.ATTACK_DAMAGE, 1.0d) // Player
        .add(Attributes.MOVEMENT_SPEED, (double) 0.23f) // Player (custom value)
        .add(Attributes.ATTACK_SPEED) // Player
        .add(Attributes.LUCK) // Player
        .build();

    // A queue of packets waiting to send to players tracking this human
    private final Map<UUID, List<Stream<Packet<?>>>> playerPacketMap = new HashMap<>();

    private GameProfile fakeProfile;
    private boolean aiDisabled = false, leftHanded = false;

    public HumanEntity(final EntityType<? extends HumanEntity> type, final Level world) {
        super(TYPE, world);
        this.fakeProfile = new GameProfile(this.uuid, "");
        this.setCanPickUpLoot(true);
    }

    @Override
    protected void defineSynchedData() {
        // LivingEntity
        this.entityData.define(LivingEntityAccessor.accessor$DATA_LIVING_ENTITY_FLAGS(), (byte) 0);
        this.entityData.define(LivingEntityAccessor.accessor$DATA_HEALTH_ID(), 1.0F);
        this.entityData.define(LivingEntityAccessor.accessor$DATA_EFFECT_COLOR_ID(), 0);
        this.entityData.define(LivingEntityAccessor.accessor$DATA_EFFECT_AMBIENCE_ID(), Boolean.FALSE);
        this.entityData.define(LivingEntityAccessor.accessor$DATA_ARROW_COUNT_ID(), 0);
        this.entityData.define(LivingEntityAccessor.accessor$DATA_STINGER_COUNT_ID(), 0);
        this.entityData.define(LivingEntityAccessor.accessor$SLEEPING_POS_ID(), Optional.empty());

        // PlayerEntity
        this.entityData.define(PlayerAccessor.accessor$DATA_PLAYER_ABSORPTION_ID(), 0.0F);
        this.entityData.define(PlayerAccessor.accessor$DATA_SCORE_ID(), 0);
        this.entityData.define(PlayerAccessor.accessor$DATA_PLAYER_MODE_CUSTOMISATION(), Constants.Sponge.Entity.Human.PLAYER_MODEL_FLAG_ALL);
        this.entityData.define(PlayerAccessor.accessor$DATA_PLAYER_MAIN_HAND(), (byte) 1);
        this.entityData.define(PlayerAccessor.accessor$DATA_SHOULDER_LEFT(), new CompoundTag());
        this.entityData.define(PlayerAccessor.accessor$DATA_SHOULDER_RIGHT(), new CompoundTag());
    }

    @Override
    protected void registerGoals() {
        // Just some defaults, so they do something by default. Plugins can fully cancel this and make them do whatever
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Mob.class, 8.0F));
    }

    @Override
    public boolean isLeftHanded() {
        return this.leftHanded;
    }

    @Override
    public boolean isNoAi() {
        return this.aiDisabled;
    }

    @Override
    public Component teamRepresentation() {
        return Component.text(this.fakeProfile.getName());
    }

    @Override
    public Team getTeam() {
        return this.level.getScoreboard().getPlayersTeam(this.fakeProfile.getName());
    }

    @Override
    public void setCustomName(final net.minecraft.network.chat.@Nullable Component name) {
        final net.minecraft.network.chat.Component customName = this.getCustomName();
        if (customName == null && name == null || customName != null && customName.equals(name)) {
            return;
        }
        super.setCustomName(name);
        this.setProfileName(name);
        if (this.isAliveAndInWorld()) {
            this.respawnOnClient();
        }
    }

    @Override
    public void readAdditionalSaveData(final CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("profile")) {
            this.fakeProfile = NbtUtils.readGameProfile(tag.getCompound("profile"));
        }
    }

    @Override
    public void addAdditionalSaveData(final CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("profile", NbtUtils.writeGameProfile(new CompoundTag(), this.fakeProfile));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        this.updateSwingTime();
    }

    @Override
    public void setNoAi(final boolean disable) {
        this.aiDisabled = disable;
    }

    @Override
    public void setLeftHanded(final boolean leftHanded) {
        this.leftHanded = leftHanded;
    }

    @Override
    public void setAggressive(final boolean aggressive) {
        // NOOP, we handle the arm swing manually...
    }

    @Override
    public int getPortalWaitTime() {
        return 1;
    }

    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.PLAYER_SWIM;
    }

    @Override
    protected SoundEvent getSwimSplashSound() {
        return SoundEvents.PLAYER_SPLASH;
    }

    @Override
    public int getDimensionChangingDelay() {
        return 10;
    }

    @Override
    public void die(@Nullable final DamageSource cause) {
        super.die(cause);
        this.reapplyPosition();
        if (cause != null) {
            this.setDeltaMovement(-Mth.cos((this.hurtDir + this.yRot) * ((float)Math.PI / 180F)) * 0.1F, 0.1F,
                    -Mth.sin((this.hurtDir + this.yRot) * ((float)Math.PI / 180F)) * 0.1F);
        } else {
            this.setDeltaMovement(0.0D, 0.1D, 0.0D);
        }

        this.clearFire();
        this.setSharedFlag(0, false);
    }

    @Override
    protected SoundEvent getHurtSound(final DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public double getMyRidingOffset() {
        return Constants.Entity.Player.PLAYER_Y_OFFSET;
    }

    @Override
    public float getSpeed() {
        return (float) this.getAttribute(Attributes.MOVEMENT_SPEED).getValue();
    }

    @Override
    protected SoundEvent getFallDamageSound(final int height) {
        return height > 4 ? SoundEvents.PLAYER_BIG_FALL : SoundEvents.PLAYER_SMALL_FALL;
    }

    @Override
    public float getAbsorptionAmount() {
        return this.getEntityData().get(PlayerAccessor.accessor$DATA_PLAYER_ABSORPTION_ID());
    }

    @Override
    public void setAbsorptionAmount(float amount) {
        if (amount < 0.0F) {
            amount = 0.0F;
        }
        this.getEntityData().set(PlayerAccessor.accessor$DATA_PLAYER_ABSORPTION_ID(), amount);
    }

    @Override
    protected float tickHeadTurn(final float p_110146_1_, final float p_110146_2_) {
        final float retValue = super.tickHeadTurn(p_110146_1_, p_110146_2_);
        // Make the body rotation follow head rotation
        this.yRot = this.yHeadRot;
        return retValue;
    }

    @Override
    public boolean doHurtTarget(final Entity entityIn) {
        super.doHurtTarget(entityIn);
        this.swing(this.getUsedItemHand());
        float f = (float) this.getAttribute(Attributes.ATTACK_DAMAGE).getValue();
        int i = 0;

        f += EnchantmentHelper.getDamageBonus(this.getItemInHand(InteractionHand.MAIN_HAND), this.getMobType());
        i += EnchantmentHelper.getKnockbackBonus(this);

        final boolean flag = entityIn.hurt(DamageSource.mobAttack(this), f);

        if (flag) {
            if (i > 0) {
                entityIn.push(-Mth.sin(this.yRot * (float) Math.PI / 180.0F) * i * 0.5F, 0.1D,
                        Mth.cos(this.yRot * (float) Math.PI / 180.0F) * i * 0.5F);
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.6, 1.0, 0.6));
            }

            final int j = EnchantmentHelper.getFireAspect(this);

            if (j > 0) {
                entityIn.setSecondsOnFire(j * 4);
            }

            this.doEnchantDamageEffects(this, entityIn);
        }

        return flag;
    }

    private void setProfileName(final net.minecraft.network.chat.@Nullable Component newName) {
        final PropertyMap props = this.fakeProfile.getProperties();
        this.fakeProfile = new GameProfile(this.fakeProfile.getId(), newName == null ? "" : newName.getString());
        this.fakeProfile.getProperties().putAll(props);
    }

    public boolean getOrLoadSkin(final UUID minecraftAccount) {
        GameProfile gameProfile = SpongeCommon.getServer().getProfileCache().get(minecraftAccount);
        if (gameProfile == null) {
            gameProfile =
                    SpongeCommon.getServer().getSessionService().fillProfileProperties(new GameProfile(minecraftAccount, ""), true);
            if (gameProfile == null) {
                return false;
            }

            // TODO Should we put profile cache entries with UUIDs that don't have their names?

            SpongeCommon.getServer().getProfileCache().add(gameProfile);
        }

        this.fakeProfile.getProperties().replaceValues(ProfileProperty.TEXTURES, gameProfile.getProperties().get(ProfileProperty.TEXTURES));
        if (this.isAliveAndInWorld()) {
            this.respawnOnClient();
        }

        return true;
    }

    public boolean getOrLoadSkin(final String minecraftAccount) {
        Objects.requireNonNull(minecraftAccount);
        GameProfile gameProfile = SpongeCommon.getServer().getProfileCache().get(minecraftAccount);
        if (gameProfile == null) {
            return false;
        }

        if (gameProfile.getProperties().isEmpty()) {
            gameProfile = SpongeCommon.getServer().getSessionService().fillProfileProperties(gameProfile, true);
            SpongeCommon.getServer().getProfileCache().add(gameProfile);
        }

        this.fakeProfile.getProperties().clear();
        this.fakeProfile.getProperties().putAll(gameProfile.getProperties());
        if (this.isAliveAndInWorld()) {
            this.respawnOnClient();
        }

        return true;
    }

    public void removeFromTabListDelayed(@Nullable final ServerPlayer player, final ClientboundPlayerInfoPacket removePacket) {
        final int delay = SpongeGameConfigs.getForWorld(this.level).get().entity.human.tabListRemoveDelay;
        final Runnable removeTask = () -> this.pushPackets(player, removePacket);
        if (delay == 0) {
            removeTask.run();
        } else {
            Sponge.server().scheduler().submit(Task.builder()
                    .execute(removeTask)
                    .delay(new SpongeTicks(delay))
                    .plugin(Launch.getInstance().getCommonPlugin())
                    .build());
        }
    }

    public Property getSkinProperty() {
        final Collection<Property> properties = this.fakeProfile.getProperties().get(ProfileProperty.TEXTURES);
        if (properties.isEmpty()) {
            return null;
        }
        return properties.iterator().next();
    }

    public void setSkinProperty(final Property property) {
        this.fakeProfile.getProperties().replaceValues(ProfileProperty.TEXTURES, Collections.singletonList(property));

        if (this.isAliveAndInWorld()) {
            this.respawnOnClient();
        }
    }

    private boolean isAliveAndInWorld() {
        return this.level.getEntity(this.getId()) == this && !this.removed;
    }

    private void respawnOnClient() {
        this.pushPackets(new ClientboundRemoveEntitiesPacket(this.getId()), this.createPlayerListPacket(ClientboundPlayerInfoPacket.Action.ADD_PLAYER));
        this.pushPackets(this.getAddEntityPacket());
        this.removeFromTabListDelayed(null, this.createPlayerListPacket(ClientboundPlayerInfoPacket.Action.REMOVE_PLAYER));
    }

    /**
     * Can the fake profile be removed from the tab list immediately (i.e. as
     * soon as the human has spawned).
     *
     * @return Whether it can be removed with 0 ticks delay
     */
    public boolean canRemoveFromListImmediately() {
        return !this.fakeProfile.getProperties().containsKey(ProfileProperty.TEXTURES);
    }

    /**
     * Called when a player stops tracking this human.
     *
     * Removes the player from the packet queue and sends them a REMOVE_PLAYER
     * tab list packet to make sure the human is not on it.
     *
     * @param player The player that has stopped tracking this human
     */
    public void untrackFrom(final ServerPlayer player) {
        this.playerPacketMap.remove(player.getUUID());
        player.connection.send(this.createPlayerListPacket(ClientboundPlayerInfoPacket.Action.REMOVE_PLAYER));
    }

    /**
     * Creates a {@link ClientboundAddPlayerPacket} packet.
     *
     * Copied directly from the constructor of the packet, because that can't be
     * used as we're not a PlayerEntity.
     *
     * @return A new spawn packet
     */
    @Override
    public ClientboundAddPlayerPacket getAddEntityPacket() {
        final ClientboundAddPlayerPacket packet = new ClientboundAddPlayerPacket();
        final ClientboundAddPlayerPacketAccessor accessor = (ClientboundAddPlayerPacketAccessor) packet;
        accessor.accessor$entityId(this.getId());
        accessor.accessor$playerId(this.fakeProfile.getId());
        accessor.accessor$x(this.getX());
        accessor.accessor$y(this.getY());
        accessor.accessor$z(this.getZ());
        accessor.accessor$yRot((byte) ((int) (this.yRot * 256.0F / 360.0F)));
        accessor.accessor$xRot((byte) ((int) (this.xRot * 256.0F / 360.0F)));
        return packet;
    }

    /**
     * Creates a {@link ClientboundPlayerInfoPacket} packet with the given action.
     *
     * @param action The action to apply on the tab list
     * @return A new tab list packet
     */
    @SuppressWarnings("ConstantConditions")
    public ClientboundPlayerInfoPacket createPlayerListPacket(final ClientboundPlayerInfoPacket.Action action) {
        final ClientboundPlayerInfoPacket packet = new ClientboundPlayerInfoPacket(action);
        ((ClientboundPlayerInfoPacketAccessor) packet).accessor$entries()
                .add(packet.new PlayerUpdate(this.fakeProfile, 0, GameType.NOT_SET, this.getDisplayName()));
        return packet;
    }

    /**
     * Push the given packets to all players tracking this human.
     *
     * @param packets All packets to send in a single tick
     */
    public void pushPackets(final Packet<?>... packets) {
        this.pushPackets(null, packets); // null = all players
    }

    /**
     * Push the given packets to the given player (who must be tracking this
     * human).
     *
     * @param player The player tracking this human
     * @param packets All packets to send in a single tick
     */
    public void pushPackets(@Nullable final ServerPlayer player, final Packet<?>... packets) {
        final List<Stream<Packet<?>>> queue;
        if (player == null) {
            queue = this.playerPacketMap.computeIfAbsent(null, k -> new ArrayList<>());
        } else {
            queue = this.playerPacketMap.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        }
        queue.add(Stream.of(packets));
    }

    /**
     * (Internal) Pops the packets off the queue for the given player.
     *
     * @param player The player to get packets for (or null for all players)
     * @return An array of packets to send in a single tick
     */
    public Stream<Packet<?>> popQueuedPackets(@Nullable final ServerPlayer player) {
        final List<Stream<Packet<?>>> queue = this.playerPacketMap.get(player == null ? null : player.getUUID());
        return queue == null || queue.isEmpty() ? Stream.empty() : queue.remove(0);
    }

    @Override
    public void performRangedAttack(final LivingEntity target, final float distanceFactor) {
        // Borrowed from Skeleton
        final Arrow entitytippedarrow = new Arrow(this.level, this);
        final double d0 = target.getX() - this.getX();
        final double d1 = target.getBoundingBox().minY + target.getBbHeight() / 3.0F - entitytippedarrow.getY();
        final double d2 = target.getZ() - this.getZ();
        final double d3 = Mth.sqrt(d0 * d0 + d2 * d2);
        entitytippedarrow.shoot(d0, d1 + d3 * 0.20000000298023224D, d2, 1.6F, 14 - this.level.getDifficulty().getId() * 4);
        // These names are wrong
        final int i = EnchantmentHelper.getEnchantmentLevel(Enchantments.PUNCH_ARROWS, this);
        final int j = EnchantmentHelper.getEnchantmentLevel(Enchantments.FLAMING_ARROWS, this);
        entitytippedarrow.setBaseDamage(distanceFactor * 2.0F + this.random.nextGaussian() * 0.25D + this.level.getDifficulty().getId() * 0.11F);

        if (i > 0) {
            entitytippedarrow.setBaseDamage(entitytippedarrow.getBaseDamage() + i * 0.5D + 0.5D);
        }

        if (j > 0) {
            entitytippedarrow.setKnockback(j);
        }

        final ItemStack itemstack = this.getItemInHand(InteractionHand.OFF_HAND);

        if (itemstack.getItem() == Items.TIPPED_ARROW) {
            entitytippedarrow.setEffectsFromItem(itemstack);
        }

        this.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (this.random.nextFloat() * 0.4F + 0.8F));
        this.level.addFreshEntity(entitytippedarrow);
    }
}
