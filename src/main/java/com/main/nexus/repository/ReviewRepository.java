package com.main.nexus.repository;

import com.main.nexus.model.Review;
import com.main.nexus.model.enums.AuthorType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByMatchId(Long matchId);

    boolean existsByMatchIdAndAuthorType(Long matchId, AuthorType authorType);

    List<Review> findByMatchProfessionalId(Long professionalId);

    List<Review> findByMatchProjectCompanyId(Long companyId);
}