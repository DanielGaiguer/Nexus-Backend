package com.main.nexus.controller;

import com.main.nexus.dto.ReviewRequestDTO;
import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.Match;
import com.main.nexus.model.Review;
import com.main.nexus.service.MatchService;
import com.main.nexus.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        Match match = matchService.findById(matchId);

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