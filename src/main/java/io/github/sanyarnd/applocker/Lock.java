package io.github.sanyarnd.applocker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * File-channel based lock.
 *
 * @author Alexander Biryukov
 */
final class Lock implements AutoCloseable {
    private final @NotNull Path file;
    private @Nullable FileChannel channel;
    private @Nullable FileLock fileLock;

    Lock(final @NotNull Path underlyingFile) {
        file = underlyingFile.toAbsolutePath();
    }

    @Override
    public void close() {
        unlock();
    }

    /**
     * Tries to acquire the lock and ignores any {@link LockingBusyException} during the process.<br> Be aware that it's
     * easy to get a spin lock if the other Lock won't call {@link #unlock()}, that's why loopLock is used only for
     * global lock acquiring.
     *
     * @throws LockingFailedException if any error occurred during the locking process (I/O exception)
     */
    void loopLock() {
        while (true) {
            try {
                lock();
                // if lock is succeeded -- return
                return;
            } catch (LockingBusyException ignored) {
            }
            // we dont catch LockingFailedException, propagate it up to the stack
        }
    }

    /**
     * Attempt to lock {@link #file}.
     *
     * @throws LockingFailedException if any error occurred during the locking process (I/O exception)
     * @throws LockingBusyException   if lock is already taken by someone
     */
    void lock() {
        if (!Files.exists(file.getParent(), LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectories(file.getParent());
            } catch (IOException ex) {
                throw new LockingFailedException(ex);
            }
        }

        try {
            channel = FileChannel.open(file,
                    StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            // we need to close channel in case we are unable to obtain FileLock
            try {
                fileLock = channel.tryLock();
                if (fileLock == null) {
                    throw new OverlappingFileLockException();
                }
            } catch (OverlappingFileLockException ex) {
                channel.close();
                channel = null;
                throw new LockingBusyException(ex);
            }
        } catch (IOException ex) {
            throw new LockingFailedException(ex);
        }
    }

    /**
     * Unlock the lock.
     */
    void unlock() {
        // these calls cannot fail fail under normal circumstances
        // can't really imagine the case when it'll be left in half-valid state
        try {
            if (fileLock != null) {
                fileLock.release();
            }
            fileLock = null;

            if (channel != null) {
                channel.close();
            }
            channel = null;

            Files.delete(file);
        } catch (IOException ignored) {
        }
    }

    boolean isLocked() {
        return channel != null && fileLock != null && channel.isOpen() && fileLock.isValid();
    }

    public String toString() {
        return "Lock(file=" + file + ", locked=" + isLocked() + ")";
    }
}
