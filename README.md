# AppLocker
[![Build Status](https://travis-ci.com/sanyarnd/applocker.svg?branch=master)](https://travis-ci.com/sanyarnd/applocker)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=io.github.sanyarnd%3Aapp-locker&metric=coverage)](https://sonarcloud.io/dashboard?id=io.github.sanyarnd%3Aapp-locker)

AppLocker is a small library which provides the often missing single instance functionality.

# Features
* Safe: based on file channel locking, lock will be released even in case of power outage
* An arbitrary `AppLocker` has the ability to communicate with the `AppLocker` which currently owns the lock
* Lightweight (~20kb) 
* No transitive dependencies
* JDK8+ support

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
    .setPath(Paths.get("."))                // where to store locks (default: ".")
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
} catch (LockingCommunicationException ex) {
} catch (LockingMessageServerException ex) {
} catch (LockingFailedException ex) {
}
```

More details can be found in [JavaDocs](https://sanyarnd.github.io/applocker/apidocs/index.html).

# Download
Maven:
```xml
<dependency> 
    <groupId>io.github.sanyarnd</groupId> 
    <artifactId>app-locker</artifactId>
    <version>1.1.1</version>
</dependency>
```

Gradle:
```gradle
compile 'io.github.sanyarnd:app-locker:1.1.1'
```
 
Standalone jars are available on [releases](https://github.com/sanyarnd/applocker/releases) page.

More download options available in [Bintray](https://bintray.com/sanya-rnd/maven-projects/applocker) repository.

# Changelog
See [CHANGELOG.md](CHANGELOG.md).
