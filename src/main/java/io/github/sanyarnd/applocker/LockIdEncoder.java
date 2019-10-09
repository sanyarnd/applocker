package io.github.sanyarnd.applocker;

import org.jetbrains.annotations.NotNull;

/**
 * Provides the safe way to encode application id such that it can be stored on filesystem without exceptions: invalid
 * characters, too long etc.
 *
 * @author Alexander Biryukov
 */
public interface LockIdEncoder {
    /**
     * Encode string.
     *
     * @param string input string
     * @return encoded string
     */
    @NotNull
    String encode(@NotNull String string);
}
