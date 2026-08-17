package com.main.nexus.controller;

import com.main.nexus.dto.ReviewDisplayDTO;
import com.main.nexus.dto.ReviewPageDTO;
import com.main.nexus.service.ReviewService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Endpoints públicos de exibição de avaliações — card de preview (top 3) e página
// dedicada (todas, com filtro de estrela) tanto de profissional quanto de empresa.
// Público porque avaliações são informação de perfil, visível por qualquer visitante,
// igual ao resto do perfil público.
@RestController
@RequestMapping("/api/reviews")
public class ReviewDisplayController {

    @Autowired
    private ReviewService reviewService;

    @GetMapping("/professional/{professionalId}/count")
    public long getCountForProfessional(@PathVariable Long professionalId) {
        return reviewService.getReviewCountForProfessional(professionalId);
    }

    @GetMapping("/company/{companyId}/count")
    public long getCountForCompany(@PathVariable Long companyId) {
        return reviewService.getReviewCountForCompany(companyId);
    }

    @GetMapping("/professional/{professionalId}/top3")
    public List<ReviewDisplayDTO> getTop3ForProfessional(@PathVariable Long professionalId) {
        return reviewService.getTop3ForProfessional(professionalId);
    }

    @GetMapping("/professional/{professionalId}/all")
    public ReviewPageDTO getAllForProfessional(
            @PathVariable Long professionalId,
            @RequestParam(required = false) Integer rating) {
        return reviewService.getAllForProfessional(professionalId, rating);
    }

    @GetMapping("/company/{companyId}/top3")
    public List<ReviewDisplayDTO> getTop3ForCompany(@PathVariable Long companyId) {
        return reviewService.getTop3ForCompany(companyId);
    }

    @GetMapping("/company/{companyId}/all")
    public ReviewPageDTO getAllForCompany(
            @PathVariable Long companyId,
            @RequestParam(required = false) Integer rating) {
        return reviewService.getAllForCompany(companyId, rating);
    }
}
