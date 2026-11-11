package com.corebanking.domain;

import com.corebanking.domain.exception.BusinessException;
import com.corebanking.domain.exception.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private Money() {
    }

    public static BigDecimal positive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT, "O valor deve ser maior que zero");
        }
        if (value.stripTrailingZeros().scale() > SCALE) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT, "O valor deve ter no máximo 2 casas decimais");
        }
        return value.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return ZERO;
        }
        return positive(value);
    }
}
