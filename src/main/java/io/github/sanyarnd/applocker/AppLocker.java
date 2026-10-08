package io.github.sanyarnd.applocker;

import static java.lang.String.format;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import org.jspecify.annotations.Nullable;

/// The Locker class provides methods for a locking mechanism and encapsulates socket-based message server for IPC.
///
/// No need to call [#unlock()] directly: the OS releases file locks when the JVM exits.
///
/// @author Alexander Biryukov
public final class AppLocker implements AutoCloseable {
    private static final Logger LOG = System.getLogger(AppLocker.class.getName());

    private static final String LOCK_PORT_PATTERN = ".%s_port.lock";
    private static final String LOCK_NAME_PATTERN = ".%s.lock";
    private static final int MAX_PORT = 0xFFFF;
    private static final int PORT_FILE_BYTES = Integer.BYTES + Protocol.TOKEN_BYTES;

    private final String lockId;
    private final Lock appLock;
    private final Path portFile;
    private final int messageTimeoutMs;
    private final @Nullable Server server;

    private AppLocker(
            final String nameId,
            final Path lockPath,
            final LockIdEncoder idEncoder,
            final @Nullable Server messageServer,
            final Duration messageTimeout) {
        final Path path = lockPath.toAbsolutePath();
        final String encodedId = idEncoder.encode(nameId);

        lockId = nameId;
        server = messageServer;
        messageTimeoutMs = toSocketTimeout(messageTimeout);

        appLock = new Lock(newLockFile(path, LOCK_NAME_PATTERN, encodedId));
        portFile = newLockFile(path, LOCK_PORT_PATTERN, encodedId);
    }

    // 0 means "no timeout" for sockets
    private static int toSocketTimeout(final Duration timeout) {
        if (timeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) >= 0) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.max(1, timeout.toMillis());
    }

    /// Create the AppLocker builder.
    ///
    /// @param id AppLocker unique ID
    /// @return builder
    public static Builder create(final String id) {
        return new Builder(id);
    }

    private static Path newLockFile(final Path path, final String lockNamePattern, final String idEncoder) {
        return path.resolve(format(lockNamePattern, idEncoder));
    }

    @Override
    public String toString() {
        return format("AppLocker{lockId='%s', appLock=%s, portFile=%s}", lockId, appLock, portFile);
    }

    @Override
    public void close() {
        unlock();
    }

    /// Acquire the lock if nobody else holds it.
    ///
    /// @return true if the lock is held by this instance, false if it's held by another one
    /// @throws LockingException if any error has occurred during the locking process (I/O exception)
    public synchronized boolean tryLock() {
        if (isLocked()) {
            return true;
        }
        if (!appLock.tryLock()) {
            return false;
        }
        if (server != null) {
            startServer(server);
        }
        return true;
    }

    private void startServer(final Server messageServer) {
        final byte[] token = Protocol.newToken();
        try {
            writePortFile(portFile, messageServer.start(token), token);
        } catch (IOException | RuntimeException ex) {
            messageServer.stop();
            appLock.close();
            throw new LockingException("Unable to start the message server", ex);
        }
    }

    /// Unlock the lock.
    ///
    /// Does nothing if a lock is not locked by this instance.
    public synchronized void unlock() {
        if (!isLocked()) {
            return;
        }

        // delete the port file while holding the lock, afterwards it may belong to the next owner
        try {
            if (server != null) {
                server.stop();
                deletePortFile();
            }
        } finally {
            appLock.close();
        }
    }

    private void deletePortFile() {
        try {
            Files.deleteIfExists(portFile);
        } catch (IOException ex) {
            LOG.log(Level.DEBUG, () -> "Unable to delete " + portFile, ex);
        }
    }

    /// Check if locker is busy.
    ///
    /// @return true if locked, false otherwise
    public boolean isLocked() {
        return appLock.isLocked();
    }

    /// Send a message to AppLocker instance that's holding the lock (including self).
    ///
    /// @param message message, at most 1 MiB in UTF-8
    /// @return the answer from the lock holder's [MessageHandler]
    /// @throws LockingException if there's a trouble communicating to other AppLocker instance
    public String sendMessage(final String message) {
        return readClient().send(message);
    }

    private static void writePortFile(final Path portFilePath, final int port, final byte[] token) throws IOException {
        final Path tmp = portFilePath.resolveSibling(portFilePath.getFileName() + ".tmp");
        Files.deleteIfExists(tmp);
        // the token protects the message server from other users, so only the owner may read it
        if (tmp.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.createFile(tmp, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        }
        Files.write(
                tmp,
                ByteBuffer.allocate(PORT_FILE_BYTES).putInt(port).put(token).array());
        Files.move(tmp, portFilePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private Client readClient() {
        LOG.log(Level.DEBUG, "Reading port file {0}", portFile);
        final byte[] bytes;
        try {
            bytes = Files.readAllBytes(portFile);
        } catch (NoSuchFileException ex) {
            throw new LockingException("Unable to open port file, please check that message server is running", ex);
        } catch (IOException ex) {
            throw new LockingException("Unable to read port file", ex);
        }
        if (bytes.length != PORT_FILE_BYTES) {
            throw new LockingException(format("Port file '%s' is corrupted", portFile));
        }
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        final int port = buffer.getInt();
        if (port <= 0 || port > MAX_PORT) {
            throw new LockingException(format("Port file '%s' contains invalid port %d", portFile, port));
        }
        final byte[] token = new byte[Protocol.TOKEN_BYTES];
        buffer.get(token);
        return new Client(port, token, messageTimeoutMs);
    }

    /// AppLocker builder.
    ///
    /// @author Alexander Biryukov
    public static final class Builder {
        private final String id;
        private Path path = Paths.get("");
        private LockIdEncoder encoder = new Sha1Encoder();
        private @Nullable MessageHandler messageHandler;
        private Duration messageTimeout = Duration.ofSeconds(30);

        private Builder(final String lockId) {
            id = lockId;
        }

        /// Sets the path where the lock file will be stored.
        ///
        /// Default value is ""
        ///
        /// @param storePath storing path
        /// @return builder
        public Builder setPath(final Path storePath) {
            path = storePath;
            return this;
        }

        /// Sets the message handler.
        ///
        /// If not set, AppLocker won't support communication features.
        ///
        /// Default value is null.
        ///
        /// @param handler message handler
        /// @return builder
        public Builder setMessageHandler(final MessageHandler handler) {
            messageHandler = handler;
            return this;
        }

        /// Sets how long [AppLocker#sendMessage(String)] waits for the answer.
        ///
        /// Default value is 30 seconds.
        ///
        /// @param timeout positive timeout
        /// @return builder
        /// @throws IllegalArgumentException if the timeout is not positive
        public Builder setMessageTimeout(final Duration timeout) {
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("Message timeout must be positive: " + timeout);
            }
            messageTimeout = timeout;
            return this;
        }

        /// Sets the name encoder.
        ///
        /// Encodes lock lockId to filesystem-friendly entry.
        ///
        /// Default value is "SHA-1" encoder.
        ///
        /// @param idEncoder name encoder
        /// @return builder
        public Builder setIdEncoder(final LockIdEncoder idEncoder) {
            encoder = idEncoder;
            return this;
        }

        /// Build AppLocker.
        ///
        /// @return AppLocker instance
        public AppLocker build() {
            final Server server = messageHandler != null ? new Server(messageHandler) : null;

            return new AppLocker(id, path, encoder, server, messageTimeout);
        }
    }
}
