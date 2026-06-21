package com.main.nexus.controller;

import com.main.nexus.dto.ReviewRequestDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Match;
import com.main.nexus.model.Review;
import com.main.nexus.model.enums.AuthorType;
import com.main.nexus.service.MatchService;
import com.main.nexus.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @PostMapping("/{matchId}")
    public ResponseEntity<String> submitReview(
            @PathVariable Long matchId,
            @RequestBody ReviewRequestDTO request) {

        UserDTO logged = getLoggedUser();
        Match match = matchService.findById(matchId);

        // Valida que quem está avaliando é participante do match
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

        reviewService.save(review);
        return ResponseEntity.ok("Review submitted successfully.");
    }

    private UserDTO getLoggedUser() {
        return (UserDTO) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}