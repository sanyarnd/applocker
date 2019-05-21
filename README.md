# AppLocker
[![Build Status](https://travis-ci.com/sanyarnd/applocker.svg?branch=master)](https://travis-ci.com/sanyarnd/applocker)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=io.github.sanyarnd%3Aapp-locker&metric=coverage)](https://sonarcloud.io/dashboard?id=io.github.sanyarnd%3Aapp-locker)

AppLocker is a small library, which provides the often missing single instance functionality.

# Features
* Safe: based on file channel locking, lock will be released even in case of power outrage
* An arbitrary `AppLocker` has the ability to communicate with the `AppLocker` which currently owns the lock
* Small memory size (~20kb) 
* No transitive dependencies
* JDK8+ support

# Quick Start
The usage flow typically look like this:
* Acquire an instance of `AppLocker` class
* Invoke `AppLocker#lock`
* Handle possible errors

To get the `AppLocker` you must invoke static `AppLocker#create` method. 

This call will return `AppLocker.Builder` instance with the set of `#set` and `#on` methods.

All methods are optional and have sane defaults.

`#set` methods are related to the filesystem and how `AppLocker` will proceed with incoming messages if it'd be able to successfully acquire the lock.

```java
AppLocker locker = AppLocker.create("lockID")
    .setPath(Paths.get("."))                // where to store locks (default: ".")
    .setIdEncoder(this::encode)             // map `lockID` to filesystem name (default: "SHA-1")
    .setMessageHandler(msg -> process(msg)) // handle messages (default: NULL) 
```

`#on` methods are related how you might handle the possible errors, which occur during the `AppLocker#lock` call.

```java
AppLocker locker = AppLocker.create("lockID")
    .onSuccess(this::logLocking)       // success callback (default: NULL)
    .onBusy(message, this::logAndExit) // send message to the instance which currently owns the lock and invoke callback (default: NULL)
    .onFail(this::logErrorAndExit)     // serious error happened during the lock (default: re-throw exception)
```

To finish the building procedure and retrieve `AppLocker` invoke `#build` method:
```java
AppLocker locker = AppLocker.create("lockID").build();
```

If you don't want to use `#on` methods, you can handle exceptions directly:
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
    <version>1.0.6</version>
</dependency>

<!--Artifact will be published on Maven Central soon-->
<repositories>
    <repository>
        <id>bintray</id>
        <name>Bintray Dependency Repository</name>
        <url>http://jcenter.bintray.com</url>
    </repository>
</repositories>
```

Gradle:
```gradle
compile 'io.github.sanyarnd:app-locker:1.0.6'

// Artifact will be published on Maven Central soon
repositories {  
   jcenter()  
}
```
 
Standalone jars are available on [releases](https://github.com/sanyarnd/applocker/releases) page.

More download options available in [Bintray](https://bintray.com/sanya-rnd/maven-projects/applocker) repository.

# Changelog
See [CHANGELOG.md](CHANGELOG.md).

