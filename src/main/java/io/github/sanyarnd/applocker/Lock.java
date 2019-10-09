package io.github.sanyarnd.applocker;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * File-channel based lock.
 *
 * @author Alexander Biryukov
 */
@Slf4j
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
        log.debug("Trying to lock {} in loop", file);
        while (true) {
            try {
                lock();
                // if lock is succeeded -- return
                log.debug("Successfully locked {} in loop", file);
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
        log.debug("Locking {}", file);
        if (!Files.exists(file.getParent(), LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.createDirectories(file.getParent());
            } catch (IOException ex) {
                log.debug("Failed at creating directory {}", file.getParent());
                throw new LockingFailedException("Unable to create directory for locks", ex);
            }
        }

        try {
            channel = FileChannel.open(file,
                    StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            // we need nested try to close channel in case it's impossible to obtain FileLock
            // and because #close throws IOException
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
            throw new LockingFailedException("Unable to open lock file channel", ex);
        }
    }

    /**
     * Unlock the lock.
     */
    void unlock() {
        log.debug("Unlocking {}", file);
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
        } catch (NoSuchFileException ignored) {
            // ignore if file is not here
        } catch (IOException ex) {
            // something very wrong goes here
            log.error("An error during unlocking {}", file, ex);
            throw new RuntimeException("Should never happen", ex);
        }
    }

    boolean isLocked() {
        return channel != null && fileLock != null && channel.isOpen() && fileLock.isValid();
    }

    public String toString() {
        return "Lock(file=" + file + ", locked=" + isLocked() + ")";
    }
}
