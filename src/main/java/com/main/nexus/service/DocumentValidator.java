package com.main.nexus.service;

import com.main.nexus.model.enums.CompanyType;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Validacao de documento (CPF ou CNPJ) de um contratante, conforme o seu {@link CompanyType}.
 * Sem dependencia de Spring alem das excecoes HTTP, para permanecer testavel sem contexto.
 */
public final class DocumentValidator {

    private static final int[] CNPJ_WEIGHTS_1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] CNPJ_WEIGHTS_2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] CPF_WEIGHTS_1 = {10, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] CPF_WEIGHTS_2 = {11, 10, 9, 8, 7, 6, 5, 4, 3, 2};

    private DocumentValidator() {
    }

    /** taxId opcional: null/blank nao valida nada (mesmo comportamento de antes da expansao para CPF). */
    public static void validate(String taxId, CompanyType type) {
        if (taxId == null || taxId.isBlank()) return;

        boolean isCpf = type == CompanyType.INDIVIDUAL;
        String label = isCpf ? "CPF" : "CNPJ";
        int expectedLength = isCpf ? 11 : 14;

        String digits = taxId.replaceAll("[^0-9]", "");

        if (digits.length() != expectedLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    label + " has an invalid format. Expected " + expectedLength + " digits.");
        }

        if (digits.chars().distinct().count() == 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    label + " is invalid. Sequences of identical digits are not accepted.");
        }

        boolean valid = isCpf ? isValidCpf(digits) : isValidCnpj(digits);
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    label + " is invalid. Please check the number and try again.");
        }
    }

    public static boolean isValidCpf(String digits) {
        int digit1 = checkDigit(digits, CPF_WEIGHTS_1, 9);
        if (digit1 != Character.getNumericValue(digits.charAt(9))) return false;

        int digit2 = checkDigit(digits, CPF_WEIGHTS_2, 10);
        return digit2 == Character.getNumericValue(digits.charAt(10));
    }

    public static boolean isValidCnpj(String digits) {
        int digit1 = checkDigit(digits, CNPJ_WEIGHTS_1, 12);
        if (digit1 != Character.getNumericValue(digits.charAt(12))) return false;

        int digit2 = checkDigit(digits, CNPJ_WEIGHTS_2, 13);
        return digit2 == Character.getNumericValue(digits.charAt(13));
    }

    private static int checkDigit(String digits, int[] weights, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += Character.getNumericValue(digits.charAt(i)) * weights[i];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
