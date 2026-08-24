package com.main.nexus.service;

import com.main.nexus.dto.PendingReviewDTO;
import com.main.nexus.dto.ReviewDisplayDTO;
import com.main.nexus.dto.ReviewPageDTO;
import com.main.nexus.model.Match;
import com.main.nexus.model.MatchStatusCheck;
import com.main.nexus.model.Review;
import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.model.enums.MatchOutcome;
import com.main.nexus.model.enums.StatusMatch;
import com.main.nexus.repository.MatchRepository;
import com.main.nexus.repository.MatchStatusCheckRepository;
import com.main.nexus.repository.ReviewRepository;
import com.main.nexus.util.ReviewReasonMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
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

    // Contagem rápida (sem trazer o corpo das avaliações) — usado pelo badge do item
    // "Avaliações" da sidebar, que roda em toda página pra quem está logado, então precisa
    // ser leve (ao contrário de getAllForX, que traz a lista inteira).
    public long getReviewCountForProfessional(Long professionalId) {
        return reviewRepository.countByMatchProfessionalIdAndAuthorType(professionalId, AuthorType.COMPANY);
    }

    public long getReviewCountForCompany(Long companyId) {
        return reviewRepository.countByCompanyId(companyId);
    }

    // As melhores avaliações — usado no card de preview do perfil (público, próprio ou
    // visto pelo admin). `size` é opcional (default 3) pra não quebrar quem já chama sem
    // o parâmetro (nexus-frontend, o app antigo); o nexus-frontend-next pede 5.

    public List<ReviewDisplayDTO> getTop3ForProfessional(Long professionalId, int size) {
        return reviewRepository.findTop3ByProfessionalId(professionalId, PageRequest.of(0, size))
                .stream().map(this::toDisplayDTO).toList();
    }

    public List<ReviewDisplayDTO> getTop3ForCompany(Long companyId, int size) {
        return reviewRepository.findTop3ByCompanyId(companyId, PageRequest.of(0, size))
                .stream().map(this::toDisplayDTO).toList();
    }

    // Página dedicada de avaliações, com filtro opcional por nota.

    public ReviewPageDTO getAllForProfessional(Long professionalId, Integer ratingFilter) {
        List<Review> reviews = ratingFilter == null
                ? reviewRepository.findCompanyReviewsForProfessional(professionalId)
                : reviewRepository.findByProfessionalIdAndRating(professionalId, ratingFilter);

        Double avg = reviewRepository.findAverageRatingByProfessionalId(professionalId);
        long total = reviewRepository.countByMatchProfessionalIdAndAuthorType(professionalId, AuthorType.COMPANY);

        return new ReviewPageDTO(
                reviews.stream().map(this::toDisplayDTO).toList(),
                total,
                avg != null ? avg : 0.0);
    }

    public ReviewPageDTO getAllForCompany(Long companyId, Integer ratingFilter) {
        List<Review> reviews = ratingFilter == null
                ? reviewRepository.findProfessionalReviewsForCompany(companyId)
                : reviewRepository.findByCompanyIdAndRating(companyId, ratingFilter);

        Double avg = reviewRepository.findAverageRatingByCompanyId(companyId);
        long total = reviewRepository.countByCompanyId(companyId);

        return new ReviewPageDTO(
                reviews.stream().map(this::toDisplayDTO).toList(),
                total,
                avg != null ? avg : 0.0);
    }

    // Monta o DTO de exibição — resolve quem é o autor (empresa ou profissional) a partir
    // do match, e traduz os motivos pra português via ReviewReasonMapper.
    private ReviewDisplayDTO toDisplayDTO(Review review) {
        Match match = review.getMatch();
        boolean authoredByCompany = review.getAuthorType() == AuthorType.COMPANY;

        String reviewerName = authoredByCompany
                ? match.getProject().getCompany().getCompanyName()
                : match.getProfessional().getName();
        String reviewerPhotoUrl = authoredByCompany
                ? match.getProject().getCompany().getProfilePhotoUrl()
                : match.getProfessional().getProfilePhotoUrl();

        List<String> positive = review.getPositiveReasons() == null ? List.of()
                : review.getPositiveReasons().stream().map(ReviewReasonMapper::toPortuguese).toList();
        List<String> negative = review.getNegativeReasons() == null ? List.of()
                : review.getNegativeReasons().stream().map(ReviewReasonMapper::toPortuguese).toList();

        return new ReviewDisplayDTO(
                review.getId(),
                review.getRating() != null ? review.getRating() : 0,
                review.getComment(),
                positive,
                negative,
                reviewerName,
                reviewerPhotoUrl,
                review.getAuthorType().name(),
                match.getProject().getTitle(),
                review.getCreatedAt());
    }
}
