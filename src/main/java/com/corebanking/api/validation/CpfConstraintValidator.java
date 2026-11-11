package com.corebanking.api.validation;

import com.corebanking.domain.Cpf;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CpfConstraintValidator implements ConstraintValidator<CPF, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null/vazio: responsabilidade de @NotBlank
        return value == null || value.isBlank() || Cpf.isValid(value);
    }
}
