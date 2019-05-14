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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Alexander Biryukov
 */
public class AppLockerHandlersTest {

    @Test
    public void busy_handler_suppress_exception() {
        final AppLocker l1 = AppLocker.create("sameId").busy("asd", (ans) -> {}).setMessageHandler(e -> e).build();
        final AppLocker l2 = AppLocker.create("sameId").busy("asd", (ans) -> {}).setMessageHandler(e -> e).build();

        l1.lock();
        l2.lock();
        Assertions.assertTrue(l1.isLocked());
        Assertions.assertFalse(l2.isLocked());

        // cleanup
        l1.unlock();
        l2.unlock();
    }

    @Test
    public void fail_handler_suppress_exception() {
        Integer[] ret = new Integer[]{0};
        final AppLocker l1 = AppLocker.create("sameId").failed((ex) -> {}).build();
        final AppLocker l2 = AppLocker.create("sameId").failed((ex) -> ret[0] = -1).build();

        l1.lock();
        l2.lock();
        Assertions.assertTrue(l1.isLocked());
        Assertions.assertFalse(l2.isLocked());
        // test that failed handler is called
        Assertions.assertEquals(ret[0], -1);

        // cleanup
        l1.unlock();
        l2.unlock();
    }

    @Test
    public void busy_handler_supersedes_fail_handler_when_suppressing_exception() {
        Integer[] ret = new Integer[]{0};
        final AppLocker l1 = AppLocker.create("sameId").failed((ex) -> {}).busy("asd", (ans) -> {}).setMessageHandler(e -> e).build();
        final AppLocker l2 = AppLocker.create("sameId").failed((ex) -> ret[0] = -1).busy("asd", (ans) -> ret[0] = 1).setMessageHandler(e -> e).build();

        l1.lock();
        l2.lock();
        Assertions.assertTrue(l1.isLocked());
        Assertions.assertFalse(l2.isLocked());

        // test that busy handler is called
        Assertions.assertEquals(ret[0], 1);

        // cleanup
        l1.unlock();
        l2.unlock();
    }


    @Test
    public void fail_handler_supersedes_fail_handler_when_busy_throws_exception() {
        Integer[] ret = new Integer[]{0};
        final AppLocker l1 = AppLocker.create("sameId").failed((ex) -> {}).busy("asd", (ans) -> {}).setMessageHandler(e -> {
            throw new IllegalArgumentException();
        }).build();
        final AppLocker l2 = AppLocker.create("sameId").failed((ex) -> ret[0] = -1).busy("asd", (ans) -> ret[0] = 1).setMessageHandler(e -> e).build();

        l1.lock();
        l2.lock();
        Assertions.assertTrue(l1.isLocked());
        Assertions.assertFalse(l2.isLocked());
        // test that failed handler is called
        Assertions.assertEquals(ret[0], -1);

        // cleanup
        l1.unlock();
        l2.unlock();
    }

    @Test
    public void lock_acquired_is_called() {
        Integer[] ret = new Integer[]{0};
        final AppLocker l1 = AppLocker.create("sameId").acquired(() -> ret[0] = 1).build();

        l1.lock();
        Assertions.assertTrue(l1.isLocked());
        // test that failed handler is called
        Assertions.assertEquals(ret[0], 1);

        // cleanup
        l1.unlock();
    }

}
