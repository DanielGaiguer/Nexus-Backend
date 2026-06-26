
package com.main.nexus.dto;

public record RegisterProfessionalRequestDTO(
        String email,
        String password,
        String name,
        String phone,
        String cep,
        Double minimumSalary,
        Double maximumSalary
) {}