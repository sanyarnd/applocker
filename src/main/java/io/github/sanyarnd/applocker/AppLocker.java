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

package io.github.sanyarnd.applocker;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import lombok.Getter;
import lombok.ToString;

import io.github.sanyarnd.applocker.builder.Builder;
import io.github.sanyarnd.applocker.builder.BuilderArgs;
import io.github.sanyarnd.applocker.exceptions.LockingBusyException;
import io.github.sanyarnd.applocker.exceptions.LockingCommunicationException;
import io.github.sanyarnd.applocker.exceptions.LockingException;
import io.github.sanyarnd.applocker.exceptions.LockingFailedException;
import io.github.sanyarnd.applocker.filesystem.LockNameProvider;
import io.github.sanyarnd.applocker.messaging.Client;
import io.github.sanyarnd.applocker.messaging.Server;

/**
 * Locker class, provides methods for locking mechanism and
 * encapsulates socket-based message server for IPC.
 * <p>
 * You don't need call {@link #unlock()} directly, method will be called once JVM is terminated.<br/>
 * You're free to call {@link #unlock()} any time if it is required by your application logic.
 *
 * @author Alexander Biryukov
 */
@ToString(of = {"id", "appLock"})
public final class AppLocker {
    @Getter
    @Nonnull
    private final String id;
    @Nonnull
    private final Lock gLock;
    @Nonnull
    private final Lock appLock;
    @Nonnull
    private final Path portFile;
    @Nonnull
    private final Runtime runtime;
    @Nonnull
    private final Thread shutdownHook;
    @Nullable
    private final Server<?, ?> server;
    @Nonnull
    private final Runnable acquiredHandler;
    @Nullable
    private final BiConsumer<AppLocker, LockingBusyException> busyHandler;
    @Nonnull
    private final Consumer<LockingException> failedHandler;

    public AppLocker(@Nonnull String id,
                     @Nonnull Path lockPath,
                     @Nonnull LockNameProvider provider,
                     @Nullable Server<?, ?> server,
                     @Nonnull Runnable acquiredHandler,
                     @Nullable BiConsumer<AppLocker, LockingBusyException> busyHandler,
                     @Nonnull Consumer<LockingException> failedHandler) {
        this.id = id;
        this.server = server;
        this.acquiredHandler = acquiredHandler;
        this.busyHandler = busyHandler;
        this.failedHandler = failedHandler;
        lockPath = lockPath.toAbsolutePath();

        gLock = new Lock(lockPath.resolve(String.format(".%s.lock", provider.encrypt("Unique global lock"))));

        final String idEncryptName = provider.encrypt(id);
        appLock = new Lock(lockPath.resolve(String.format(".%s.lock", idEncryptName)));
        portFile = lockPath.resolve(String.format(".%s_port.lock", idEncryptName));

        runtime = Runtime.getRuntime();
        shutdownHook = new Thread(() -> unlock(true), String.format("AppLocker `%s` shutdownHook", id));
    }

    /**
     * Create the AppLocker builder
     *
     * @param id AppLocker unique ID
     * @return builder
     */
    public static BuilderArgs create(@Nonnull String id) {
        return new Builder(id);
    }

    /**
     * Acquire the lock
     *
     * @throws LockingBusyException          if lock is already taken by someone
     * @throws LockingFailedException        if any error occurred during the locking process (I/O exception)
     * @throws LockingCommunicationException if there are issues with starting message server
     */
    public void lock() {
        try {
            try {
                gLock.loopLock();

                appLock.lock();
                if (server != null) {
                    try {
                        server.start();
                        int port = server.getPortBlocking();
                        writeAppLockPortToFile(portFile, port);
                    } catch (IOException ex) {
                        appLock.unlock();
                        throw new LockingCommunicationException(ex);
                    }
                }

                runtime.addShutdownHook(shutdownHook);

                acquiredHandler.run();
            } finally {
                gLock.unlock();
            }
        } catch (LockingBusyException ex) {
            // fallback to failedHandler
            if (busyHandler != null) {
                try {
                    busyHandler.accept(this, ex);
                } catch (LockingException exx) {
                    failedHandler.accept(exx);
                }
            } else {
                failedHandler.accept(ex);
            }
        } catch (LockingException ex) {
            failedHandler.accept(ex);
        }
    }

    /**
     * Unlock the lock.<br/>
     * Does nothing if lock is not locked
     */
    public void unlock() {
        unlock(false);
    }

    private void unlock(boolean internal) {
        try {
            gLock.loopLock();
            if (!internal) {
                runtime.removeShutdownHook(shutdownHook);
            }

            try {
                if (server != null) {
                    server.stop();
                    Files.delete(portFile);
                }
            } finally {
                appLock.unlock();
            }
        } catch (IOException ignored) {
        } finally {
            gLock.unlock();
        }
    }

    /**
     * @return true if locked, false otherwise
     */
    public boolean isLocked() {
        return appLock.isLocked();
    }

    /**
     * Send message to AppLocker instance who's holding the lock (including itself)
     *
     * @param message message
     * @param <I>     message type
     * @param <O>     return type
     * @return the answer from AppLocker's message handler
     * @throws LockingCommunicationException if there is an issue with communicating to other AppLocker instance
     */
    @Nonnull
    public <I extends Serializable, O extends Serializable> O sendMessage(@Nonnull I message) {
        try {
            int port = getPortFromFile();
            Client<I, O> client = new Client<>(port);
            return client.send(message);
        } catch (NoSuchFileException ex) {
            throw new LockingCommunicationException("Unable to open port file, please check that message server is running");
        } catch (IOException ex) {
            throw new LockingCommunicationException(ex);
        }
    }

    private void writeAppLockPortToFile(@Nonnull Path portFile, int port) throws IOException {
        Files.write(portFile, ByteBuffer.allocate(4).putInt(port).array());
    }

    private int getPortFromFile() throws IOException {
        return ByteBuffer.wrap(Files.readAllBytes(portFile)).getInt();
    }


}
