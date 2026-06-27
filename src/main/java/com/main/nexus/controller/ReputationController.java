
package com.main.nexus.controller;

import com.main.nexus.dto.ReputationExplanationDTO;
import com.main.nexus.model.ReputationMetrics;
import com.main.nexus.repository.ReputationMetricsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reputation")
public class ReputationController {

    @Autowired
    private ReputationMetricsRepository reputationMetricsRepository;

    @GetMapping("/professional/{id}")
    public ResponseEntity<ReputationExplanationDTO> getProfessionalReputation(@PathVariable Long id) {
        ReputationMetrics m = reputationMetricsRepository.findByProfessionalId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "No reputation data yet."));
        return ResponseEntity.ok(toDTO(m));
    }

    @GetMapping("/company/{id}")
    public ResponseEntity<ReputationExplanationDTO> getCompanyReputation(@PathVariable Long id) {
        ReputationMetrics m = reputationMetricsRepository.findByCompanyId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404), "No reputation data yet."));
        return ResponseEntity.ok(toDTO(m));
    }

    private ReputationExplanationDTO toDTO(ReputationMetrics m) {
        return new ReputationExplanationDTO(
                m.getReputationScore(),
                m.getConfidenceScore() != null ? m.getConfidenceScore() * 100 : 0.0,
                m.getTotalReviews(),
                m.getTechnicalCompetence(),
                m.getCommunication(),
                m.getReliability(),
                m.getPunctuality(),
                m.getProfessionalism(),
                m.getSatisfactionAverage(),
                m.getRecommendationRate()
        );
    }
}