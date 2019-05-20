# AppLocker
[![Build Status](https://travis-ci.com/sanyarnd/applocker.svg?branch=master)](https://travis-ci.com/sanyarnd/applocker)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=io.github.sanyarnd%3Aapp-locker&metric=coverage)](https://sonarcloud.io/dashboard?id=io.github.sanyarnd%3Aapp-locker)

AppLocker library provides single instance functionality, allowing exactly 1 running instance of your application.

# Features
* Safe: based on file channel locking, lock will be released even in case of power outrage
* Can communicate with instance which's acquired the lock
* Small memory footprint (~20kb) 
* No transitive dependencies
* JDK8+ support

# Usage
Get the `AppLocker`:
```java
AppLocker locker = AppLocker.create("lockID")
    .setPath(Paths.get("."))            // where to store locks (default: ".")
    .setIdEncoder(this::encode)         // how to encode lock files (default: "SHA-1")
    .setMessageHandler(e -> e)          // handle messages which are sent with locker#send or #onBusy (default: NULL)
    .build();                           // create instance 
```

There are two ways how to handle errors.
 
I encourage you to try smart (fluent) builder:
compact, easy to read and provides expecting functionality right off the bat:
```java
AppLocker locker = AppLocker.create("lockID")
    .onSuccess(this::logLocking)        // success callback (default: NULL)
    .onBusy("close", this::logAndExit)  // send message to the instance which currently owns the lock and invoke callback (default: NULL)
    .onFail(this::logErrorAndExit)      // serious error happened during the lock (default: re-throw exception)
    .build();
locker.lock();
}
```

If it's not suited for you for some reason, use exceptions mechanism:
```java
AppLocker locker = AppLocker.create("lockID").build();
try {
    locker.lock();
} catch (LockingBusyException ex) {
} catch (LockingCommunicationException ex) {
} catch (LockingMessageServerException ex) {
} catch (LockingFailedException ex) {
}
```

JavaDocs available [here](https://sanyarnd.github.io/applocker/apidocs/index.html).

# Download
Maven:
```xml
<dependency> 
    <groupId>io.github.sanyarnd</groupId> 
    <artifactId>app-locker</artifactId>
    <version>1.0.4</version>
</dependency>

<repositories>
    <repository>
        <id>bintray-sanya-rnd-maven-projects</id>
        <name>bintray</name>
        <url>https://dl.bintray.com/sanya-rnd/maven-projects</url>
    </repository>
</repositories>
```
Gradle:
```gradle
compile 'io.github.sanyarnd:app-locker:1.0.4'

repositories { 
    maven { 
        url "https://dl.bintray.com/sanya-rnd/maven-projects" 
    } 
}
```
For more information visit [bintray](https://bintray.com/sanya-rnd/maven-projects/applocker).
 
Standalone jars are available on [releases](https://github.com/sanyarnd/applocker/releases) page.


# Changelog
To see what has changed in recent versions of AppLocker, see the [CHANGELOG.md](CHANGELOG.md)
