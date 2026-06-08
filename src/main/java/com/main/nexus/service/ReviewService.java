package com.main.nexus.service;

import com.main.nexus.model.Match;
import com.main.nexus.model.Review;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ReviewService {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ProfessionalService professionalService;

    @Autowired
    private CompanyService companyService;

    public Review save(Review review) {
        Match match = review.getMatch();

        // Só permite avaliação se o match foi confirmado
        if (match.getStatus() != StatusMatch.MATCHED) {
            throw new RuntimeException("Reviews are only allowed after a confirmed match");
        }

        Review saved = reviewRepository.save(review);

        // Recalcula reputação após salvar
        recalculateReputation(review);

        return saved;
    }

    private void recalculateReputation(Review review) {
        Match match = review.getMatch();

        switch (review.getAuthor()) {
            case COMPANY -> {
                // Empresa avaliou profissional — atualiza reputação do profissional
                Long professionalId = match.getProfessional().getId();
                Double avg = reviewRepository.findAverageRatingByProfessionalId(professionalId);
                if (avg != null) {
                    professionalService.updateReputation(professionalId, avg);
                }
            }
            case PROFESSIONAL -> {
                // Profissional avaliou empresa — atualiza reputação da empresa
                Long companyId = match.getProject().getCompany().getId();
                Double avg = reviewRepository.findAverageRatingByCompanyId(companyId);
                if (avg != null) {
                    companyService.updateReputation(companyId, avg);
                }
            }
        }
    }
}