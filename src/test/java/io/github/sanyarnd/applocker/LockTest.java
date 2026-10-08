package io.github.sanyarnd.applocker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
            lock.tryLock();

            assertThat(lock.isLocked()).isTrue();
            assertThat(lockFile()).exists();
        }
    }

    @Test
    void tryLockIsIdempotent() {
        try (Lock lock = new Lock(lockFile())) {
            lock.tryLock();
            lock.tryLock();

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

            assertThatThrownBy(second::tryLock).isInstanceOf(LockingBusyException.class);
            assertThat(first.isLocked()).isTrue();
            assertThat(second.isLocked()).isFalse();
        }
    }

    @Test
    void busyLockCanBeAcquiredAfterOwnerReleasesIt() {
        try (Lock first = new Lock(lockFile());
                Lock second = new Lock(lockFile())) {
            first.tryLock();
            assertThatThrownBy(second::tryLock).isInstanceOf(LockingBusyException.class);

            first.unlock();
            second.tryLock();

            assertThat(second.isLocked()).isTrue();
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
    void lockWithTimeoutAcquiresFreeLock() throws InterruptedException {
        try (Lock lock = new Lock(lockFile())) {
            lock.lock(0);

            assertThat(lock.isLocked()).isTrue();
        }
    }

    @Test
    void lockWithTimeoutFailsIfLockIsNotReleased() {
        try (Lock owner = new Lock(lockFile());
                Lock waiter = new Lock(lockFile())) {
            owner.tryLock();

            final long start = System.nanoTime();
            assertThatThrownBy(() -> waiter.lock(100))
                    .isExactlyInstanceOf(LockingException.class)
                    .hasMessageContaining("timeout=100ms")
                    .hasCauseInstanceOf(LockingBusyException.class);
            assertThat(System.nanoTime() - start).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(100));
            assertThat(waiter.isLocked()).isFalse();
        }
    }

    @Test
    void lockWithTimeoutWaitsUntilLockIsReleased() throws Exception {
        final CountDownLatch started = new CountDownLatch(1);
        try (Lock owner = new Lock(lockFile());
                Lock waiter = new Lock(lockFile())) {
            owner.tryLock();

            final CompletableFuture<Boolean> acquired = CompletableFuture.supplyAsync(() -> {
                started.countDown();
                try {
                    waiter.lock(10_000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return waiter.isLocked();
            });

            started.await();
            Thread.sleep(100);
            assertThat(acquired).isNotDone();

            owner.unlock();

            assertThat(acquired.get(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void lockWithTimeoutIsInterruptible() throws Exception {
        try (Lock owner = new Lock(lockFile());
                Lock waiter = new Lock(lockFile())) {
            owner.tryLock();

            final CompletableFuture<Throwable> result = new CompletableFuture<>();
            final Thread thread = new Thread(() -> {
                try {
                    waiter.lock(60_000);
                    result.complete(new AssertionError("lock must not be acquired"));
                } catch (Throwable ex) {
                    result.complete(ex);
                }
            });
            thread.start();
            Thread.sleep(50);
            thread.interrupt();

            assertThat(result.get(10, TimeUnit.SECONDS)).isInstanceOf(InterruptedException.class);
        }
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
