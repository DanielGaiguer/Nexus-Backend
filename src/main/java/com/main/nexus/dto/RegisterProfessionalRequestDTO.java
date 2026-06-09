
package com.main.nexus.dto;

public record RegisterProfessionalRequestDTO(
        String email,
        String password,
        String name,
        String phone,
        String city,
        Double minimumSalary,
        Double maximumSalary
) {}