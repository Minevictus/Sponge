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
package org.spongepowered.common.network.status;

import org.spongepowered.api.MinecraftVersion;
import org.spongepowered.api.network.status.StatusClient;

import java.net.InetSocketAddress;
import java.util.Optional;

import javax.annotation.Nullable;

public class SpongeLegacyStatusClient implements StatusClient {

    private final InetSocketAddress address;
    private final MinecraftVersion version;
    private final Optional<InetSocketAddress> virtualHost;

    public SpongeLegacyStatusClient(InetSocketAddress address, MinecraftVersion version, @Nullable InetSocketAddress virtualHost) {
        this.address = address;
        this.version = version;
        this.virtualHost = Optional.ofNullable(virtualHost);
    }

    @Override
    public InetSocketAddress address() {
        return this.address;
    }

    @Override
    public MinecraftVersion version() {
        return this.version;
    }

    @Override
    public Optional<InetSocketAddress> virtualHost() {
        return this.virtualHost;
    }

}
