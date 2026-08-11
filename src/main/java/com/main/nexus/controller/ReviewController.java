package com.main.nexus.controller;

import com.main.nexus.dto.PendingReviewDTO;
import com.main.nexus.dto.ReviewRequestDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Company;
import com.main.nexus.model.Match;
import com.main.nexus.model.Professional;
import com.main.nexus.model.Review;
import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.service.CompanyService;
import com.main.nexus.service.MatchService;
import com.main.nexus.service.ProfessionalService;
import com.main.nexus.service.ReviewService;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private MatchService matchService;

    @Autowired
    private ProfessionalService professionalService;

    @Autowired
    private CompanyService companyService;

    @PostMapping("/{matchId}")
    public ResponseEntity<String> submitReview(
            @PathVariable Long matchId,
            @RequestBody ReviewRequestDTO request) {

        UserDTO logged = getLoggedUser();
        Match match = matchService.findById(matchId);

        boolean isCompany = match.getProject().getCompany().getUser().getId().equals(logged.id());
        boolean isProfessional = match.getProfessional().getUser().getId().equals(logged.id());

        if (!isCompany && !isProfessional) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "You are not a participant of this match.");
        }

        if (request.authorType() == AuthorType.COMPANY && !isCompany) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "Only the company of this match can submit a COMPANY review.");
        }
        if (request.authorType() == AuthorType.PROFESSIONAL && !isProfessional) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(403),
                    "Only the professional of this match can submit a PROFESSIONAL review.");
        }

        Review review = new Review();
        review.setMatch(match);
        review.setRating(request.rating());
        review.setComment(request.comment());
        review.setAuthorType(request.authorType());
        review.setPositiveReasons(request.positiveReasons());
        review.setNegativeReasons(request.negativeReasons());

        reviewService.save(review);
        return ResponseEntity.ok("Review submitted successfully.");
    }

    // Usado pelo dashboard do profissional pra abrir a avaliação automaticamente,
    // sem precisar clicar na notificação.
    @GetMapping("/pending/professional")
    public ResponseEntity<PendingReviewDTO> getPendingForProfessional() {
        Long professionalId = getLoggedProfessionalId();
        return reviewService.findPendingForProfessional(professionalId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "No pending review."));
    }

    @GetMapping("/pending/company")
    public ResponseEntity<PendingReviewDTO> getPendingForCompany() {
        Long companyId = getLoggedCompanyId();
        return reviewService.findPendingForCompany(companyId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "No pending review."));
    }

    // Usado na listagem de matches pra trocar "Avaliar" por "Avaliado"
    @GetMapping("/reviewed-match-ids/professional")
    public ResponseEntity<Set<Long>> getReviewedMatchIdsForProfessional() {
        Long professionalId = getLoggedProfessionalId();
        return ResponseEntity.ok(reviewService.getReviewedMatchIdsForProfessional(professionalId));
    }

    @GetMapping("/reviewed-match-ids/company")
    public ResponseEntity<Set<Long>> getReviewedMatchIdsForCompany() {
        Long companyId = getLoggedCompanyId();
        return ResponseEntity.ok(reviewService.getReviewedMatchIdsForCompany(companyId));
    }

    // Utilitários de identidade

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }

    private Long getLoggedProfessionalId() {
        UserDTO logged = getLoggedUser();
        Professional professional = professionalService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Professional profile not found"));
        return professional.getId();
    }

    private Long getLoggedCompanyId() {
        UserDTO logged = getLoggedUser();
        Company company = companyService.findByUserId(logged.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Company profile not found"));
        return company.getId();
    }
}
