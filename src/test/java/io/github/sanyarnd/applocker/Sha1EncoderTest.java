package io.github.sanyarnd.applocker;

import static org.assertj.core.api.Assertions.assertThat;

import org.instancio.junit.Given;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ExtendWith(InstancioExtension.class)
class Sha1EncoderTest {
    private final Sha1Encoder encoder = new Sha1Encoder();

    @ParameterizedTest
    @CsvSource({
        "'', da39a3ee5e6b4b0d3255bfef95601890afd80709",
        "abc, a9993e364706816aba3e25717850c26c9cd0d89d",
        "The quick brown fox jumps over the lazy dog, 2fd4e1c67a2d28fced849ee1bb76e7391b93eb12",
        "привет, e24505f94db2b5df4c7c2596b0788e720e073021"
    })
    void encodesKnownVectors(final String input, final String expected) {
        assertThat(encoder.encode(input)).isEqualTo(expected);
    }

    @RepeatedTest(10)
    void producesFilesystemFriendlyName(@Given final String input) {
        assertThat(encoder.encode(input)).matches("[0-9a-f]{40}");
    }

    @RepeatedTest(10)
    void isDeterministic(@Given final String input) {
        assertThat(encoder.encode(input)).isEqualTo(new Sha1Encoder().encode(input));
    }

    @RepeatedTest(10)
    void differentInputsProduceDifferentNames(@Given final String input) {
        assertThat(encoder.encode(input)).isNotEqualTo(encoder.encode(input + "x"));
    }
}
