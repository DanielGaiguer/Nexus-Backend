package com.main.nexus.service;

import com.main.nexus.dto.PendingReviewDTO;
import com.main.nexus.model.Match;
import com.main.nexus.model.MatchStatusCheck;
import com.main.nexus.model.Review;
import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.model.enums.MatchOutcome;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.MatchStatusCheckRepository;
import com.main.nexus.repository.ReviewRepository;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewService {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchStatusCheckRepository matchStatusCheckRepository;

    @Autowired
    private ReputationService reputationService;

    public Review save(Review review) {
        Match match = review.getMatch();

        boolean matchExpired = Boolean.FALSE.equals(match.getActive());
        boolean matchConfirmed = match.getStatus() == StatusMatch.MATCHED;
        boolean matchRejected = match.getStatus() == StatusMatch.REJECTED;

        //So consegue fazer avaliacao sobre um match ja confirmado ou rejeitado
        if (!matchExpired && !matchConfirmed && !matchRejected) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Reviews are only allowed after a confirmed or rejected match.");
        }

        // Nao e possivel fazer mais de uma avaliacao por match
        if (reviewRepository.existsByMatchIdAndAuthorType(match.getId(), review.getAuthorType())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "A review from this author type already exists for this match.");
        }

        // Rejeitados nunca chegaram a ser confirmados, então não há status check
        // E necessario ter respondido o StatusCheck, caso nao tenha da erro
        if (review.getAuthorType() == AuthorType.COMPANY && !matchRejected
                && !matchStatusCheckRepository.existsByMatchId(match.getId())) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Please answer the match status check before reviewing.");
        }

        MatchStatusCheck statusCheck = matchStatusCheckRepository.findByMatchId(match.getId()).orElse(null);
        // Se ter statusCheck, e esse status for SEM CONTATO
        if (statusCheck != null && statusCheck.getOutcome() == MatchOutcome.NO_CONTACT_YET) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Reviews are not available when there was no contact.");
        }

        Review saved = reviewRepository.save(review);

        // Recalcula a reputacao pos avaliacao
        if (review.getAuthorType() == AuthorType.COMPANY) {
            reputationService.recalculateForProfessional(match.getProfessional().getId());
        } else {
            reputationService.recalculateForCompany(match.getProject().getCompany().getId());
        }

        return saved;
    }

    // Match mais antigo que expirou (30 dias) e ainda não foi avaliado por esse lado —
    // usado pra abrir a avaliação automaticamente no dashboard, igual ao status check.
    public Optional<PendingReviewDTO> findPendingForProfessional(Long professionalId) {
        return matchRepository.findPendingReviewsForProfessional(professionalId)
                .stream()
                .findFirst()
                .map(m -> new PendingReviewDTO(
                        m.getId(),
                        m.getProject().getCompany().getCompanyName(),
                        m.getProject().getTitle()));
    }

    public Optional<PendingReviewDTO> findPendingForCompany(Long companyId) {
        return matchRepository.findPendingReviewsForCompany(companyId)
                .stream()
                .findFirst()
                .map(m -> new PendingReviewDTO(
                        m.getId(),
                        m.getProfessional().getName(),
                        m.getProject().getTitle()));
    }

    // IDs dos matches já avaliados por esse lado — usado pra trocar "Avaliar" por
    // "Avaliado" na listagem de matches.
    public Set<Long> getReviewedMatchIdsForProfessional(Long professionalId) {
        return new HashSet<>(reviewRepository.findReviewedMatchIdsByProfessional(professionalId));
    }

    public Set<Long> getReviewedMatchIdsForCompany(Long companyId) {
        return new HashSet<>(reviewRepository.findReviewedMatchIdsByCompany(companyId));
    }
}
