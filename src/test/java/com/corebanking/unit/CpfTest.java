package com.corebanking.unit;

import com.corebanking.domain.Cpf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CpfTest {

    @ParameterizedTest
    @ValueSource(strings = {"52998224725", "529.982.247-25", "11144477735", "111.444.777-35"})
    void acceptsValidCpf(String cpf) {
        assertThat(Cpf.isValid(cpf)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"52998224724", "11144477736", "123", "abcdefghijk", "529.982.247.25", "5299822472511"})
    void rejectsInvalidCpf(String cpf) {
        assertThat(Cpf.isValid(cpf)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"00000000000", "11111111111", "22222222222", "99999999999", "111.111.111-11"})
    void rejectsRepeatedSequences(String cpf) {
        assertThat(Cpf.isValid(cpf)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsNullAndEmpty(String cpf) {
        assertThat(Cpf.isValid(cpf)).isFalse();
    }

    @Test
    void normalizesAndMasks() {
        assertThat(Cpf.normalize("529.982.247-25")).isEqualTo("52998224725");
        assertThat(Cpf.mask("52998224725")).isEqualTo("***.***.247-**").doesNotContain("529");
        assertThat(Cpf.mask(null)).isEqualTo("***");
    }
}
