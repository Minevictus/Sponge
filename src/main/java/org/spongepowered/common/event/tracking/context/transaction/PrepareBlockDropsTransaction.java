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
package org.spongepowered.common.event.tracking.context.transaction;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.event.CauseStackManager;
import org.spongepowered.common.block.SpongeBlockSnapshot;
import org.spongepowered.common.event.tracking.BlockChangeFlagManager;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.util.Constants;
import org.spongepowered.common.util.PrettyPrinter;

import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class PrepareBlockDropsTransaction extends BlockEventBasedTransaction {

    private final SpongeBlockSnapshot originalState;

    PrepareBlockDropsTransaction(
        final BlockPos affectedPosition, final BlockState originalState, final SpongeBlockSnapshot original
    ) {
        super(affectedPosition, originalState, original.world());
        this.originalState = original;
    }

    @Override
    protected SpongeBlockSnapshot getResultingSnapshot() {
        return null;
    }

    @Override
    protected SpongeBlockSnapshot getOriginalSnapshot() {
        return this.originalState;
    }

    @Override
    public Optional<BiConsumer<PhaseContext<@NonNull ?>, CauseStackManager.StackFrame>> getFrameMutator(
        @Nullable GameTransaction<@NonNull ?> parent
    ) {
        return Optional.of((context, frame) -> frame.pushCause(this.originalState));
    }

    @Override
    public void addToPrinter(final PrettyPrinter printer) {

    }

    @Override
    public void restore() {
        this.originalState.restore(true, BlockChangeFlagManager.fromNativeInt(Constants.BlockChangeFlags.FORCED_RESTORE));
    }
}
