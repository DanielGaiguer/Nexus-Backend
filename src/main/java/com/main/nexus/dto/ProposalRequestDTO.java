package com.main.nexus.dto;

import java.time.LocalDate;
import java.util.List;

public record ProposalRequestDTO(
        Long projectId,
        Double proposedValue,
        Integer estimatedDays,
        LocalDate proposedStartDate,
        LocalDate proposedDeliveryDate,
        String description,
        String relevantExperience,
        List<Long> skillIds,
        String deliverables,
        List<String> executionSteps,
        String paymentTerms,
        Integer validityDays,
        String questionsForCompany
) {}
