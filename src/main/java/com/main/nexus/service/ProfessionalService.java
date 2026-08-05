/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.main.nexus.service;

import com.main.nexus.dto.ProfessionalStatsDTO;
import com.main.nexus.model.Professional;
import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.ProfessionalRepository;
import com.main.nexus.repository.RejectionFeedbackRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfessionalService {
    @Autowired
    private ProfessionalRepository professionalRepository;
    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private RejectionFeedbackRepository rejectionFeedbackRepository;
    
    public Professional save(Professional professional){
        return professionalRepository.save(professional);
    }
    
    public List<Professional> findAll() {
        return professionalRepository.findAll();
    }

    public Professional update(Professional professional) {
        return professionalRepository.save(professional);
    }

    public void delete(Long id) {
        professionalRepository.deleteById(id);
    }

    public Professional findById(Long professionalId) {
        return professionalRepository.findById(professionalId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Professional not found: " + professionalId));
    }

    public Optional<Professional> findByUserId(Long userId) {
        return professionalRepository.findByUserId(userId);
    }

    public ProfessionalStatsDTO getStats(Long professionalId) {
        int totalMatchesReceived = (int) matchRepository.countByProfessionalId(professionalId);
        int totalAccepted = (int) matchRepository.countByProfessionalIdAndStatus(professionalId, StatusMatch.MATCHED);
        int totalRejectedByMe = (int) rejectionFeedbackRepository.countByMatchProfessionalIdAndRejectedBy(
                professionalId, AuthorType.PROFESSIONAL);
        double acceptanceRate = totalMatchesReceived > 0
                ? (totalAccepted * 100.0 / totalMatchesReceived) : 0.0;
        Double avg = matchRepository.findAverageScoreByProfessionalId(professionalId);
        double avgScore = avg != null ? avg : 0.0;

        String rankingPosition;
        if (avgScore >= 85) {
            rankingPosition = "Top 15%";
        } else if (avgScore >= 70) {
            rankingPosition = "Top 35%";
        } else if (avgScore >= 55) {
            rankingPosition = "Top 60%";
        } else {
            rankingPosition = "Abaixo da média";
        }

        return new ProfessionalStatsDTO(
                totalMatchesReceived, totalAccepted, totalRejectedByMe,
                Math.round(acceptanceRate * 10.0) / 10.0,
                Math.round(avgScore * 10.0) / 10.0,
                rankingPosition
        );
    }
}
