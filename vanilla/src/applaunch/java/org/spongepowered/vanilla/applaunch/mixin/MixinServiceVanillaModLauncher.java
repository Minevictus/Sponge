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
package org.spongepowered.vanilla.applaunch.mixin;

import org.spongepowered.asm.launch.platform.container.ContainerHandleModLauncher;
import org.spongepowered.asm.service.modlauncher.MixinServiceModLauncher;
import org.spongepowered.asm.util.Constants;

public final class MixinServiceVanillaModLauncher extends MixinServiceModLauncher {

    private final ContainerHandleModLauncher rootContainer = new ContainerHandleVanillaModLauncher(this.getName());

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public ContainerHandleModLauncher getPrimaryContainer() {
        return this.rootContainer;
    }

    private static final class ContainerHandleVanillaModLauncher extends ContainerHandleModLauncher {

        public ContainerHandleVanillaModLauncher(String name) {
            super(name);

            this.setAttribute(Constants.ManifestAttributes.MIXINCONNECTOR, "org.spongepowered.vanilla.applaunch.mixin.VanillaLaunchMixinConnector");
        }
    }
}
