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

import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.Nonnull;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.github.sanyarnd.applocker.exceptions.LockingBusyException;
import io.github.sanyarnd.applocker.exceptions.LockingCommunicationException;
import io.github.sanyarnd.applocker.exceptions.LockingFailedException;
import io.github.sanyarnd.applocker.filesystem.LockNameProvider;
import io.github.sanyarnd.applocker.messaging.MessageHandler;

public class AppLockerTest {
    private static <T extends Serializable> MessageHandler<T, T> createEchoHandler() {
        return new MessageHandler<T, T>() {
            @Nonnull
            @Override
            public T handleMessage(@Nonnull T message) {
                return message;
            }
        };
    }

    @Test
    public void lock_twice_throws() {
        final AppLocker l1 = AppLocker.create("sameId").build();
        final AppLocker l2 = AppLocker.create("sameId").build();

        l1.lock();
        Assertions.assertThrows(LockingBusyException.class, l2::lock);

        Assertions.assertTrue(l1.isLocked());
        Assertions.assertFalse(l2.isLocked());

        // cleanup
        l1.unlock();
        l2.unlock();
    }

    @Test
    public void lock_unlock_the_same_lock() {
        final AppLocker l1 = AppLocker.create("sameId").build();

        l1.lock();
        Assertions.assertTrue(l1.isLocked());
        l1.unlock();
        Assertions.assertFalse(l1.isLocked());
        l1.lock();
        Assertions.assertTrue(l1.isLocked());

        // cleanup
        l1.unlock();
    }

    @Test
    public void lock_unlock_then_lock_by_other_applock() {
        final AppLocker l1 = AppLocker.create("sameId").build();
        final AppLocker l2 = AppLocker.create("sameId").build();

        l1.lock();
        Assertions.assertTrue(l1.isLocked());
        l1.unlock();
        Assertions.assertFalse(l1.isLocked());
        l2.lock();
        Assertions.assertTrue(l2.isLocked());

        // cleanup
        l1.unlock();
        l2.unlock();
    }

    @Test
    public void lock_two_independent_locks() {
        final AppLocker l1 = AppLocker.create("idOne").build();
        final AppLocker l2 = AppLocker.create("idTwo").build();

        l1.lock();
        Assertions.assertTrue(l1.isLocked());
        l2.lock();
        Assertions.assertTrue(l2.isLocked());

        // cleanup
        l1.unlock();
        l2.unlock();
    }

    @Test
    public void unlock_before_lock_doesnt_throw() {
        final AppLocker l1 = AppLocker.create("sameId").build();
        l1.unlock();
    }

    @Test
    public void communication_to_self() {
        final AppLocker l1 = AppLocker.create("sameId").busy("", (ans) -> {}).setMessageHandler(createEchoHandler()).build();

        l1.lock();

        String messageToSelf = l1.sendMessage("self");
        Assertions.assertEquals("self", messageToSelf);

        // cleanup
        l1.unlock();
    }

    @Test
    public void communication_between_two_locks() {
        final AppLocker l1 = AppLocker.create("sameId").busy("", (ans) -> {}).setMessageHandler(createEchoHandler()).build();
        final AppLocker l2 = AppLocker.create("sameId").build();

        l1.lock();

        String messageToOther = l2.sendMessage("other");
        Assertions.assertEquals("other", messageToOther);

        // cleanup
        l1.unlock();
        l2.unlock();
    }

    @Test
    public void communication_doesnt_work_without_lock() {
        final AppLocker l1 = AppLocker.create("sameId").busy("", (ans) -> {}).setMessageHandler(createEchoHandler()).build();
        Assertions.assertThrows(LockingCommunicationException.class, () -> l1.sendMessage("self"));

        // cleanup
        l1.unlock();
    }

    @Test
    public void communication_after_reacquiring_the_lock() {
        final AppLocker l1 = AppLocker.create("sameId").busy("", (ans) -> {}).setMessageHandler(new MessageHandler<String, String>() {
            @Nonnull
            @Override
            public String handleMessage(@Nonnull String message) {
                return "1";
            }
        }).build();
        final AppLocker l2 = AppLocker.create("sameId").busy("", (ans) -> {}).setMessageHandler(new MessageHandler<String, String>() {
            @Nonnull
            @Override
            public String handleMessage(@Nonnull String message) {
                return "2";
            }
        }).build();

        l1.lock();
        String messageToOther = l2.sendMessage("whatever");
        Assertions.assertEquals("1", messageToOther);

        l1.unlock();

        l2.lock();
        messageToOther = l1.sendMessage("whatever");
        Assertions.assertEquals("2", messageToOther);

        // cleanup
        l1.unlock();
        l2.unlock();
    }

    @Test
    public void custom_name_provider() {
        LockNameProvider doubleName = new LockNameProvider() {
            @Nonnull
            @Override
            public String encrypt(@Nonnull String string) {
                return string + string;
            }
        };
        final AppLocker l1 = AppLocker.create("sameId").setNameProvider(doubleName).build();
        l1.lock();

        // cleanup
        l1.unlock();
    }

    @Test
    public void to_string() {
        AppLocker l1 = AppLocker.create("sameId").build();
        Assertions.assertNotEquals(l1.toString(), null);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void invalid_path_windows_throws() {
        Path path = Paths.get("Z:/");
        final AppLocker l1 = AppLocker.create("sameId").setPath(path).build();

        Assertions.assertThrows(LockingFailedException.class, l1::lock);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    public void invalid_path_2_windows_throws() {
        Path path = Paths.get("Z:/invalid_subpath");
        final AppLocker l1 = AppLocker.create("sameId").setPath(path).build();
        Assertions.assertThrows(LockingFailedException.class, l1::lock);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    public void invalid_path_linux_throws() {
        Path path = Paths.get("/invalid");
        final AppLocker l1 = AppLocker.create("sameId").setPath(path).build();
        Assertions.assertThrows(LockingFailedException.class, l1::lock);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    public void invalid_path_2_linux_throws() {
        Path path = Paths.get("/invalid/invalid");
        final AppLocker l1 = AppLocker.create("sameId").setPath(path).build();
        Assertions.assertThrows(LockingFailedException.class, l1::lock);
    }
}
