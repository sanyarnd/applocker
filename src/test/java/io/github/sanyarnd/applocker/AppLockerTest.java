package io.github.sanyarnd.applocker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.instancio.junit.Given;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@ExtendWith(InstancioExtension.class)
class AppLockerTest {
    private static final String ID = "test-app";

    @TempDir
    Path tempDir;

    private final List<AppLocker> lockers = new ArrayList<>();

    @AfterEach
    void unlockAll() {
        for (AppLocker locker : lockers) {
            locker.unlock();
        }
    }

    private AppLocker.Builder builder(final String id) {
        return AppLocker.create(id).setPath(tempDir);
    }

    private AppLocker register(final AppLocker locker) {
        lockers.add(locker);
        return locker;
    }

    private AppLocker locker(final String id) {
        return register(builder(id).build());
    }

    private AppLocker echoLocker(final String id) {
        return register(builder(id).setMessageHandler(m -> m).build());
    }

    private byte[] token() throws IOException {
        final byte[] bytes = Files.readAllBytes(portFile(ID));
        return Arrays.copyOfRange(bytes, Integer.BYTES, bytes.length);
    }

    private Path portFile(final String id) {
        return tempDir.resolve("." + new Sha1Encoder().encode(id) + "_port.lock");
    }

    @Test
    void newLockerIsNotLocked() {
        assertThat(locker(ID).isLocked()).isFalse();
    }

    @Test
    void tryLockAcquiresLock() {
        final AppLocker locker = locker(ID);

        assertThat(locker.tryLock()).isTrue();
        assertThat(locker.isLocked()).isTrue();
    }

    @Test
    void tryLockIsReentrant() {
        final AppLocker locker = locker(ID);

        assertThat(locker.tryLock()).isTrue();
        assertThat(locker.tryLock()).isTrue();
    }

    @Test
    void unlockReleasesLock() {
        final AppLocker locker = locker(ID);
        locker.tryLock();

        locker.unlock();

        assertThat(locker.isLocked()).isFalse();
    }

    @Test
    void closeReleasesLock() {
        final AppLocker locker = locker(ID);
        try (AppLocker l = locker) {
            l.tryLock();
        }

        assertThat(locker.isLocked()).isFalse();
    }

    @Test
    void unlockWithoutLockDoesNothing() {
        assertThatCode(locker(ID)::unlock).doesNotThrowAnyException();
    }

    @Test
    void canLockAndUnlockManyTimes() {
        final AppLocker locker = locker(ID);

        for (int i = 0; i < 5; ++i) {
            locker.tryLock();
            assertThat(locker.isLocked()).isTrue();
            locker.unlock();
            assertThat(locker.isLocked()).isFalse();
        }
    }

    @Test
    void secondLockerWithSameIdIsBusy() {
        final AppLocker first = locker(ID);
        final AppLocker second = locker(ID);
        first.tryLock();

        assertThat(second.tryLock()).isFalse();
        assertThat(first.isLocked()).isTrue();
        assertThat(second.isLocked()).isFalse();
    }

    @Test
    void lockCanBeTakenOverAfterUnlock() {
        final AppLocker first = locker(ID);
        final AppLocker second = locker(ID);
        first.tryLock();
        first.unlock();

        second.tryLock();

        assertThat(second.isLocked()).isTrue();
    }

    @RepeatedTest(10)
    void lockersWithDifferentIdsAreIndependent(@Given final String firstId, @Given final String secondId) {
        final AppLocker first = locker(firstId);
        final AppLocker second = locker(secondId + "-other");

        first.tryLock();
        second.tryLock();

        assertThat(first.isLocked()).isTrue();
        assertThat(second.isLocked()).isTrue();
    }

    @Test
    void lockersInDifferentDirectoriesAreIndependent() throws IOException {
        final AppLocker first = locker(ID);
        final AppLocker second = register(AppLocker.create(ID)
                .setPath(Files.createDirectory(tempDir.resolve("other")))
                .build());

        first.tryLock();
        second.tryLock();

        assertThat(first.isLocked()).isTrue();
        assertThat(second.isLocked()).isTrue();
    }

    @Test
    void usesCustomIdEncoder() {
        final LockIdEncoder encoder = mock(LockIdEncoder.class);
        when(encoder.encode(anyString()))
                .thenAnswer(inv -> "custom-" + inv.getArgument(0, String.class).length());
        final AppLocker locker = register(builder(ID).setIdEncoder(encoder).build());

        locker.tryLock();

        verify(encoder).encode(ID);
        assertThat(tempDir.resolve(".custom-" + ID.length() + ".lock")).exists();
    }

    @Test
    void failsIfLockDirectoryCannotBeCreated() throws IOException {
        final Path file = Files.createFile(tempDir.resolve("not-a-directory"));
        final AppLocker locker = register(AppLocker.create(ID).setPath(file).build());

        assertThatThrownBy(locker::tryLock).isExactlyInstanceOf(LockingException.class);
        assertThat(locker.isLocked()).isFalse();
    }

    @Test
    void createsLockDirectory() {
        final Path dir = tempDir.resolve("nested").resolve("dir");
        final AppLocker locker = register(AppLocker.create(ID).setPath(dir).build());

        locker.tryLock();

        assertThat(dir).isDirectory();
    }

    @Test
    void sendsMessageToSelf() {
        final AppLocker locker = echoLocker(ID);
        locker.tryLock();

        final String answer = locker.sendMessage("self");

        assertThat(answer).isEqualTo("self");
    }

    @RepeatedTest(10)
    void sendsMessageToLockOwner(@Given final String message) {
        final AppLocker owner = echoLocker(ID);
        final AppLocker other = locker(ID);
        owner.tryLock();

        final String answer = other.sendMessage(message);

        assertThat(answer).isEqualTo(message);
    }

    @Test
    void sendMessageFailsIfOwnerDoesNotAnswerInTime() {
        final CountDownLatch release = new CountDownLatch(1);
        register(builder(ID)
                        .setMessageHandler(m -> {
                            awaitQuietly(release);
                            return m;
                        })
                        .build())
                .tryLock();
        final AppLocker sender =
                register(builder(ID).setMessageTimeout(Duration.ofMillis(100)).build());

        try {
            assertThatThrownBy(() -> sender.sendMessage("hello"))
                    .isExactlyInstanceOf(LockingException.class)
                    .hasMessageContaining("did not answer in time");
        } finally {
            release.countDown();
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveMessageTimeout(final long millis) {
        final AppLocker.Builder builder = builder(ID);

        assertThatThrownBy(() -> builder.setMessageTimeout(Duration.ofMillis(millis)))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsHugeMessageTimeout() {
        echoLocker(ID).tryLock();
        final AppLocker sender =
                register(builder(ID).setMessageTimeout(Duration.ofDays(36_500)).build());

        assertThat(sender.sendMessage("hello")).isEqualTo("hello");
    }

    private static void awaitQuietly(final CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void sendMessageFailsWithoutOwner() {
        final AppLocker locker = echoLocker(ID);

        assertThatThrownBy(() -> locker.sendMessage("self"))
                .isExactlyInstanceOf(LockingException.class)
                .hasMessageContaining("port file");
    }

    @Test
    void sendMessageFailsIfOwnerHasNoMessageHandler() {
        final AppLocker owner = locker(ID);
        owner.tryLock();

        assertThatThrownBy(() -> locker(ID).sendMessage("hello")).isExactlyInstanceOf(LockingException.class);
    }

    @Test
    void sendMessageFailsOnCorruptedPortFile() throws IOException {
        Files.write(portFile(ID), new byte[] {1, 2});

        assertThatThrownBy(() -> locker(ID).sendMessage("hello"))
                .isExactlyInstanceOf(LockingException.class)
                .hasMessageContaining("corrupted");
    }

    @Test
    void sendMessageFailsOnInvalidPort() throws IOException {
        Files.write(portFile(ID), new byte[Integer.BYTES + Protocol.TOKEN_BYTES]);

        assertThatThrownBy(() -> locker(ID).sendMessage("hello"))
                .isExactlyInstanceOf(LockingException.class)
                .hasMessageContaining("invalid port");
    }

    @Test
    void portFileIsReadableByOwnerOnly() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        echoLocker(ID).tryLock();

        assertThat(Files.getPosixFilePermissions(portFile(ID)))
                .containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    }

    @Test
    void newTokenIsGeneratedOnEveryLock() throws IOException {
        final AppLocker locker = echoLocker(ID);
        locker.tryLock();
        final byte[] first = token();
        locker.unlock();

        locker.tryLock();

        assertThat(token()).isNotEqualTo(first);
    }

    @Test
    void unlockRemovesPortFile() {
        final AppLocker locker = echoLocker(ID);
        locker.tryLock();
        assertThat(portFile(ID)).exists();

        locker.unlock();

        assertThat(portFile(ID)).doesNotExist();
    }

    @Test
    void unlockByNonOwnerDoesNotBreakOwner() {
        final AppLocker owner = echoLocker(ID);
        final AppLocker other = register(builder(ID).setMessageHandler(m -> m).build());
        owner.tryLock();
        other.tryLock();

        other.unlock();

        assertThat(owner.isLocked()).isTrue();
        assertThat(portFile(ID)).exists();
        assertThat(other.sendMessage("still there")).isEqualTo("still there");
    }

    @Test
    void messagesAreRoutedToNewOwnerAfterTakeover() {
        final AppLocker first =
                register(builder(ID).setMessageHandler(m -> "first").build());
        final AppLocker second =
                register(builder(ID).setMessageHandler(m -> "second").build());

        first.tryLock();
        assertThat(second.sendMessage("who?")).isEqualTo("first");

        first.unlock();
        second.tryLock();
        assertThat(first.sendMessage("who?")).isEqualTo("second");
    }

    @Test
    void failedServerStartReleasesLock() throws IOException {
        // the port file cannot replace a non-empty directory
        Files.createFile(Files.createDirectory(portFile(ID)).resolve("blocker"));
        final AppLocker locker = echoLocker(ID);

        assertThatThrownBy(locker::tryLock)
                .isExactlyInstanceOf(LockingException.class)
                .hasMessageContaining("message server");
        assertThat(locker.isLocked()).isFalse();

        final AppLocker plain = locker(ID);
        plain.tryLock();
        assertThat(plain.isLocked()).isTrue();
    }

    @Test
    void toStringContainsId() {
        assertThat(locker(ID).toString()).startsWith("AppLocker{lockId='" + ID + "'");
    }
}
