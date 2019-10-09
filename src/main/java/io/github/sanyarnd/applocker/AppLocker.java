package io.github.sanyarnd.applocker;

import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Locker class provides methods for locking mechanism and encapsulates socket-based message server for IPC.
 *
 * <p>No need to call {@link #unlock()} directly, method will be called once JVM is terminated.<br> Feel free to call
 * {@link #unlock()} any time if it is required by your application logic.
 *
 * @author Alexander Biryukov
 */
@ToString(of = {"lockId", "appLock"})
public final class AppLocker {
    private static final String LOCK_NAME_PATTERN = ".%s.lock";
    private final @NotNull String lockId;
    private final @NotNull Lock gLock;
    private final @NotNull Lock appLock;
    private final @NotNull Path portFile;
    private final @NotNull Runtime runtime;
    private final Thread shutdownHook;
    private final @Nullable Server<?, ?> server;
    private final @NotNull Runnable acquiredHandler;
    private final @Nullable BiConsumer<AppLocker, LockingBusyException> busyHandler;
    private final @NotNull Consumer<LockingException> failedHandler;

    private AppLocker(final @NotNull String nameId,
                      final @NotNull Path lockPath,
                      final @NotNull LockIdEncoder idEncoder,
                      final @Nullable Server<?, ?> messageServer,
                      final @NotNull Runnable onAcquire,
                      final @Nullable BiConsumer<AppLocker, LockingBusyException> onBusy,
                      final @NotNull Consumer<LockingException> onFail) {
        lockId = nameId;
        server = messageServer;
        acquiredHandler = onAcquire;
        busyHandler = onBusy;
        failedHandler = onFail;
        final Path path = lockPath.toAbsolutePath();

        gLock = new Lock(path.resolve(String.format(LOCK_NAME_PATTERN, idEncoder.encode("Unique global lock"))));

        final String encodedId = idEncoder.encode(nameId);
        appLock = new Lock(path.resolve(String.format(LOCK_NAME_PATTERN, encodedId)));
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
    public static @NotNull Builder create(final @NotNull String id) {
        return new Builder(id);
    }

    /**
     * Acquire the lock.
     *
     * @throws LockingBusyException          if lock has already been taken by someone
     * @throws LockingFailedException        if any error has occurred during the locking process (I/O exception)
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
                    final int port = server.getPortBlocking();
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

    private void handleLockBusyException(final @NotNull LockingBusyException ex) {
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
     * Unlock the lock.<br> Does nothing if lock is not locked.
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
     * Check if locker is busy.
     *
     * @return true if locked, false otherwise
     */
    public boolean isLocked() {
        return appLock.isLocked();
    }

    /**
     * Send message to AppLocker instance that's holding the lock (including self).
     *
     * @param message message
     * @param <I>     message type
     * @param <O>     return type
     * @return the answer from AppLocker's message messageHandler
     * @throws LockingCommunicationException if there's a trouble communicating to other AppLocker instance
     */
    public @NotNull <I extends Serializable, O extends Serializable> O sendMessage(final @NotNull I message) {
        try {
            final int port = getPortFromFile();
            final Client<I, O> client = new Client<>(port);
            return client.send(message);
        } catch (NoSuchFileException ex) {
            throw new LockingCommunicationException(
                    "Unable to open port file, please check that message server is running");
        } catch (IOException ex) {
            throw new LockingCommunicationException(ex);
        }
    }

    private void writeAppLockPortToFile(final @NotNull Path portFilePath, final int portNumber) throws IOException {
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
        private final @NotNull String id;
        private @NotNull Path path = Paths.get(".");
        private @NotNull LockIdEncoder encoder = new Sha1Encoder();
        private @Nullable MessageHandler<?, ?> messageHandler;
        private @NotNull Runnable acquiredHandler = () -> { };
        private @NotNull Consumer<LockingException> failedHandler = ex -> {
            throw ex;
        };
        private @Nullable BiConsumer<AppLocker, LockingBusyException> busyHandler;

        public Builder(final String lockId) {
            id = lockId;
        }

        /**
         * Sets the path where the lock file will be stored.<br> Default value is "."
         *
         * @param storePath storing path
         * @return builder
         */
        public @NotNull Builder setPath(final @NotNull Path storePath) {
            path = storePath;
            return this;
        }

        /**
         * Sets the message handler.<br> If not set, AppLocker won't support communication features.<br> Default value
         * is null.
         *
         * @param handler message handler
         * @return builder
         */
        public @NotNull Builder setMessageHandler(final @NotNull MessageHandler<?, ?> handler) {
            messageHandler = handler;
            return this;
        }

        /**
         * Sets the name encoder.<br> Encodes lock lockId to filesystem-friendly entry.<br> Default value is "SHA-1"
         * encoder.
         *
         * @param idEncoder name encoder
         * @return builder
         */
        public @NotNull Builder setIdEncoder(final @NotNull LockIdEncoder idEncoder) {
            encoder = idEncoder;
            return this;
        }

        /**
         * Defines a callback if locking was successful.<br> Default value is empty function.
         *
         * @param callback function to call after successful locking
         * @return builder
         */
        public @NotNull Builder onSuccess(final @NotNull Runnable callback) {
            acquiredHandler = callback;
            return this;
        }

        /**
         * Defines the action for when the lock is already taken.<br> Default value is null.
         *
         * @param message message for the lock holder
         * @param handler answer processing function
         * @param <T>     answer type
         * @return builder
         */
        public @NotNull <T extends Serializable> Builder onBusy(final @NotNull Serializable message,
                                                                final @NotNull Consumer<T> handler) {
            busyHandler = (appLocker, ex) -> {
                final T answer = appLocker.sendMessage(message);
                handler.accept(answer);
            };
            return this;
        }

        /**
         * Defines the action for when the lock is already taken.<br> Default value is null.
         *
         * @param message message for the lock holder
         * @param handler answer processing function
         * @return builder
         */
        public @NotNull Builder onBusy(final @NotNull Serializable message, final @NotNull Runnable handler) {
            busyHandler = (appLocker, ignoredException) -> {
                appLocker.sendMessage(message);
                handler.run();
            };
            return this;
        }

        /**
         * Defines the action for when locking is impossible.<br> Default value is identity function (re-throws
         * exception).
         *
         * @param handler error processing function
         * @return builder
         */
        public @NotNull Builder onFail(final @NotNull Consumer<LockingException> handler) {
            failedHandler = handler;
            return this;
        }

        /**
         * Defines the action for when locking is impossible.<br> Default value is identity function (re-throws
         * exception).
         *
         * @param handler error processing function
         * @return builder
         */
        public @NotNull Builder onFail(final @NotNull Runnable handler) {
            failedHandler = ignoredException -> handler.run();
            return this;
        }

        /**
         * Build AppLocker.
         *
         * @return AppLocker instance
         */
        public @NotNull AppLocker build() {
            @SuppressWarnings("unchecked") final Server<?, ?> server = messageHandler != null
                    ? new Server(messageHandler)
                    : null;

            return new AppLocker(id, path, encoder, server, acquiredHandler, busyHandler, failedHandler);
        }
    }
}
