# AppLocker
[![Build Status](https://travis-ci.com/sanyarnd/applocker.svg?branch=master)](https://travis-ci.com/sanyarnd/applocker)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=io.github.sanyarnd%3Aapp-locker&metric=coverage)](https://sonarcloud.io/dashboard?id=io.github.sanyarnd%3Aapp-locker)
[![Download](https://api.bintray.com/packages/sanya-rnd/maven-projects/applocker/images/download.svg)](https://bintray.com/sanya-rnd/maven-projects/applocker/_latestVersion)
[![FOSSA Status](https://app.fossa.com/api/projects/custom%2B8815%2Fgithub.com%2Fsanyarnd%2Fapplocker.svg?type=shield)](https://app.fossa.com/projects/custom%2B8815%2Fgithub.com%2Fsanyarnd%2Fapplocker?ref=badge_shield)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

AppLocker library adds single instance application functionality: do not allow more than 1 running instance of the application.

Library is compatible with any JDK8+ version.

Library does not have any transitive dependencies.

# Features
* File-system based locking mechanism
* IPC: optional communication between already running application and any process which is trying to acquire the same lock

# Download

Visit [releases page](https://github.com/sanyarnd/applocker/releases) (jar) or [bintray artifactory](https://bintray.com/sanya-rnd/maven-projects/applocker) (maven, gradle, ivy).

# How to use
```java
enum LockMessage {
    CLOSE, TO_FRONT
}

void logAndExit(@Nonnull LockingException ex) {
    log.error("An error occurred during locking", ex);
    closeApplication();
}

LockMessage closeApp(LockMessage message) {
    log.info("Application is already running");
    log.info("Other application answer: " + message);
    // in this example we don't really care for an answer, close the app anyway
    closeApplication();
}

void logLockAcquiring() {
    log.info("AppLock is acquired");
}

public void main() {
    final AppLocker locker = AppLocker.create("Unique Lock ID") // identify your application
        .setPath(Paths.get("path/to/store/locks"))              // we can decide where to store locks (".", ".cache" etc)
        .busy(LockMessage.TO_FRONT, this::closeApp)             // handles situations, like: failed to lock because `Unique Lock ID` is already taken
        .setMessageHandler((LockMessage msg) -> {               // because we have a busy handler, 
            if (msg == LockMessage.TO_FRONT) {                  // message handler is processing messages sent from `.busy` and sends an answer back
                Ui.run(...);
            }
            return LockMessage.CLOSE;                           // return answer
        })
        .failed(this::logAndExit)                               // handles situations, like: provided path for lock storage is non-writable by user, also handles `busy error` if busy handler is missing
        .acquired(this::logLockAcquiring)                       // simple callback if acquiring the lock was successful
        .build();                                               // create AppLocker instance with provided parameters

    // try lock
    locker.lock();
}
```
