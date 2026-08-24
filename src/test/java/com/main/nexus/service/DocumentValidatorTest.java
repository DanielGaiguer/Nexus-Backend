package com.main.nexus.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.main.nexus.model.enums.CompanyType;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class DocumentValidatorTest {

    @Test
    void acceptsValidCnpj() {
        assertTrue(DocumentValidator.isValidCnpj("11222333000181"));
        assertDoesNotThrow(() -> DocumentValidator.validate("11.222.333/0001-81", CompanyType.LEGAL_ENTITY));
    }

    @Test
    void rejectsCnpjWithWrongCheckDigit() {
        assertFalse(DocumentValidator.isValidCnpj("11222333000180"));
        assertThrows(ResponseStatusException.class,
                () -> DocumentValidator.validate("11.222.333/0001-80", CompanyType.LEGAL_ENTITY));
    }

    @Test
    void rejectsCnpjWithRepeatedDigits() {
        assertThrows(ResponseStatusException.class,
                () -> DocumentValidator.validate("11.111.111/1111-11", CompanyType.LEGAL_ENTITY));
    }

    @Test
    void rejectsCnpjWithWrongLength() {
        assertThrows(ResponseStatusException.class,
                () -> DocumentValidator.validate("123456789", CompanyType.LEGAL_ENTITY));
    }

    @Test
    void acceptsValidCpf() {
        assertTrue(DocumentValidator.isValidCpf("52998224725"));
        assertDoesNotThrow(() -> DocumentValidator.validate("529.982.247-25", CompanyType.INDIVIDUAL));
    }

    @Test
    void rejectsCpfWithWrongCheckDigit() {
        assertFalse(DocumentValidator.isValidCpf("52998224726"));
        assertThrows(ResponseStatusException.class,
                () -> DocumentValidator.validate("529.982.247-26", CompanyType.INDIVIDUAL));
    }

    @Test
    void rejectsCpfWithRepeatedDigits() {
        assertThrows(ResponseStatusException.class,
                () -> DocumentValidator.validate("111.111.111-11", CompanyType.INDIVIDUAL));
    }

    @Test
    void rejectsCpfWithWrongLength() {
        assertThrows(ResponseStatusException.class,
                () -> DocumentValidator.validate("123456789012", CompanyType.INDIVIDUAL));
    }

    @Test
    void blankOrNullTaxIdIsAlwaysAccepted() {
        assertDoesNotThrow(() -> DocumentValidator.validate(null, CompanyType.LEGAL_ENTITY));
        assertDoesNotThrow(() -> DocumentValidator.validate("", CompanyType.INDIVIDUAL));
        assertDoesNotThrow(() -> DocumentValidator.validate("   ", CompanyType.LEGAL_ENTITY));
    }

    @Test
    void aCnpjIsNotAcceptedAsACpfEvenIfBothAreNumericallyValid() {
        // 11222333000181 e um CNPJ valido com 14 digitos; forcado como CPF (11 digitos
        // esperados) deve falhar por tamanho, nao por digito verificador.
        assertThrows(ResponseStatusException.class,
                () -> DocumentValidator.validate("11222333000181", CompanyType.INDIVIDUAL));
    }
}
