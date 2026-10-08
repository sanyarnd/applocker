package io.github.sanyarnd.applocker;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

/**
 * Entry point of a separate JVM used by {@link CrossProcessTest}: acquires the lock, reports it on stdout and holds it
 * until stdin is closed.
 */
final class LockHolderProcess {
    static final String READY = "LOCKED";

    private LockHolderProcess() {}

    public static void main(final String[] args) throws InterruptedException, IOException {
        final AppLocker locker = AppLocker.create(args[1])
                .setPath(Paths.get(args[0]))
                .setMessageHandler((MessageHandler<String, String>) message -> "pong:" + message)
                .build();
        locker.lock();

        final PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        out.println(READY);

        // block until the parent process closes stdin
        while (System.in.read() != -1) {
            // ignore input
        }
        locker.unlock();
    }
}
