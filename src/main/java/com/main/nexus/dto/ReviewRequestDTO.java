package com.main.nexus.dto;

import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.model.enums.NegativeReason;
import com.main.nexus.model.enums.PositiveReason;
import java.util.List;

public record ReviewRequestDTO(
        Integer rating,
        String comment,
        AuthorType authorType,
        List<PositiveReason> positiveReasons,
        List<NegativeReason> negativeReasons
) {}