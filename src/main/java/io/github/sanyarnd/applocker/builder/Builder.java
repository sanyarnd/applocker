// This is an open source non-commercial project. Dear PVS-Studio, please check it.
// PVS-Studio Static Code Analyzer for C, C++, C#, and Java: http://www.viva64.com
//
//                     Copyright 2019 Alexander Biryukov
//
//     Licensed under the Apache License, Version 2.0 (the "License");
//     you may not use this file except in compliance with the License.
//     You may obtain a copy of the License at
//
//               http://www.apache.org/licenses/LICENSE-2.0
//
//     Unless required by applicable law or agreed to in writing, software
//     distributed under the License is distributed on an "AS IS" BASIS,
//     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//     See the License for the specific language governing permissions and
//     limitations under the License.

package io.github.sanyarnd.applocker.builder;

import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.github.sanyarnd.applocker.AppLocker;
import io.github.sanyarnd.applocker.exceptions.LockingBusyException;
import io.github.sanyarnd.applocker.exceptions.LockingException;
import io.github.sanyarnd.applocker.filesystem.LockNameProvider;
import io.github.sanyarnd.applocker.filesystem.Sha1Provider;
import io.github.sanyarnd.applocker.messaging.MessageHandler;
import io.github.sanyarnd.applocker.messaging.Server;

/**
 * AppLocker builder
 *
 * @author Alexander Biryukov
 */
public final class Builder implements BuilderCommunicationArgs, BuilderArgs {
    @Nonnull
    private final String id;
    @Nonnull
    private Path path = Paths.get(".");
    @Nonnull
    private LockNameProvider provider = new Sha1Provider();
    @Nullable
    private MessageHandler<?, ?> handler = null;
    @Nonnull
    private Runnable acquiredHandler = () -> {};
    @Nonnull
    private Consumer<LockingException> failedHandler = ex -> { throw ex; };
    @Nullable
    private BiConsumer<AppLocker, LockingBusyException> busyHandler;

    public Builder(@Nonnull String id) { this.id = id; }

    @Override
    public BuilderArgs setPath(@Nonnull Path path) {
        this.path = path;
        return this;
    }

    @Override
    public BuilderArgs setMessageHandler(@Nonnull MessageHandler<?, ?> handler) {
        this.handler = handler;
        return this;
    }

    @Override
    public BuilderArgs setNameProvider(@Nonnull LockNameProvider provider) {
        this.provider = provider;
        return this;
    }

    @Override
    public BuilderArgs acquired(@Nonnull Runnable callback) {
        acquiredHandler = callback;
        return this;
    }

    @Override
    public <T extends Serializable> BuilderCommunicationArgs busy(@Nonnull Serializable message, @Nonnull Consumer<T> handler) {
        busyHandler = (appLocker, ex) -> {
            T answer = appLocker.sendMessage(message);
            handler.accept(answer);
        };
        return this;
    }

    @Override
    public BuilderArgs failed(@Nonnull Consumer<LockingException> handler) {
        failedHandler = handler;

        return this;
    }

    @Override
    public AppLocker build() {
        @SuppressWarnings("unchecked")
        Server<?, ?> server = handler != null ? new Server(handler) : null;

        return new AppLocker(id, path, provider, server, acquiredHandler, busyHandler, failedHandler);
    }
}
