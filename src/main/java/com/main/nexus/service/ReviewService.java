package com.main.nexus.service;

import com.main.nexus.model.Match;
import com.main.nexus.model.Review;
import com.main.nexus.model.enums.AuthorType;
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
    private ReputationService reputationService;

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

        // Dispara o recálculo de reputação de quem foi avaliado
        if (review.getAuthorType() == AuthorType.COMPANY) {
            // Empresa avaliou o profissional → recalcula o profissional
            reputationService.recalculateForProfessional(match.getProfessional().getId());
        } else {
            // Profissional avaliou a empresa → recalcula a empresa
            reputationService.recalculateForCompany(match.getProject().getCompany().getId());
        }

        return saved;
    }
}