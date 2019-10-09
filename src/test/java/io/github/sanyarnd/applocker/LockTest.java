package io.github.sanyarnd.applocker;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class LockTest {
    @Test
    void lock_is_autoclosable() {
        Path file = Paths.get(".").toAbsolutePath().resolve("testFile");
        try (Lock lock = new Lock(file)) {
            lock.lock();
        }
        // can lock the same file since it's been auto-unlocked
        try (Lock lock = new Lock(file)) {
            lock.lock();
        }
    }

    @Test
    void lock_looplock_doesnt_throw() {
        final Path file = Paths.get(".").toAbsolutePath().resolve("testFile");

        Lock lock = new Lock(file);
        lock.lock();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(new Runnable() {
            @Override
            public void run() {
                try (Lock lock = new Lock(file)) {
                    lock.loopLock();
                }
            }
        });
        // sleep and let other thread to spin a bit
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
        }

        lock.unlock();
    }
}
