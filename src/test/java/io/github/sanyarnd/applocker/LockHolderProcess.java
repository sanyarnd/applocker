package io.github.sanyarnd.applocker;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;

/// Holds the lock in a separate JVM until stdin is closed.
final class LockHolderProcess {
    static final String READY = "LOCKED";

    private LockHolderProcess() {}

    public static void main(final String[] args) throws IOException {
        final AppLocker locker = AppLocker.create(args[1])
                .setPath(Paths.get(args[0]))
                .setMessageHandler((MessageHandler<String, String>) message -> "pong:" + message)
                .build();
        locker.lock();

        System.out.println(READY);
        System.out.flush();

        System.in.transferTo(OutputStream.nullOutputStream());
        locker.unlock();
    }
}
