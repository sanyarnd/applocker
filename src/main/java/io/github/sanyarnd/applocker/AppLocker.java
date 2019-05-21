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

import org.checkerframework.checker.nullness.qual.Nullable;

import lombok.ToString;

import io.github.sanyarnd.applocker.exceptions.LockingBusyException;
import io.github.sanyarnd.applocker.exceptions.LockingCommunicationException;
import io.github.sanyarnd.applocker.exceptions.LockingException;
import io.github.sanyarnd.applocker.exceptions.LockingFailedException;

/**
 * Locker class, provides methods for locking mechanism and
 * encapsulates socket-based message server for IPC.
 * <p>
 * You don't need call {@link #unlock()} directly, method will be called once JVM is terminated.<br>
 * You're free to call {@link #unlock()} any time if it is required by your application logic.
 *
 * @author Alexander Biryukov
 */
@ToString(of = {"lockId", "appLock"})
public final class AppLocker {
    private final String lockId;
    private final Lock gLock;
    private final Lock appLock;
    private final Path portFile;
    private final Runtime runtime;
    private final Thread shutdownHook;
    @Nullable
    private final Server<?, ?> server;
    private final Runnable acquiredHandler;
    @Nullable
    private final BiConsumer<AppLocker, LockingBusyException> busyHandler;
    private final Consumer<LockingException> failedHandler;

    private AppLocker(final String nameId,
                      final Path lockPath,
                      final LockIdEncoder idEncoder,
                      @Nullable final Server<?, ?> messageServer,
                      final Runnable onAcquire,
                      @Nullable final BiConsumer<AppLocker, LockingBusyException> onBusy,
                      final Consumer<LockingException> onFail) {
        lockId = nameId;
        server = messageServer;
        acquiredHandler = onAcquire;
        busyHandler = onBusy;
        failedHandler = onFail;
        final Path path = lockPath.toAbsolutePath();

        gLock = new Lock(path.resolve(String.format(".%s.lock", idEncoder.encode("Unique global lock"))));

        final String encodedId = idEncoder.encode(nameId);
        appLock = new Lock(path.resolve(String.format(".%s.lock", encodedId)));
        portFile = path.resolve(String.format(".%s_port.lock", encodedId));

        runtime = Runtime.getRuntime();
        shutdownHook = new Thread(() -> unlock(true), String.format("AppLocker `%s` shutdownHook", nameId));
    }

    /**
     * Create the AppLocker builder.
     *
     * @param id AppLocker unique ID
     * @return builder
     */
    public static Builder create(final String id) {
        return new Builder(id);
    }

    /**
     * Acquire the lock.
     *
     * @throws LockingBusyException          if lock is already taken by someone
     * @throws LockingFailedException        if any error occurred during the locking process (I/O exception)
     * @throws LockingCommunicationException if there are issues with starting message server
     */
    public void lock() {
        try {
            lockInternal();
        } catch (LockingBusyException ex) {
            handleLockBusyException(ex);
        } catch (LockingException ex) {
            failedHandler.accept(ex);
        }
    }

    private void lockInternal() {
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
    }

    private void handleLockBusyException(final LockingBusyException ex) {
        // if busy != null then prefer busy
        if (busyHandler != null) {
            try {
                busyHandler.accept(this, ex);
            } catch (LockingException exx) {
                failedHandler.accept(exx);
            }
        } else {
            failedHandler.accept(ex);
        }
    }

    /**
     * Unlock the lock.<br>
     * Does nothing if lock is not locked.
     */
    public void unlock() {
        unlock(false);
    }

    private void unlock(final boolean internal) {
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
     * Send message to AppLocker instance who's holding the lock (including itself).
     *
     * @param message message
     * @param <I>     message type
     * @param <O>     return type
     * @return the answer from AppLocker's message messageHandler
     * @throws LockingCommunicationException if there is an issue with communicating to other AppLocker instance
     */
    public <I extends Serializable, O extends Serializable> O sendMessage(final I message) {
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

    private void writeAppLockPortToFile(final Path portFilePath, final int portNumber) throws IOException {
        final int bytesInInt = 4;
        Files.write(portFilePath, ByteBuffer.allocate(bytesInInt).putInt(portNumber).array());
    }

    private int getPortFromFile() throws IOException {
        return ByteBuffer.wrap(Files.readAllBytes(portFile)).getInt();
    }

    /**
     * AppLocker builder.
     *
     * @author Alexander Biryukov
     */
    public static final class Builder {
        private final String id;
        private Path path = Paths.get(".");
        private LockIdEncoder encoder = new Sha1Encoder();
        @Nullable
        private MessageHandler<?, ?> messageHandler = null;
        private Runnable acquiredHandler = () -> { };
        private Consumer<LockingException> failedHandler = ex -> { throw ex; };
        @Nullable
        private BiConsumer<AppLocker, LockingBusyException> busyHandler = null;

        public Builder(final String lockId) { id = lockId; }

        /**
         * Sets the path where the lock file will be stored.<br>
         * Default value is "."
         *
         * @param storePath store path
         * @return builder
         */
        public Builder setPath(final Path storePath) {
            path = storePath;
            return this;
        }

        /**
         * Sets the message handler.<br>
         * If not set, AppLocker won't support communication features.<br>
         * Default value is null.
         *
         * @param handler message handler
         * @return builder
         */
        public Builder setMessageHandler(final MessageHandler<?, ?> handler) {
            messageHandler = handler;
            return this;
        }

        /**
         * Sets the name encoder.<br>
         * Encodes lock lockId to filesystem-friendly entry.<br>
         * Default value is "SHA-1" encoder.
         *
         * @param provider name encoder
         * @return builder
         */
        public Builder setIdEncoder(final LockIdEncoder provider) {
            encoder = provider;
            return this;
        }

        /**
         * Defines a callback if locking was successful.<br>
         * Default value is empty function.
         *
         * @param callback the function to call after successful locking
         * @return builder
         */
        public Builder onSuccess(final Runnable callback) {
            acquiredHandler = callback;
            return this;
        }

        /**
         * Defines an action in situations when lock is already taken.<br>
         * Default value is null.
         *
         * @param message message for lock holder
         * @param handler answer processing function
         * @param <T>     answer type
         * @return builder
         */
        public <T extends Serializable> Builder onBusy(final Serializable message, final Consumer<T> handler) {
            busyHandler = (appLocker, ex) -> {
                T answer = appLocker.sendMessage(message);
                handler.accept(answer);
            };
            return this;
        }

        /**
         * Defines an action in situations when lock is already taken.<br>
         * Default value is null.
         *
         * @param message message for lock holder
         * @param handler callback which ignores the answer
         * @return builder
         */
        public Builder onBusy(final Serializable message, final Runnable handler) {
            busyHandler = (appLocker, ignoredException) -> {
                appLocker.sendMessage(message);
                handler.run();
            };
            return this;
        }

        /**
         * Defines an action in situations when locking is impossible.<br>
         * Default value is identity function (re-throws exception).
         *
         * @param handler error processing function
         * @return builder
         */
        public Builder onFail(final Consumer<LockingException> handler) {
            failedHandler = handler;
            return this;
        }

        /**
         * Defines an action in situations when locking is impossible.<br>
         * Default value is identity function (re-throws exception).
         *
         * @param handler error processing function
         * @return builder
         */
        public Builder onFail(final Runnable handler) {
            failedHandler = ignoredException -> handler.run();
            return this;
        }

        /**
         * Build AppLocker.
         *
         * @return AppLocker instance
         */
        public AppLocker build() {
            @SuppressWarnings("unchecked")
            Server<?, ?> server = messageHandler != null ? new Server(messageHandler) : null;

            return new AppLocker(id, path, encoder, server, acquiredHandler, busyHandler, failedHandler);
        }
    }
}
