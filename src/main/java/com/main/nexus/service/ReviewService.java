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
    
    @Autowired
    private NotificationService notificationService;

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
        
        if (review.getAuthorType() == AuthorType.COMPANY) {
            // Empresa avaliou o profissional — notifica o profissional
            notificationService.notifyNewReviewReceived(
                review.getMatch().getProfessional().getUser(),
                review.getMatch().getProject().getCompany().getCompanyName(),
                review.getRating()
            );
        } else {
            // Profissional avaliou a empresa — notifica a empresa
            notificationService.notifyNewReviewReceived(
                review.getMatch().getProject().getCompany().getUser(),
                review.getMatch().getProfessional().getName(),
                review.getRating()
            );
        }

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