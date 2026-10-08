# AppLocker
[![Build](https://github.com/sanyarnd/applocker/actions/workflows/build.yml/badge.svg)](https://github.com/sanyarnd/applocker/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.sanyarnd/app-locker)](https://central.sonatype.com/artifact/io.github.sanyarnd/app-locker)
[![Javadoc](https://javadoc.io/badge2/io.github.sanyarnd/app-locker/javadoc.svg)](https://javadoc.io/doc/io.github.sanyarnd/app-locker)

AppLocker is a small library which provides the often missing single instance functionality.

# Features
* Safe: based on file channel locking, lock will be released even in case of power outage
* An arbitrary `AppLocker` can send a string message to the `AppLocker` which currently owns the lock
* Lightweight
* No transitive dependencies
* JDK 11+ support
* Logging via `System.Logger`

# Quick Start
```java
AppLocker locker = AppLocker.create("my-app")
        .setMessageHandler(file -> open(file))  // optional: answer messages from other instances
        .build();

if (!locker.tryLock()) {
    // another instance is running: pass it the file and exit
    locker.sendMessage(file);
    System.exit(0);
}
```

`AppLocker.Builder` options, all optional:
* `setPath(Path)`: directory for the lock files (default: working directory)
* `setIdEncoder(LockIdEncoder)`: maps the id to a file name (default: SHA-1)
* `setMessageHandler(MessageHandler)`: enables messaging (default: none)
* `setMessageTimeout(Duration)`: how long `sendMessage` waits for the answer (default: 30 seconds)

I/O errors are reported as `LockingException`.

More details can be found in [JavaDocs](https://javadoc.io/doc/io.github.sanyarnd/app-locker).

# Download
Maven:
```xml
<dependency>
    <groupId>io.github.sanyarnd</groupId>
    <artifactId>app-locker</artifactId>
    <version>1.2.0</version>
</dependency>
```

Gradle:
```kotlin
implementation("io.github.sanyarnd:app-locker:1.2.0")
```

Jars are also available in [GitHub Packages](https://github.com/sanyarnd/applocker/packages)
and on the [releases](https://github.com/sanyarnd/applocker/releases) page.

# Building
Requires JDK 25:
```shell
./gradlew build
./gradlew spotlessApply
```

# Releasing
Pushing a tag like `2.0.0` publishes to Maven Central and GitHub Packages, creates a GitHub release
and updates the [site](https://sanyarnd.github.io/applocker/).

Secrets: `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD` ([Central Portal token](https://central.sonatype.com/account)),
`GPG_PRIVATE_KEY`, `GPG_KEY_PASSPHRASE`.

# Changelog
See [CHANGELOG.md](CHANGELOG.md).
