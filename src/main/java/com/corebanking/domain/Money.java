package com.corebanking.domain;

import com.corebanking.domain.exception.BusinessException;
import com.corebanking.domain.exception.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Utilitários de valor monetário. Sempre BigDecimal com 2 casas; nunca double/float. */
public final class Money {

    public static final int SCALE = 2;
    /** Arredondamento explícito (entradas do usuário NUNCA são arredondadas, apenas rejeitadas). */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private Money() {
    }

    /** Valida valor > 0 com no máximo 2 casas decimais e devolve com escala 2. */
    public static BigDecimal positive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT, "O valor deve ser maior que zero");
        }
        if (value.stripTrailingZeros().scale() > SCALE) {
            throw new BusinessException(ErrorCode.INVALID_AMOUNT, "O valor deve ter no máximo 2 casas decimais");
        }
        return value.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    /** Valida valor >= 0 com no máximo 2 casas decimais e devolve com escala 2. */
    public static BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return ZERO;
        }
        return positive(value);
    }
}
