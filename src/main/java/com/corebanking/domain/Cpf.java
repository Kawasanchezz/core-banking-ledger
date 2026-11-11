package com.corebanking.domain;

import java.util.regex.Pattern;

/** Validação e normalização de CPF (dígitos verificadores, rejeita sequências repetidas). */
public final class Cpf {

    private static final Pattern FORMAT = Pattern.compile("\\d{11}|\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");

    private Cpf() {
    }

    public static boolean isValid(String value) {
        if (value == null || !FORMAT.matcher(value).matches()) {
            return false;
        }
        String digits = normalize(value);
        if (digits.chars().distinct().count() == 1) {
            return false;
        }
        int d1 = checkDigit(digits, 9);
        int d2 = checkDigit(digits, 10);
        return d1 == digits.charAt(9) - '0' && d2 == digits.charAt(10) - '0';
    }

    /** Remove a máscara, mantendo apenas os dígitos. */
    public static String normalize(String value) {
        return value == null ? null : value.replaceAll("\\D", "");
    }

    /** Máscara para exibição e logs: ***.***.789-** */
    public static String mask(String digits) {
        if (digits == null || digits.length() != 11) {
            return "***";
        }
        return "***.***." + digits.substring(6, 9) + "-**";
    }

    private static int checkDigit(String digits, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (digits.charAt(i) - '0') * (length + 1 - i);
        }
        int mod = (sum * 10) % 11;
        return mod == 10 ? 0 : mod;
    }
}
