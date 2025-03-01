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
package org.spongepowered.common.entity;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import org.spongepowered.api.data.persistence.DataContainer;
import org.spongepowered.api.entity.EntityArchetype;
import org.spongepowered.api.entity.EntitySnapshot;
import org.spongepowered.api.entity.EntityType;
import org.spongepowered.api.event.EventContextKeys;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.cause.entity.SpawnType;
import org.spongepowered.api.event.entity.SpawnEntityEvent;
import org.spongepowered.api.world.server.ServerLocation;
import org.spongepowered.common.bridge.data.DataContainerHolder;
import org.spongepowered.common.data.AbstractArchetype;
import org.spongepowered.common.data.SpongeDataManager;
import org.spongepowered.common.data.nbt.validation.RawDataValidator;
import org.spongepowered.common.data.nbt.validation.ValidationType;
import org.spongepowered.common.data.nbt.validation.ValidationTypes;
import org.spongepowered.common.data.persistence.NBTTranslator;
import org.spongepowered.common.data.provider.DataProviderLookup;
import org.spongepowered.common.event.tracking.PhaseTracker;
import org.spongepowered.common.hooks.PlatformHooks;
import org.spongepowered.common.util.Constants;
import org.spongepowered.math.vector.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

public class SpongeEntityArchetype extends AbstractArchetype<EntityType, EntitySnapshot, org.spongepowered.api.entity.Entity> implements EntityArchetype,
        DataContainerHolder.Mutable {

    // TODO actually validate stuff
    public static final ImmutableList<RawDataValidator> VALIDATORS = ImmutableList.of();

    private static final DataProviderLookup lookup = SpongeDataManager.getProviderRegistry().getProviderLookup(SpongeEntityArchetype.class);

    @Nullable
    private Vector3d position;

    SpongeEntityArchetype(final SpongeEntityArchetypeBuilder builder) {
        super(builder.entityType, builder.compound != null ? builder.compound : builder.entityData == null ? new CompoundTag() :
                NBTTranslator.INSTANCE.translate(builder.entityData));
    }

    @Override
    public EntityType<?> type() {
        return this.type;
    }

    @Nullable
    public CompoundTag getData() {
        return this.data;
    }

    @Override
    public DataProviderLookup getLookup() {
        return SpongeEntityArchetype.lookup;
    }

    public Optional<Vector3d> getPosition() {
        if (this.position != null) {
            return Optional.of(this.position);
        }
        if (!this.data.contains(Constants.Entity.ENTITY_POSITION, Constants.NBT.TAG_LIST)) {
            return Optional.empty();
        }
        try {
            final ListTag pos = this.data.getList(Constants.Entity.ENTITY_POSITION, Constants.NBT.TAG_DOUBLE);
            final double x = pos.getDouble(0);
            final double y = pos.getDouble(1);
            final double z = pos.getDouble(2);
            this.position = new Vector3d(x, y, z);
            return Optional.of(this.position);
        } catch (final Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public DataContainer data$getDataContainer() {
        return this.entityData();
    }

    @Override
    public void data$setDataContainer(final DataContainer container) {
        this.data = NBTTranslator.INSTANCE.translate(container);
    }

    @Override
    public DataContainer entityData() {
        return NBTTranslator.INSTANCE.translateFrom(this.data);
    }

    @Override
    public Optional<org.spongepowered.api.entity.Entity> apply(final ServerLocation location) {
        if (!PlatformHooks.INSTANCE.getGeneralHooks().onServerThread()) {
            return Optional.empty();
        }
        final org.spongepowered.api.world.server.ServerWorld spongeWorld = location.world();
        final ServerLevel worldServer = (ServerLevel) spongeWorld;

        final Entity entity = ((net.minecraft.world.entity.EntityType<?>) this.type).create(worldServer);
        if (entity == null) {
            return Optional.empty();
        }
        entity.setPos(location.x(), location.y(), location.z()); // Set initial position

        final boolean requiresInitialSpawn;
        if (this.data.contains(Constants.Sponge.EntityArchetype.REQUIRES_EXTRA_INITIAL_SPAWN)) {
            requiresInitialSpawn = !this.data.getBoolean(Constants.Sponge.EntityArchetype.REQUIRES_EXTRA_INITIAL_SPAWN);
            this.data.remove(Constants.Sponge.EntityArchetype.REQUIRES_EXTRA_INITIAL_SPAWN);
        } else {
            requiresInitialSpawn = true;
        }

        if (entity instanceof Mob) {
            final Mob mobentity = (Mob) entity;
            mobentity.yHeadRot = mobentity.yRot;
            mobentity.yBodyRot = mobentity.xRot;
            if (requiresInitialSpawn) {
                // TODO null reason?
                mobentity.finalizeSpawn(worldServer, worldServer.getCurrentDifficultyAt(mobentity.blockPosition()), null, null, null);
            }
        }

        // like applyItemNBT
        final CompoundTag mergedNbt = entity.saveWithoutId(new CompoundTag());
        final UUID uniqueID = entity.getUUID();

        mergedNbt.merge(this.data);
        mergedNbt.remove(Constants.Sponge.EntityArchetype.REQUIRES_EXTRA_INITIAL_SPAWN);
        mergedNbt.putString(Constants.Sponge.World.WORLD_KEY, location.worldKey().formatted());
        mergedNbt.putUUID(Constants.Entity.ENTITY_UUID, uniqueID); // TODO can we avoid this when the entity is only spawned once?
        entity.load(mergedNbt); // Read in all data
        entity.setPos(location.x(), location.y(), location.z());

        // Finished building the entity. Now spawn it if not cancelled.
        final org.spongepowered.api.entity.Entity spongeEntity = (org.spongepowered.api.entity.Entity) entity;
        final List<org.spongepowered.api.entity.Entity> entities = new ArrayList<>();
        entities.add(spongeEntity);

        // We require spawn types. This is more of a sanity check to throw an IllegalStateException otherwise for the plugin developer to properly associate the type.
        final SpawnType require = PhaseTracker.getCauseStackManager().currentContext().require(EventContextKeys.SPAWN_TYPE);
        final SpawnEntityEvent.Custom event = SpongeEventFactory.createSpawnEntityEventCustom(PhaseTracker.getCauseStackManager().currentCause(), entities);
        if (!event.isCancelled()) {
            worldServer.addFreshEntity(entity);
            return Optional.of(spongeEntity);
        }
        return Optional.empty();
    }

    @Override
    public EntitySnapshot toSnapshot(final ServerLocation location) {
        final SpongeEntitySnapshotBuilder builder = new SpongeEntitySnapshotBuilder();
        builder.entityType = this.type;
        final CompoundTag newCompound = this.data.copy();
        final Vector3d pos = location.position();
        newCompound.put(Constants.Entity.ENTITY_POSITION, Constants.NBT.newDoubleNBTList(pos.getX(), pos.getY(), pos.getZ()));
        newCompound.putString(Constants.Sponge.World.WORLD_KEY, location.worldKey().formatted());
        builder.compound = newCompound;
        builder.worldKey = location.world().properties().key();
        builder.position = pos;
        builder.rotation = this.getRotation();
        builder.scale = Vector3d.ONE;
        return builder.build();
    }

    private Vector3d getRotation() {
        final ListTag listnbt3 = this.data.getList("Rotation", 5);
        final float rotationYaw = listnbt3.getFloat(0);
        final float rotationPitch = listnbt3.getFloat(1);
        return new Vector3d(rotationPitch, rotationYaw, 0);
    }

    @Override
    public int contentVersion() {
        return Constants.Sponge.EntityArchetype.BASE_VERSION;
    }

    @Override
    public DataContainer toContainer() {
        return DataContainer.createNew()
                .set(Constants.Sponge.EntityArchetype.ENTITY_TYPE, this.type)
                .set(Constants.Sponge.EntityArchetype.ENTITY_DATA, this.entityData());
    }

    @Override
    protected ValidationType getValidationType() {
        return ValidationTypes.ENTITY.get();
    }

    @Override
    public EntityArchetype copy() {
        final SpongeEntityArchetypeBuilder builder = new SpongeEntityArchetypeBuilder();
        builder.entityType = this.type;
        builder.entityData = NBTTranslator.INSTANCE.translate(this.data);
        return builder.build();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final SpongeEntityArchetype that = (SpongeEntityArchetype) o;
        return Objects.equals(this.position, that.position);
    }

    @Override
    protected ImmutableList<RawDataValidator> getValidators() {
        return SpongeEntityArchetype.VALIDATORS;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), this.position);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("position", this.position)
                .add("type", this.type)
                .toString();
    }
}
