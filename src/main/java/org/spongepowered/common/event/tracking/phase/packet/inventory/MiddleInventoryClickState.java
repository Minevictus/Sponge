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
package org.spongepowered.common.event.tracking.phase.packet.inventory;

import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.item.inventory.container.ClickContainerEvent;
import org.spongepowered.api.item.inventory.Container;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.item.inventory.Slot;
import org.spongepowered.api.item.inventory.transaction.SlotTransaction;
import org.spongepowered.common.event.tracking.PhaseTracker;
import org.spongepowered.common.util.Constants;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.Optional;

public final class MiddleInventoryClickState extends BasicInventoryPacketState {

    public MiddleInventoryClickState() {
        super(
            Constants.Networking.BUTTON_PRIMARY // The primary is set if the pick block is used as a different button mapping.
                | Constants.Networking.BUTTON_MIDDLE
                | Constants.Networking.MODE_PICKBLOCK
                | Constants.Networking.CLICK_INSIDE_WINDOW
                | Constants.Networking.CLICK_OUTSIDE_WINDOW);
    }

    @Override
    public ClickContainerEvent createInventoryEvent(final ServerPlayer playerMP, final Container openContainer,
        final Transaction<ItemStackSnapshot> transaction,
        final List<SlotTransaction> slotTransactions, final List<Entity> capturedEntities, final int usedButton,
        @Nullable final Slot slot) {
        return SpongeEventFactory.createClickContainerEventMiddle(
            PhaseTracker.getCauseStackManager().currentCause(),
            openContainer, transaction,
            Optional.ofNullable(slot), slotTransactions);
    }

}
