package com.main.nexus.service;

import com.main.nexus.model.Match;
import com.main.nexus.model.MatchStatusCheck;
import com.main.nexus.model.Review;
import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.model.enums.MatchOutcome;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.MatchStatusCheckRepository;
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
    private MatchStatusCheckRepository matchStatusCheckRepository;

    @Autowired
    private ReputationService reputationService;

    @Autowired
    private NotificationService notificationService;

    public Review save(Review review) {
        Match match = review.getMatch();

        boolean matchExpired = Boolean.FALSE.equals(match.getActive());
        boolean matchConfirmed = match.getStatus() == StatusMatch.MATCHED;

        if (!matchExpired && !matchConfirmed) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Reviews are only allowed after a confirmed match.");
        }

        if (reviewRepository.existsByMatchIdAndAuthorType(match.getId(), review.getAuthorType())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "A review from this author type already exists for this match.");
        }

        if (review.getAuthorType() == AuthorType.COMPANY
                && !matchStatusCheckRepository.existsByMatchId(match.getId())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Please answer the match status check before reviewing.");
        }

        MatchStatusCheck statusCheck = matchStatusCheckRepository.findByMatchId(match.getId()).orElse(null);
        if (statusCheck != null && statusCheck.getOutcome() == MatchOutcome.NO_CONTACT_YET) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Reviews are not available when there was no contact.");
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