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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;


public class LockTest {
    @Test
    public void lock_is_autoclosable() {
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
    public void lock_looplock_doesnt_throw() {
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
