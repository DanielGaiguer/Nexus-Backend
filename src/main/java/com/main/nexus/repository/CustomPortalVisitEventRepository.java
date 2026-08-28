package com.main.nexus.repository;

import com.main.nexus.model.CustomPortalVisitEvent;
import com.main.nexus.model.enums.CustomPortalEventType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomPortalVisitEventRepository
        extends JpaRepository<CustomPortalVisitEvent, Long> {

    @Query("SELECT COUNT(e) FROM CustomPortalVisitEvent e "
         + "WHERE e.customPortal.id = :portalId AND e.type = :type AND e.createdAt >= :since")
    long countByType(@Param("portalId") Long portalId,
                     @Param("type") CustomPortalEventType type,
                     @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(DISTINCT e.visitorId) FROM CustomPortalVisitEvent e "
         + "WHERE e.customPortal.id = :portalId AND e.type = :type AND e.createdAt >= :since")
    long countDistinctVisitors(@Param("portalId") Long portalId,
                               @Param("type") CustomPortalEventType type,
                               @Param("since") LocalDateTime since);

    @Query("SELECT AVG(e.durationSeconds) FROM CustomPortalVisitEvent e "
         + "WHERE e.customPortal.id = :portalId AND e.type = com.main.nexus.model.enums.CustomPortalEventType.SESSION_END "
         + "AND e.durationSeconds IS NOT NULL AND e.createdAt >= :since")
    Double avgSessionSeconds(@Param("portalId") Long portalId,
                             @Param("since") LocalDateTime since);

    // [java.sql.Date dia, long acessos] — PAGE_VIEW por dia.
    @Query("SELECT FUNCTION('DATE', e.createdAt), COUNT(e) FROM CustomPortalVisitEvent e "
         + "WHERE e.customPortal.id = :portalId AND e.type = com.main.nexus.model.enums.CustomPortalEventType.PAGE_VIEW "
         + "AND e.createdAt >= :since "
         + "GROUP BY FUNCTION('DATE', e.createdAt) ORDER BY FUNCTION('DATE', e.createdAt)")
    List<Object[]> viewsPerDay(@Param("portalId") Long portalId,
                               @Param("since") LocalDateTime since);

    // [Long opportunityId, long acessos] — vagas mais vistas.
    @Query("SELECT e.opportunityId, COUNT(e) FROM CustomPortalVisitEvent e "
         + "WHERE e.customPortal.id = :portalId AND e.type = com.main.nexus.model.enums.CustomPortalEventType.PAGE_VIEW "
         + "AND e.opportunityId IS NOT NULL AND e.createdAt >= :since "
         + "GROUP BY e.opportunityId ORDER BY COUNT(e) DESC")
    List<Object[]> topOpportunities(@Param("portalId") Long portalId,
                                    @Param("since") LocalDateTime since);

    // [String referrerHost (pode ser null), long acessos] — origem do tráfego.
    @Query("SELECT e.referrerHost, COUNT(e) FROM CustomPortalVisitEvent e "
         + "WHERE e.customPortal.id = :portalId AND e.type = com.main.nexus.model.enums.CustomPortalEventType.PAGE_VIEW "
         + "AND e.createdAt >= :since "
         + "GROUP BY e.referrerHost ORDER BY COUNT(e) DESC")
    List<Object[]> referrerBreakdown(@Param("portalId") Long portalId,
                                     @Param("since") LocalDateTime since);
}
