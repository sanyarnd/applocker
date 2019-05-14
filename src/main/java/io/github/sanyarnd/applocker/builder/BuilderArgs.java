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
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import io.github.sanyarnd.applocker.AppLocker;
import io.github.sanyarnd.applocker.exceptions.LockingException;
import io.github.sanyarnd.applocker.filesystem.LockNameProvider;
import io.github.sanyarnd.applocker.filesystem.Sha1Provider;

public interface BuilderArgs {
    /**
     * Sets the path where the lock file will be stored<br/>
     * Default value is "." (relative)
     *
     * @param path store path
     * @return builder
     */
    BuilderArgs setPath(@Nonnull Path path);

    /**
     * Sets the name provider.<br/>
     * Provider encodes lock id to filesystem-friendly entry<br/>
     * Default value is {@link Sha1Provider}
     *
     * @param provider name provider
     * @return builder
     */
    BuilderArgs setNameProvider(@Nonnull LockNameProvider provider);

    /**
     * Successful locking callback.<br/>
     * By default does nothing
     *
     * @param callback the function to call after successful locking
     * @return builder
     */
    BuilderArgs acquired(@Nonnull Runnable callback);

    /**
     * Unable to lock for unknown reasons callback.<br/>
     * By default re-throws the exception
     *
     * @param handler error processing function
     * @return builder
     */
    BuilderArgs failed(@Nonnull Consumer<LockingException> handler);

    /**
     * Lock is already taken callback.<br/>
     * By default does nothing (null)
     *
     * @param message message for lock holder
     * @param handler answer processing function
     * @param <T>     answer type
     * @return builder
     */
    <T extends Serializable> BuilderCommunicationArgs busy(@Nonnull Serializable message, @Nonnull Consumer<T> handler);

    /**
     * Build AppLocker
     *
     * @return AppLocker instance
     */
    AppLocker build();
}
