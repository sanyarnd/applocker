package io.github.sanyarnd.applocker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@ExtendWith(MockitoExtension.class)
class AppLockerHandlersTest {
    private static final String ID = "handlers";

    @TempDir
    Path tempDir;

    @Mock
    private Runnable onSuccess;

    @Mock
    private Runnable onBusyRunnable;

    @Mock
    private Consumer<String> onBusyConsumer;

    @Mock
    private Consumer<LockingException> onFail;

    @Mock
    private Runnable onFailRunnable;

    private final List<AppLocker> lockers = new ArrayList<>();

    @AfterEach
    void unlockAll() {
        for (AppLocker locker : lockers) {
            locker.unlock();
        }
    }

    private AppLocker.Builder builder() {
        return AppLocker.create(ID).setPath(tempDir);
    }

    private AppLocker register(final AppLocker.Builder builder) {
        final AppLocker locker = builder.build();
        lockers.add(locker);
        return locker;
    }

    private AppLocker lockedOwner(final MessageHandler<String, String> handler) {
        final AppLocker owner = register(builder().setMessageHandler(handler));
        owner.lock();
        return owner;
    }

    @Test
    void successHandlerIsCalledOnLock() {
        final AppLocker locker = register(builder().onSuccess(onSuccess));

        locker.lock();

        verify(onSuccess).run();
    }

    @Test
    void successHandlerIsNotCalledWhenAlreadyLocked() {
        final AppLocker locker = register(builder().onSuccess(onSuccess));

        locker.lock();
        locker.lock();

        verify(onSuccess).run();
    }

    @Test
    void successHandlerIsNotCalledWhenBusy() {
        lockedOwner(m -> m);
        final AppLocker locker = register(builder().onSuccess(onSuccess).onFail(onFail));

        locker.lock();

        verifyNoInteractions(onSuccess);
    }

    @Test
    void busyHandlerReceivesAnswerOfOwner() {
        lockedOwner(m -> "answer to " + m);
        final AppLocker locker =
                register(builder().onBusy("question", onBusyConsumer).onFail(onFail));

        locker.lock();

        verify(onBusyConsumer).accept("answer to question");
        verifyNoInteractions(onFail);
        assertThat(locker.isLocked()).isFalse();
    }

    @Test
    void busyRunnableIsCalledAfterMessageIsDelivered() {
        final List<String> received = new ArrayList<>();
        lockedOwner(m -> {
            received.add(m);
            return m;
        });
        final AppLocker locker =
                register(builder().onBusy("hello", onBusyRunnable).onFail(onFail));

        locker.lock();

        verify(onBusyRunnable).run();
        verifyNoInteractions(onFail);
        assertThat(received).containsExactly("hello");
    }

    @Test
    void failHandlerReceivesBusyExceptionWithoutBusyHandler() {
        lockedOwner(m -> m);
        final AppLocker locker = register(builder().onFail(onFail));

        locker.lock();

        verify(onFail).accept(any(LockingBusyException.class));
    }

    @Test
    void failHandlerIsCalledIfBusyHandlerCannotReachOwner() {
        // owner without a message handler
        register(builder()).lock();
        final AppLocker locker =
                register(builder().onBusy("hello", onBusyConsumer).onFail(onFail));

        locker.lock();

        verifyNoInteractions(onBusyConsumer);
        final ArgumentCaptor<LockingException> captor = ArgumentCaptor.forClass(LockingException.class);
        verify(onFail).accept(captor.capture());
        assertThat(captor.getValue()).isNotInstanceOf(LockingBusyException.class);
    }

    @Test
    void failHandlerIsCalledIfOwnerFailsToHandleMessage() {
        lockedOwner(m -> {
            throw new IllegalStateException("broken handler");
        });
        final AppLocker locker =
                register(builder().onBusy("hello", onBusyConsumer).onFail(onFail));

        locker.lock();

        verifyNoInteractions(onBusyConsumer);
        verify(onFail).accept(any(LockingException.class));
    }

    @Test
    void failRunnableIsCalledOnFailure() {
        lockedOwner(m -> m);
        final AppLocker locker = register(builder().onFail(onFailRunnable));

        locker.lock();

        verify(onFailRunnable).run();
    }

    @Test
    void defaultFailHandlerRethrows() {
        lockedOwner(m -> m);
        final AppLocker locker = register(builder());

        assertThatThrownBy(locker::lock).isInstanceOf(LockingBusyException.class);
    }

    @Test
    void lastConfiguredHandlerWins() {
        lockedOwner(m -> m);
        final AppLocker locker = register(builder().onFail(onFail).onFail(onFailRunnable));

        locker.lock();

        verify(onFailRunnable).run();
        verify(onFail, never()).accept(any());
    }

    @Test
    void exceptionFromSuccessHandlerIsPassedToFailHandler() {
        final LockingException failure = new LockingException("success handler failed");
        doThrow(failure).when(onSuccess).run();
        final AppLocker locker = register(builder().onSuccess(onSuccess).onFail(onFail));

        assertThatCode(locker::lock).doesNotThrowAnyException();

        verify(onFail).accept(failure);
    }
}
