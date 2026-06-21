package com.main.nexus.service;

import com.main.nexus.model.Match;
import com.main.nexus.model.Review;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewService {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ProfessionalService professionalService;

    @Autowired
    private CompanyService companyService;

    // ReviewService.java
    public Review save(Review review) {
        Match match = review.getMatch();

        if (match.getStatus() != StatusMatch.MATCHED) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Reviews are only allowed after a confirmed match.");
        }

        if (reviewRepository.existsByMatchIdAndAuthorType(match.getId(), review.getAuthorType())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "A review from this author type already exists for this match.");
        }

        Review saved = reviewRepository.save(review);
        recalculateReputation(review);
        return saved;
    }

    private void recalculateReputation(Review review) {
        Match match = review.getMatch();

        switch (review.getAuthorType()) {
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