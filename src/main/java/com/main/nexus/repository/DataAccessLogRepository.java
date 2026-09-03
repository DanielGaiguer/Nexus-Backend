package com.main.nexus.repository;

import com.main.nexus.model.DataAccessLog;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Deliberadamente estende {@code Repository} (não {@code JpaRepository}): expõe
 * só INSERÇÃO e CONSULTA. Sem {@code delete}/{@code deleteById}/{@code saveAll}
 * de update -- o log de auditoria é imutável (Rule 5). Ninguém, nem admin,
 * apaga uma linha pela aplicação.
 */
public interface DataAccessLogRepository extends Repository<DataAccessLog, Long> {

    DataAccessLog save(DataAccessLog entry);

    @Query("""
            SELECT l FROM DataAccessLog l
            WHERE (:adminUserId IS NULL OR l.adminUser.id = :adminUserId)
              AND (:targetUserId IS NULL OR l.targetUser.id = :targetUserId)
              AND (:from IS NULL OR l.createdAt >= :from)
              AND (:to IS NULL OR l.createdAt <= :to)
            ORDER BY l.createdAt DESC
            """)
    Page<DataAccessLog> search(@Param("adminUserId") Long adminUserId,
                               @Param("targetUserId") Long targetUserId,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to,
                               Pageable pageable);
}
