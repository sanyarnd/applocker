package io.github.sanyarnd.applocker;

import static java.lang.String.format;

import java.io.IOException;
import java.io.Serializable;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The Locker class provides methods for a locking mechanism and encapsulates socket-based message server for IPC.
 *
 * <p>No need to call {@link #unlock()} directly: the OS releases file locks when the JVM exits.
 *
 * @author Alexander Biryukov
 */
// TODO: close() throws InterruptedException
@SuppressWarnings("try")
public final class AppLocker implements AutoCloseable {
    private static final Logger LOG = System.getLogger(AppLocker.class.getName());

    private static final String UNIQUE_GLOBAL_LOCK = "Unique global lock";
    private static final String LOCK_PORT_PATTERN = ".%s_port.lock";
    private static final String LOCK_NAME_PATTERN = ".%s.lock";
    private static final int LOCK_TIMEOUT_MS = 1000;
    private static final int PORT_TIMEOUT_MS = 1000;
    private static final int MAX_PORT = 0xFFFF;

    private final String lockId;
    private final Lock gLock;
    private final Lock appLock;
    private final Path portFile;
    private final @Nullable Server<?, ?> server;
    private final Runnable acquiredHandler;
    private final @Nullable BiConsumer<AppLocker, LockingBusyException> busyHandler;
    private final Consumer<LockingException> failedHandler;

    private AppLocker(
            final String nameId,
            final Path lockPath,
            final LockIdEncoder idEncoder,
            final @Nullable Server<?, ?> messageServer,
            final Runnable onAcquire,
            final @Nullable BiConsumer<AppLocker, LockingBusyException> onBusy,
            final Consumer<LockingException> onFail) {
        final Path path = lockPath.toAbsolutePath();
        final String encodedId = idEncoder.encode(nameId);

        lockId = nameId;
        server = messageServer;
        acquiredHandler = onAcquire;
        busyHandler = onBusy;
        failedHandler = onFail;

        gLock = new Lock(newLockFile(path, LOCK_NAME_PATTERN, idEncoder.encode(UNIQUE_GLOBAL_LOCK)));
        appLock = new Lock(newLockFile(path, LOCK_NAME_PATTERN, encodedId));
        portFile = newLockFile(path, LOCK_PORT_PATTERN, encodedId);
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

    private static Path newLockFile(final Path path, final String lockNamePattern, final String idEncoder) {
        return path.resolve(format(lockNamePattern, idEncoder));
    }

    @Override
    public String toString() {
        return format("AppLocker{lockId='%s', gLock=%s, appLock=%s, portFile=%s}", lockId, gLock, appLock, portFile);
    }

    @Override
    public void close() throws Exception {
        unlock();
    }

    /**
     * Acquire the lock.
     *
     * @throws LockingBusyException if lock has already been taken by someone
     * @throws LockingException if any error has occurred during the locking process (I/O exception)
     * @throws InterruptedException if the thread was interrupted while waiting for the global lock
     */
    public synchronized void lock() throws InterruptedException {
        if (isLocked()) {
            return;
        }

        try {
            lock0();
        } catch (LockingBusyException ex) {
            handleLockBusyException(ex);
        } catch (LockingException ex) {
            failedHandler.accept(ex);
        }
    }

    private void lock0() throws InterruptedException {
        gLock.lock(LOCK_TIMEOUT_MS);
        try {
            appLock.tryLock();
            if (server != null) {
                startServer(server);
            }
        } finally {
            gLock.close();
        }

        acquiredHandler.run();
    }

    private void startServer(final Server<?, ?> messageServer) throws InterruptedException {
        try {
            messageServer.start();
            final int port = messageServer.getPort(PORT_TIMEOUT_MS);
            writeAppLockPortToFile(portFile, port);
        } catch (IOException | RuntimeException | InterruptedException ex) {
            messageServer.stop();
            appLock.close();
            if (ex instanceof InterruptedException) {
                throw (InterruptedException) ex;
            }
            throw new LockingException("Unable to start the message server", ex);
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
     * Unlock the lock.
     *
     * <p>Does nothing if a lock is not locked by this instance.
     *
     * @throws InterruptedException if the thread was interrupted while waiting for the global lock
     */
    public synchronized void unlock() throws InterruptedException {
        if (!isLocked()) {
            return;
        }

        gLock.lock(LOCK_TIMEOUT_MS);
        try {
            if (server != null) {
                server.stop();
                deletePortFile();
            }
        } finally {
            appLock.close();
            gLock.close();
        }
    }

    private void deletePortFile() {
        try {
            Files.deleteIfExists(portFile);
        } catch (IOException ex) {
            LOG.log(Level.DEBUG, () -> "Unable to delete " + portFile, ex);
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
     * Send a message to AppLocker instance that's holding the lock (including self).
     *
     * @param message message
     * @param <I> message type
     * @param <O> return type
     * @return the answer from AppLocker's message messageHandler
     * @throws LockingException if there's a trouble communicating to other AppLocker instance
     */
    @SuppressWarnings("TypeParameterUnusedInFormals") // TODO: unchecked return type
    public <I extends Serializable, O extends Serializable> O sendMessage(final I message) {
        try {
            final int port = getPortFromFile();
            final Client<I, O> client = new Client<>(port);
            return client.send(message);
        } catch (NoSuchFileException ex) {
            throw new LockingException("Unable to open port file, please check that message server is running", ex);
        } catch (IOException ex) {
            throw new LockingException("Unable to read port file", ex);
        }
    }

    private static void writeAppLockPortToFile(final Path portFilePath, final int portNumber) throws IOException {
        final Path tmp = portFilePath.resolveSibling(portFilePath.getFileName() + ".tmp");
        Files.write(tmp, ByteBuffer.allocate(Integer.BYTES).putInt(portNumber).array());
        Files.move(tmp, portFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private int getPortFromFile() throws IOException {
        LOG.log(Level.DEBUG, "Reading port file {0}", portFile);
        final byte[] bytes = Files.readAllBytes(portFile);
        if (bytes.length != Integer.BYTES) {
            throw new LockingException(format("Port file '%s' is corrupted", portFile));
        }
        final int port = ByteBuffer.wrap(bytes).getInt();
        if (port <= 0 || port > MAX_PORT) {
            throw new LockingException(format("Port file '%s' contains invalid port %d", portFile, port));
        }
        return port;
    }

    /**
     * AppLocker builder.
     *
     * @author Alexander Biryukov
     */
    public static final class Builder {
        private final String id;
        private Path path = Paths.get("");
        private LockIdEncoder encoder = new Sha1Encoder();
        private @Nullable MessageHandler<?, ?> messageHandler;
        private Runnable acquiredHandler = () -> {};
        private Consumer<LockingException> failedHandler = ex -> {
            throw ex;
        };
        private @Nullable BiConsumer<AppLocker, LockingBusyException> busyHandler;

        /**
         * Create Application Locker builder.
         *
         * @param lockId lock id
         */
        public Builder(final String lockId) {
            id = lockId;
        }

        /**
         * Sets the path where the lock file will be stored.<br>
         * Default value is ""
         *
         * @param storePath storing path
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
         * @param idEncoder name encoder
         * @return builder
         */
        public Builder setIdEncoder(final LockIdEncoder idEncoder) {
            encoder = idEncoder;
            return this;
        }

        /**
         * Defines a callback if locking was successful.<br>
         * Default value is empty function.
         *
         * @param callback function to call after successful locking
         * @return builder
         */
        public Builder onSuccess(final Runnable callback) {
            acquiredHandler = callback;
            return this;
        }

        /**
         * Defines the action for when the lock is already taken.<br>
         * Default value is null.
         *
         * @param message message for the lock holder
         * @param handler answer processing function
         * @param <T> answer type
         * @return builder
         */
        public <T extends Serializable> Builder onBusy(final Serializable message, final Consumer<T> handler) {
            busyHandler = (appLocker, ex) -> {
                final T answer = appLocker.sendMessage(message);
                handler.accept(answer);
            };
            return this;
        }

        /**
         * Defines the action for when the lock is already taken.<br>
         * Default value is null.
         *
         * @param message message for the lock holder
         * @param handler answer processing function
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
         * Defines the action for when locking is impossible.<br>
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
         * Defines the action for when locking is impossible.<br>
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
            final Server<?, ?> server = messageHandler != null ? new Server<>(messageHandler) : null;

            return new AppLocker(id, path, encoder, server, acquiredHandler, busyHandler, failedHandler);
        }
    }
}
