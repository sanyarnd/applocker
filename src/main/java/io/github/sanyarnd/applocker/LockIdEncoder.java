package io.github.sanyarnd.applocker;

/// Provides a safe way to encode application id such that it can be stored on filesystem without exceptions: invalid
/// characters, too long etc.
///
/// @author Alexander Biryukov
@FunctionalInterface
public interface LockIdEncoder {
    /// Encode string.
    ///
    /// @param inputString input string
    /// @return encoded string
    String encode(String inputString);
}
