# 2.0.0 (unreleased)
- Java 11+
- No runtime dependencies: `slf4j-api` replaced with `System.Logger`, JetBrains annotations with JSpecify
- Message server listens on loopback only
- Socket timeouts for the message client and server
- `AppLocker#unlock` by a non-owner no longer deletes the owner's port file
- Failed message server start releases the lock
- A broken client connection no longer stops the message server
- Lock files are no longer deleted on unlock, deleting them allowed two owners at once
- `Lock#tryLock` no longer leaks file channels when called twice
- `Lock#unlock` no longer throws `AssertionError`

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
