package com.corebanking.unit;

import com.corebanking.domain.Money;
import com.corebanking.domain.exception.BusinessException;
import com.corebanking.domain.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void normalizesToTwoDecimals() {
        assertThat(Money.positive(new BigDecimal("10"))).isEqualTo(new BigDecimal("10.00"));
        assertThat(Money.positive(new BigDecimal("10.500"))).isEqualTo(new BigDecimal("10.50"));
    }

    @Test
    void rejectsZeroNegativeNullAndTooManyDecimals() {
        for (BigDecimal invalid : new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("-1"), null, new BigDecimal("0.001")}) {
            assertThatThrownBy(() -> Money.positive(invalid))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.INVALID_AMOUNT));
        }
    }

    @Test
    void bigDecimalAvoidsFloatingPointErrors() {
        assertThat(new BigDecimal("0.10").add(new BigDecimal("0.20"))).isEqualByComparingTo("0.30");
    }

    @Test
    void nonNegativeAcceptsZeroAndNull() {
        assertThat(Money.nonNegative(null)).isEqualTo(Money.ZERO);
        assertThat(Money.nonNegative(BigDecimal.ZERO)).isEqualTo(Money.ZERO);
        assertThat(Money.nonNegative(new BigDecimal("500"))).isEqualTo(new BigDecimal("500.00"));
    }
}
