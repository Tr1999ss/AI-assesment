package com.shk.generic.ingestion.dedup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Sha256ContentHasherTest {

    @Test
    void matchesKnownSha256Vector() {
        assertThat(Sha256ContentHasher.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void producesLowercaseHexAcceptedByRegistryCheckConstraint() {
        assertThat(Sha256ContentHasher.sha256("Hello")).matches("^[0-9a-f]{64}$");
    }
}
