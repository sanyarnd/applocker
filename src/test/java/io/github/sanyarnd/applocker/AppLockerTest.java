package io.github.sanyarnd.applocker;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;

class AppLockerTest {
    private static <T extends Serializable> MessageHandler<T, T> createEchoHandler() {
        return message -> message;
    }

    @Test
    void lock_twice_throws() {
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
    void lock_unlock_the_same_lock() {
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
    void lock_unlock_then_lock_by_other_applock() {
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
    void lock_two_independent_locks() {
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
    void unlock_before_lock_doesnt_throw() {
        final AppLocker l1 = AppLocker.create("sameId").build();
        Assertions.assertDoesNotThrow(l1::unlock);
    }

    @Test
    void communication_to_self() {
        final AppLocker l1 = AppLocker.create("sameId").onBusy("", (ans) -> {}).setMessageHandler(createEchoHandler()).build();

        l1.lock();

        String messageToSelf = l1.sendMessage("self");
        Assertions.assertEquals("self", messageToSelf);

        // cleanup
        l1.unlock();
    }

    @Test
    void communication_between_two_locks() {
        final AppLocker l1 = AppLocker.create("sameId").onBusy("", (ans) -> {}).setMessageHandler(createEchoHandler()).build();
        final AppLocker l2 = AppLocker.create("sameId").build();

        l1.lock();

        String messageToOther = l2.sendMessage("other");
        Assertions.assertEquals("other", messageToOther);

        // cleanup
        l1.unlock();
        l2.unlock();
    }

    @Test
    void communication_doesnt_work_without_lock() {
        final AppLocker l1 = AppLocker.create("sameId").onBusy("", (ans) -> {}).setMessageHandler(createEchoHandler()).build();
        Assertions.assertThrows(LockingCommunicationException.class, () -> l1.sendMessage("self"));

        // cleanup
        l1.unlock();
    }

    @Test
    void communication_after_reacquiring_the_lock() {
        final AppLocker l1 = AppLocker.create("sameId").onBusy("", (ans) -> {}).setMessageHandler((MessageHandler<String, String>) message -> "1").build();
        final AppLocker l2 = AppLocker.create("sameId").onBusy("", (ans) -> {}).setMessageHandler((MessageHandler<String, String>) message -> "2").build();

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
    void custom_name_provider() {
        LockIdEncoder doubleName = string -> string + string;
        final AppLocker l1 = AppLocker.create("sameId").setIdEncoder(doubleName).build();
        Assertions.assertDoesNotThrow(l1::lock);

        // cleanup
        l1.unlock();
    }

    @Test
    void to_string() {
        AppLocker l1 = AppLocker.create("sameId").build();
        Assertions.assertDoesNotThrow(l1::toString);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void invalid_path_windows_throws() {
        Path path = Paths.get("Z:/");
        final AppLocker l1 = AppLocker.create("sameId").setPath(path).build();

        Assertions.assertThrows(LockingFailedException.class, l1::lock);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void invalid_path_2_windows_throws() {
        Path path = Paths.get("Z:/invalid_subpath");
        final AppLocker l1 = AppLocker.create("sameId").setPath(path).build();
        Assertions.assertThrows(LockingFailedException.class, l1::lock);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void invalid_path_linux_throws() {
        Path path = Paths.get("/invalid");
        final AppLocker l1 = AppLocker.create("sameId").setPath(path).build();
        Assertions.assertThrows(LockingFailedException.class, l1::lock);
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    void invalid_path_2_linux_throws() {
        Path path = Paths.get("/invalid/invalid");
        final AppLocker l1 = AppLocker.create("sameId").setPath(path).build();
        Assertions.assertThrows(LockingFailedException.class, l1::lock);
    }
}
