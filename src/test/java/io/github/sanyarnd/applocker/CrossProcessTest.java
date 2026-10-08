package io.github.sanyarnd.applocker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/// Verifies locking and messaging between two real JVM processes.
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class CrossProcessTest {
    private static final String ID = "cross-process";

    @TempDir
    Path tempDir;

    private Process holder;
    private AppLocker locker;

    @BeforeEach
    void startHolder() throws Exception {
        final String java =
                Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        final String classpath =
                String.join(File.pathSeparator, location(AppLocker.class), location(LockHolderProcess.class));
        holder = new ProcessBuilder(java, "-cp", classpath, LockHolderProcess.class.getName(), tempDir.toString(), ID)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();

        final BufferedReader reader =
                new BufferedReader(new InputStreamReader(holder.getInputStream(), StandardCharsets.UTF_8));
        final String line = CompletableFuture.supplyAsync(() -> {
                    try {
                        return reader.readLine();
                    } catch (IOException ex) {
                        throw new IllegalStateException(ex);
                    }
                })
                .get(30, TimeUnit.SECONDS);
        assertThat(line).isEqualTo(LockHolderProcess.READY);

        locker = AppLocker.create(ID).setPath(tempDir).build();
    }

    @AfterEach
    void stopHolder() throws InterruptedException {
        holder.destroyForcibly().waitFor();
        locker.unlock();
    }

    private static String location(final Class<?> type) throws URISyntaxException {
        return Paths.get(
                        type.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toString();
    }

    @Test
    void lockIsBusyWhileOtherProcessHoldsIt() {
        assertThatThrownBy(locker::lock).isInstanceOf(LockingBusyException.class);
        assertThat(locker.isLocked()).isFalse();
    }

    @Test
    void sendsMessageToOtherProcess() {
        final String answer = locker.sendMessage("ping");

        assertThat(answer).isEqualTo("pong:ping");
    }

    @Test
    void lockIsAvailableAfterOtherProcessUnlocks() throws Exception {
        holder.getOutputStream().close();
        assertThat(holder.waitFor(30, TimeUnit.SECONDS)).isTrue();

        locker.lock();

        assertThat(locker.isLocked()).isTrue();
    }

    @Test
    void lockIsAvailableAfterOtherProcessIsKilled() throws Exception {
        holder.destroyForcibly();
        assertThat(holder.waitFor(30, TimeUnit.SECONDS)).isTrue();

        // Windows releases locks of a terminated process asynchronously
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (true) {
            try {
                locker.lock();
                break;
            } catch (LockingBusyException ex) {
                assertThat(System.nanoTime() - deadline)
                        .as("lock is still busy")
                        .isNegative();
                Thread.sleep(50);
            }
        }

        assertThat(locker.isLocked()).isTrue();
    }
}
