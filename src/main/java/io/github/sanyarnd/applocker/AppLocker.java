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
import java.nio.file.Paths;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import lombok.Getter;
import lombok.ToString;

import io.github.sanyarnd.applocker.exceptions.LockingBusyException;
import io.github.sanyarnd.applocker.exceptions.LockingCommunicationException;
import io.github.sanyarnd.applocker.exceptions.LockingException;
import io.github.sanyarnd.applocker.exceptions.LockingFailedException;
import io.github.sanyarnd.applocker.filesystem.LockNameProvider;
import io.github.sanyarnd.applocker.filesystem.Sha1Provider;

/**
 * Locker class, provides methods for locking mechanism and
 * encapsulates socket-based message server for IPC.
 * <p>
 * You don't need call {@link #unlock()} directly, method will be called once JVM is terminated.<br>
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

    private AppLocker(@Nonnull String id,
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
    public static Builder create(@Nonnull String id) {
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
     * Unlock the lock.<br>
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

    /**
     * AppLocker builder
     *
     * @author Alexander Biryukov
     */
    public static final class Builder {
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

        /**
         * Sets the path where the lock file will be stored<br>
         * Default value is "." (relative)
         *
         * @param path store path
         * @return builder
         */
        public Builder setPath(@Nonnull Path path) {
            this.path = path;
            return this;
        }

        /**
         * Sets the message handler.<br>
         * If not set, AppLocker won't support communication features <br>
         * Default value is null
         *
         * @param handler message handler
         * @return builder
         */
        public Builder setMessageHandler(@Nonnull MessageHandler<?, ?> handler) {
            this.handler = handler;
            return this;
        }

        /**
         * Sets the name provider.<br>
         * Provider encodes lock id to filesystem-friendly entry<br>
         * Default value is {@link Sha1Provider}
         *
         * @param provider name provider
         * @return builder
         */
        public Builder setNameProvider(@Nonnull LockNameProvider provider) {
            this.provider = provider;
            return this;
        }

        /**
         * Successful locking callback.<br>
         * Does nothing if not set
         *
         * @param callback the function to call after successful locking
         * @return builder
         */
        public Builder acquired(@Nonnull Runnable callback) {
            acquiredHandler = callback;
            return this;
        }

        /**
         * Lock is already taken callback.<br>
         * Default value is null
         *
         * @param message message for lock holder
         * @param handler answer processing function
         * @param <T>     answer type
         * @return builder
         */
        public <T extends Serializable> Builder busy(@Nonnull Serializable message, @Nonnull Consumer<T> handler) {
            busyHandler = (appLocker, ex) -> {
                T answer = appLocker.sendMessage(message);
                handler.accept(answer);
            };
            return this;
        }

        /**
         * Lock is already taken callback.<br>
         * Default value is null
         *
         * @param message message for lock holder
         * @param handler callback which ignores the answer
         * @return builder
         */
        public Builder busy(@Nonnull Serializable message, @Nonnull Runnable handler) {
            busyHandler = (appLocker, ex) -> {
                appLocker.sendMessage(message);
                handler.run();
            };
            return this;
        }

        /**
         * Unable to lock for unknown reasons callback.<br>
         * By default re-throws the exception
         *
         * @param handler error processing function
         * @return builder
         */
        public Builder failed(@Nonnull Consumer<LockingException> handler) {
            failedHandler = handler;
            return this;
        }

        /**
         * Build AppLocker
         *
         * @return AppLocker instance
         */
        public AppLocker build() {
            @SuppressWarnings("unchecked")
            Server<?, ?> server = handler != null ? new Server(handler) : null;

            return new AppLocker(id, path, provider, server, acquiredHandler, busyHandler, failedHandler);
        }
    }
}
