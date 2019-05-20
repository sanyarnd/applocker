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
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import javax.annotation.Nullable;

import io.github.sanyarnd.applocker.exceptions.LockingBusyException;
import io.github.sanyarnd.applocker.exceptions.LockingFailedException;

/**
 * File-channel based lock.
 *
 * @author Alexander Biryukov
 */
final class Lock implements AutoCloseable {
    private final Path file;
    @Nullable
    private FileChannel channel;
    @Nullable
    private FileLock fileLock;

    Lock(final Path underlyingFile) {
        file = underlyingFile.toAbsolutePath();
    }

    @Override
    public void close() {
        unlock();
    }

    /**
     * Tries to acquire the lock and ignores any {@link LockingBusyException} during the process.<br>
     * Be aware that it's easy to get a spin lock if the other Lock won't call {@link #unlock()}, that's
     * why loopLock is used only for global lock acquiring.
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
            channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
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

    public String toString() { return "Lock(file=" + file + ", locked=" + isLocked() + ")"; }
}
