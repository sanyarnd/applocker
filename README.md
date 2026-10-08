# AppLocker
[![Build](https://github.com/sanyarnd/applocker/actions/workflows/build.yml/badge.svg)](https://github.com/sanyarnd/applocker/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.sanyarnd/app-locker)](https://central.sonatype.com/artifact/io.github.sanyarnd/app-locker)
[![Javadoc](https://javadoc.io/badge2/io.github.sanyarnd/app-locker/javadoc.svg)](https://javadoc.io/doc/io.github.sanyarnd/app-locker)

AppLocker is a small library which provides the often missing single instance functionality.

# Features
* Safe: based on file channel locking, lock will be released even in case of power outage
* An arbitrary `AppLocker` has the ability to communicate with the `AppLocker` which currently owns the lock
* Lightweight (~20kb) 
* No transitive dependencies
* JDK 11+ support
* Logging via `System.Logger` (Java Platform Logging), route it to any backend you like

# Quick Start
The usage flow typically looks like this:
* Acquire an instance of `AppLocker` class
* Invoke `AppLocker#lock`
* Handle possible errors

To get the `AppLocker` you must invoke static `AppLocker#create` method. 

This call will return `AppLocker.Builder` instance with a set of `#set` and `#on` methods.

All methods are optional and have sane defaults.

`#set` methods allow congiguring interactions with filesystem and how `AppLocker` will handle incoming messages in case it was able to successfully acquire the lock.

```java
AppLocker locker = AppLocker.create("lockID")
    .setPath(Paths.get(""))                // where to store locks (default: "")
    .setIdEncoder(this::encode)             // map `lockID` to filesystem name (default: "SHA-1")
    .setMessageHandler(msg -> process(msg)) // handle messages (default: NULL) 
```

`#on` methods allow handling errors that may occur during the `AppLocker#lock` call.

```java
AppLocker locker = AppLocker.create("lockID")
    .onSuccess(this::logLocking)       // success callback (default: NULL)
    .onBusy(message, this::logAndExit) // send message to the instance which currently owns the lock and invoke callback (default: NULL)
    .onFail(this::logErrorAndExit)     // serious error happened during the lock (default: re-throw exception)
```

Invoke `#build` method to finish the building procedure and retrieve `AppLocker`:
```java
AppLocker locker = AppLocker.create("lockID").build();
```

If you don't want to use the `#on` methods, you can handle exceptions directly:
```java
AppLocker locker = AppLocker.create("lockID").build();
try {
    locker.lock();
} catch (LockingBusyException ex) {
} catch (LockingException ex) {
}
```

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

Artifacts are published to [Maven Central](https://central.sonatype.com/artifact/io.github.sanyarnd/app-locker),
[GitHub Packages](https://github.com/sanyarnd/applocker/packages) and attached to
[GitHub releases](https://github.com/sanyarnd/applocker/releases).

# Building
JDK 21+ is required to build the project (the library itself targets Java 11):
```shell
./gradlew build            # compile, run Error Prone/NullAway, Spotless checks, tests and coverage
./gradlew spotlessApply    # reformat sources (palantir-java-format)
```

# Releasing
Push a tag like `2.0.0` and the [Release](.github/workflows/release.yml) workflow publishes
the artifacts to Maven Central, GitHub Packages and creates a GitHub release.

Required repository secrets:

| Secret                   | Description                                                                     |
|--------------------------|---------------------------------------------------------------------------------|
| `MAVEN_CENTRAL_USERNAME` | Central Portal user token name (https://central.sonatype.com/account)           |
| `MAVEN_CENTRAL_PASSWORD` | Central Portal user token password                                              |
| `GPG_PRIVATE_KEY`        | ASCII-armored GPG private key (`gpg --armor --export-secret-keys <id>`)         |
| `GPG_KEY_PASSPHRASE`     | Passphrase of the GPG key                                                       |

# Changelog
See [CHANGELOG.md](CHANGELOG.md).
