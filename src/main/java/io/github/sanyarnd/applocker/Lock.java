package io.github.sanyarnd.applocker;

import static java.lang.String.format;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/// File-channel based lock.
///
/// The lock file is not deleted on [#unlock()]: otherwise another process could lock a new file with the same
/// name while the deleted one is still locked.
///
/// @author Alexander Biryukov
public final class Lock implements AutoCloseable {
    private static final Logger LOG = System.getLogger(Lock.class.getName());
    private static final long LOCK_SLEEP_MS = 10;

    private final Path file;
    private @Nullable FileChannel channel;
    private @Nullable FileLock fileLock;

    /// Create a lock.
    ///
    /// @param f lock file
    public Lock(final Path f) {
        file = f.toAbsolutePath();
    }

    @Override
    public void close() {
        unlock();
    }

    /// Tries to acquire the lock and ignores any [LockingBusyException] during the process.
    ///
    /// Be aware that it's easy to get a spin lock if the other Lock won't call [#close()].
    ///
    /// @param timeoutMs timeout in milliseconds
    /// @throws LockingException lock exceeded timeout
    /// @throws InterruptedException if the thread was interrupted while waiting for the lock
    public synchronized void lock(final long timeoutMs) throws InterruptedException {
        final long start = System.nanoTime();
        final long timeoutNs = TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (true) {
            try {
                tryLock();
                return;
            } catch (LockingBusyException ex) {
                if (System.nanoTime() - start >= timeoutNs) {
                    throw new LockingException(format("Lock attempt timeout=%dms exceeded", timeoutMs), ex);
                }
                Thread.sleep(LOCK_SLEEP_MS);
            }
        }
    }

    /// Unlock the lock.
    ///
    /// Does nothing if the lock is not locked.
    public synchronized void unlock() {
        final FileChannel ch = channel;
        channel = null;
        fileLock = null;
        if (ch == null) {
            return;
        }

        LOG.log(Level.DEBUG, "Unlocking {0}", file);
        // closing the channel releases the file lock as well
        closeQuietly(ch);
    }

    /// Attempt to lock the file.
    ///
    /// Does nothing if the lock is already held by this instance.
    ///
    /// @throws LockingException if any error occurred during the locking process (I/O exception)
    /// @throws LockingBusyException if a lock is already taken by someone
    public synchronized void tryLock() {
        if (isLocked()) {
            return;
        }

        LOG.log(Level.DEBUG, "Locking {0}", file);
        createParentDirs();

        final FileChannel ch;
        try {
            ch = FileChannel.open(file, CREATE, READ, WRITE);
        } catch (IOException ex) {
            throw new LockingException(format("Unable to open lock file '%s'", file), ex);
        }

        final FileLock lock;
        try {
            lock = ch.tryLock();
        } catch (OverlappingFileLockException ex) {
            closeQuietly(ch);
            throw new LockingBusyException("Lock is already held by this JVM", ex);
        } catch (IOException ex) {
            closeQuietly(ch);
            throw new LockingException(format("Unable to lock file '%s'", file), ex);
        }

        if (lock == null) {
            closeQuietly(ch);
            throw new LockingBusyException("Lock is already held by another process", null);
        }

        channel = ch;
        fileLock = lock;
    }

    private void createParentDirs() {
        final Path parent = file.getParent();
        if (parent == null || Files.isDirectory(parent)) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException ex) {
            throw new LockingException(format("Unable to create parent directory '%s' for lock", parent), ex);
        }
    }

    private void closeQuietly(final FileChannel ch) {
        try {
            ch.close();
        } catch (IOException ex) {
            LOG.log(Level.WARNING, () -> "Unable to close lock file " + file, ex);
        }
    }

    /// Check whether lock is currently in use.
    ///
    /// @return true if locked, false otherwise
    public synchronized boolean isLocked() {
        return channel != null && fileLock != null && channel.isOpen() && fileLock.isValid();
    }

    @Override
    public String toString() {
        return format("Lock{file=%s, locked=%s}", file, isLocked());
    }
}
