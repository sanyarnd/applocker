package io.github.sanyarnd.applocker;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static java.lang.String.format;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * File-channel based lock.
 *
 * @author Alexander Biryukov
 */
public final class Lock implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(Lock.class);
    private static final int LOCK_SLEEP_MS = 10;

    private final @NotNull Path file;
    private @Nullable FileChannel channel;
    private @Nullable FileLock fileLock;

    /**
     * Create a lock.
     *
     * @param f lock file
     */
    public Lock(final @NotNull Path f) {
        file = f.toAbsolutePath();
    }

    @Override
    public synchronized void close() {
        unlock();
    }

    /**
     * Tries to acquire the lock and ignores any {@link LockingBusyException} during the process.
     * <br>
     * Be aware that it's easy to get a spin lock if the other Lock won't call {@link #close()}.
     *
     * @param timeoutMs timeout in milliseconds
     * @throws LockingException lock exceeded timeout
     */
    public synchronized void lock(final long timeoutMs) throws InterruptedException {
        final long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - timeoutMs <= start) {
            try {
                tryLock();
                return;
            } catch (LockingBusyException ignored) {
                Thread.sleep(LOCK_SLEEP_MS);
            }
        }
        throw new LockingException(format("Lock attempt timeout=%dms exceeded", timeoutMs));
    }

    /**
     * Unlock the lock.
     */
    public void unlock() {
        LOG.debug("Unlocking {}", file);
        try {
            if (fileLock != null) {
                fileLock.close();
            }
            fileLock = null;

            if (channel != null) {
                channel.close();
            }
            channel = null;

            Files.delete(file);
        } catch (NoSuchFileException ignored) {
            // ignore if file is not here
        } catch (IOException ex) {
            // something very wrong goes here
            LOG.error("An error during unlocking {}", file, ex);
            throw new AssertionError("Should never happen", ex);
        }
    }

    /**
     * Attempt to lock {@link #file}.
     *
     * @throws LockingException     if any error occurred during the locking process (I/O exception)
     * @throws LockingBusyException if a lock is already taken by someone
     */
    public synchronized void tryLock() {
        LOG.debug("Locking {}", file);
        createParentDirs();
        try {
            createChannelLock();
        } catch (IOException ex) {
            throw new LockingException("Unable to open lock file channel", ex);
        }
    }

    private void createParentDirs() {
        if (!Files.exists(file.getParent(), LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectories(file.getParent());
            } catch (IOException ex) {
                throw new LockingException(format("Unable to create parent directory '%s' for lock", file), ex);
            }
        }
    }

    private void createChannelLock() throws IOException {
        channel = FileChannel.open(file, CREATE, READ, WRITE);
        try {
            fileLock = channel.tryLock(); // can throw or return null
            if (fileLock == null) {
                throw new OverlappingFileLockException();
            }
        } catch (OverlappingFileLockException ex) {
            channel.close();
            channel = null;
            throw new LockingBusyException("Unable to acquire file lock", ex);
        }
    }

    /**
     * Check whether lock is currently in use.
     *
     * @return true if locked, false otherwise
     */
    public boolean isLocked() {
        return channel != null && fileLock != null && channel.isOpen() && fileLock.isValid();
    }

    @Override
    public String toString() {
        return format("Lock{file=%s, locked=%s}", file, isLocked());
    }
}
