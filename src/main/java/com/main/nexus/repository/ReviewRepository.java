package com.main.nexus.repository;

import com.main.nexus.model.Review;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByMatchId(Long matchId);
    
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.match.professional.id = :professionalId")
    Double findAverageRatingByProfessionalId(Long professionalId);
    
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.match.project.company.id = :companyId")
    Double findAverageRatingByCompanyId(Long companyId);
}