package io.github.sanyarnd.applocker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class LockTest {
    @TempDir
    Path tempDir;

    private Path lockFile() {
        return tempDir.resolve("test.lock");
    }

    @Test
    void newLockIsNotLocked() {
        final Lock lock = new Lock(lockFile());

        assertThat(lock.isLocked()).isFalse();
    }

    @Test
    void tryLockAcquiresLock() {
        try (Lock lock = new Lock(lockFile())) {
            assertThat(lock.tryLock()).isTrue();

            assertThat(lock.isLocked()).isTrue();
            assertThat(lockFile()).exists();
        }
    }

    @Test
    void tryLockIsIdempotent() {
        try (Lock lock = new Lock(lockFile())) {
            assertThat(lock.tryLock()).isTrue();
            assertThat(lock.tryLock()).isTrue();

            assertThat(lock.isLocked()).isTrue();
        }
    }

    @Test
    void unlockReleasesLock() {
        final Lock lock = new Lock(lockFile());
        lock.tryLock();

        lock.unlock();

        assertThat(lock.isLocked()).isFalse();
    }

    @Test
    void unlockKeepsLockFile() {
        final Lock lock = new Lock(lockFile());
        lock.tryLock();

        lock.unlock();

        assertThat(lockFile()).exists();
    }

    @Test
    void unlockWithoutLockDoesNothing() {
        final Lock lock = new Lock(lockFile());

        assertThatCode(lock::unlock).doesNotThrowAnyException();
        assertThat(lockFile()).doesNotExist();
    }

    @Test
    void lockCanBeReacquiredAfterUnlock() {
        try (Lock lock = new Lock(lockFile())) {
            for (int i = 0; i < 3; ++i) {
                lock.tryLock();
                assertThat(lock.isLocked()).isTrue();
                lock.unlock();
                assertThat(lock.isLocked()).isFalse();
            }
        }
    }

    @Test
    void closeReleasesLock() {
        try (Lock lock = new Lock(lockFile())) {
            lock.tryLock();
        }

        try (Lock lock = new Lock(lockFile())) {
            lock.tryLock();
            assertThat(lock.isLocked()).isTrue();
        }
    }

    @Test
    void secondLockOnSameFileIsBusy() {
        try (Lock first = new Lock(lockFile());
                Lock second = new Lock(lockFile())) {
            first.tryLock();

            assertThat(second.tryLock()).isFalse();
            assertThat(first.isLocked()).isTrue();
            assertThat(second.isLocked()).isFalse();
        }
    }

    @Test
    void busyLockCanBeAcquiredAfterOwnerReleasesIt() {
        try (Lock first = new Lock(lockFile());
                Lock second = new Lock(lockFile())) {
            first.tryLock();
            assertThat(second.tryLock()).isFalse();

            first.unlock();

            assertThat(second.tryLock()).isTrue();
        }
    }

    @Test
    void locksOnDifferentFilesAreIndependent() {
        try (Lock first = new Lock(tempDir.resolve("first.lock"));
                Lock second = new Lock(tempDir.resolve("second.lock"))) {
            first.tryLock();
            second.tryLock();

            assertThat(first.isLocked()).isTrue();
            assertThat(second.isLocked()).isTrue();
        }
    }

    @Test
    void createsMissingParentDirectories() {
        final Path file = tempDir.resolve("a").resolve("b").resolve("test.lock");

        try (Lock lock = new Lock(file)) {
            lock.tryLock();

            assertThat(file).exists();
        }
    }

    @Test
    void failsIfParentIsRegularFile() throws IOException {
        final Path parent = Files.createFile(tempDir.resolve("file"));
        final Lock lock = new Lock(parent.resolve("test.lock"));

        assertThatThrownBy(lock::tryLock)
                .isExactlyInstanceOf(LockingException.class)
                .hasMessageContaining("parent directory");
        assertThat(lock.isLocked()).isFalse();
    }

    @Test
    void failsIfLockFileIsDirectory() throws IOException {
        final Path dir = Files.createDirectory(lockFile());
        final Lock lock = new Lock(dir);

        assertThatThrownBy(lock::tryLock).isExactlyInstanceOf(LockingException.class);
        assertThat(lock.isLocked()).isFalse();
    }

    @Test
    void toStringContainsFileAndState() {
        try (Lock lock = new Lock(lockFile())) {
            assertThat(lock).hasToString("Lock{file=" + lockFile().toAbsolutePath() + ", locked=false}");

            lock.tryLock();

            assertThat(lock).hasToString("Lock{file=" + lockFile().toAbsolutePath() + ", locked=true}");
        }
    }
}
