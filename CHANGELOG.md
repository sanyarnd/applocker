# 2.0.0 (unreleased)
- Build migrated from Maven to Gradle, publishing via Central Portal, mirror in GitHub Packages
- Java 11 is now the minimal supported version
- `slf4j-api` dependency removed, logging goes through `System.Logger` (Java Platform Logging)
- `org.jetbrains:annotations` replaced with JSpecify annotations (compile-only, no runtime dependency)
- Static analysis: Error Prone, NullAway, Spotless with palantir-java-format
- Message server listens on the loopback interface only (previously it was bound to all interfaces)
- `AppLocker#unlock` called by a non-owner no longer deletes the owner's port file
- A failure while starting the message server releases the lock instead of leaving it half-acquired
- Port file is written atomically and validated on read
- A single broken client connection no longer stops the message server
- Lock files are kept after unlock to avoid a race where two processes could own the same lock
- `Lock#tryLock` is idempotent and no longer leaks file channels
- `Lock#unlock` no longer throws `AssertionError` on I/O errors

# 1.2.0
- Simplify exception hierarchy
- Add AutoClosable interface
- Use correct initial value for Path
- Add blocking timeout methods
- Remove lombok
- Better function naming

# 1.1.2
- Enable `SO_REUSEADDR` feature for a client
 
# 1.1.1
- Specify nullable annotations directly, 
`package-info.java` annotations are not sufficient

# 1.1
- `JSR305` was replaced with `Checker Framework Annotations`
- Cleanup documentation and all textual resources
- `pom.xml` tweaks

# 1.0.6
- Added missing `<scm>` entry in `pom.xml`

# 1.0.5
- Added `Runnable` overload for `Applocker.Builder#onFail`
- Removed `Spotbugs` and `PMD`
- `pom.xml` tweaks

# 1.0.4
- Methods, parameters and fields annotated as `Nonnull` by default
- `Spotbugs` and `PMD` integration
- `Checkstyle` compliance
- Missing `JavaDoc`'s
- Missing `package-info.java`
- Simplified `pom.xml`

# 1.0.3
- Breaking API name changes
- Improved `JavaDoc`
- Refactoring
- Added `mvnw` executable for `Linux` and `Mac`

# 1.0.2
- Added `Runnable` overload for `Applocker.Builder#busy`
- javadoc and sources are now distributed along with the package 

# 1.0.1
- Removed unnecessary classes
- Improved test coverage

# 1.0
- Initial commit
