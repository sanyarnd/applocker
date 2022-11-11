package io.github.sanyarnd.applocker;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AppLockerHandlersTest {
    @Test
    void busy_handler_suppress_exception() throws InterruptedException {
        final AppLocker l1 = AppLocker.create("sameId").onBusy("asd", (ans) -> {
        }).setMessageHandler(e -> e).build();
        final AppLocker l2 = AppLocker.create("sameId").onBusy("asd", (ans) -> {
        }).setMessageHandler(e -> e).build();

        l1.lock();
        l2.lock();
        Assertions.assertTrue(l1.isLocked());
        Assertions.assertFalse(l2.isLocked());

        // cleanup
        l1.unlock();
        l2.unlock();
    }

    @Test
    void busy_handler_runnable_suppress_exception() throws InterruptedException {
        final AppLocker l1 = AppLocker.create("sameId").onBusy("asd", () -> {
        }).setMessageHandler(e -> e).build();
        final AppLocker l2 = AppLocker.create("sameId").onBusy("asd", () -> {
        }).setMessageHandler(e -> e).build();

        l1.lock();
        l2.lock();
        Assertions.assertTrue(l1.isLocked());
        Assertions.assertFalse(l2.isLocked());

        // cleanup
        l1.unlock();
        l2.unlock();
    }

    @Test
    void fail_handler_suppress_exception() throws InterruptedException {
        Integer[] ret = {0};
        final AppLocker l1 = AppLocker.create("sameId").onFail((ex) -> {
        }).build();
        final AppLocker l2 = AppLocker.create("sameId").onFail((ex) -> ret[0] = -1).build();

        l1.lock();
        l2.lock();
        Assertions.assertTrue(l1.isLocked());
        Assertions.assertFalse(l2.isLocked());
        // test that failed handler is called
        Assertions.assertEquals(-1, ret[0]);

        // cleanup
        l1.unlock();
        l2.unlock();
    }

    @Test
    void busy_handler_supersedes_fail_handler_when_suppressing_exception() throws InterruptedException {
        Integer[] ret = {0};
        final AppLocker l1 = AppLocker.create("sameId").onFail((ex) -> {
        }).onBusy("asd", (ans) -> {
        }).setMessageHandler(e -> e).build();
        final AppLocker l2 = AppLocker.create("sameId").onFail((ex) -> ret[0] = -1).onBusy("asd", (ans) -> ret[0] = 1)
            .setMessageHandler(e -> e).build();

        l1.lock();
        l2.lock();
        Assertions.assertTrue(l1.isLocked());
        Assertions.assertFalse(l2.isLocked());

        // test that busy handler is called
        Assertions.assertEquals(1, ret[0]);

        // cleanup
        l1.unlock();
        l2.unlock();
    }

    @Test
    void fail_handler_supersedes_fail_handler_when_busy_throws_exception() throws InterruptedException {
        Integer[] ret = {0};
        final AppLocker l1 = AppLocker.create("sameId").onFail((ex) -> {
        }).onBusy("asd", (ans) -> {
        }).setMessageHandler(e -> {
            throw new IllegalArgumentException();
        }).build();
        final AppLocker l2 = AppLocker.create("sameId").onFail((ex) -> ret[0] = -1).onBusy("asd", (ans) -> ret[0] = 1)
            .setMessageHandler(e -> e).build();

        l1.lock();
        l2.lock();
        Assertions.assertTrue(l1.isLocked());
        Assertions.assertFalse(l2.isLocked());
        // test that failed handler is called
        Assertions.assertEquals(-1, ret[0]);

        // cleanup
        l1.unlock();
        l2.unlock();
    }

    @Test
    void lock_acquired_is_called() throws InterruptedException {
        Integer[] ret = {0};
        final AppLocker l1 = AppLocker.create("sameId").onSuccess(() -> ret[0] = 1).build();

        l1.lock();
        Assertions.assertTrue(l1.isLocked());
        // test that failed handler is called
        Assertions.assertEquals(1, ret[0]);

        // cleanup
        l1.unlock();
    }

}
