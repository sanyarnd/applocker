package io.github.sanyarnd.applocker;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LockTest {
    @Test
    void lock_is_autoclosable() {
        Path file = Paths.get("").toAbsolutePath().resolve("testFile");
        try (Lock lock = new Lock(file)) {
            lock.tryLock();
        }
        // can lock the same file since it's been auto-unlocked
        try (Lock lock = new Lock(file)) {
            lock.tryLock();
        }
    }

    @Test
    void lock_looplock_doesnt_throw() throws InterruptedException, ExecutionException {
        final Path file = Paths.get("").toAbsolutePath().resolve("testFile");

        Lock lock = new Lock(file);
        lock.tryLock();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> {
            try (Lock lock1 = new Lock(file)) {
                lock1.lock(300);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        // sleep and let other thread to spin a bit
        Thread.sleep(100);

        lock.close();
        executor.shutdown();
    }

    @Test
    void lock_throws_if_timeout_exceeded() {
        final Path file = Paths.get("").toAbsolutePath().resolve("testFile");

        Lock lock = new Lock(file);
        lock.tryLock();
        Lock lock2 = new Lock(file);

        Assertions.assertThrows(LockingException.class, () -> lock2.lock(50));

        lock.close();
        lock2.close();
    }
}
