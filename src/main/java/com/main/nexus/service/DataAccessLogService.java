package com.main.nexus.service;

import com.main.nexus.dto.DataAccessLogDTO;
import com.main.nexus.dto.DataAccessLogPageDTO;
import com.main.nexus.model.DataAccessLog;
import com.main.nexus.repository.DataAccessLogRepository;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Consulta do log de auditoria pelo Admin -- filtrável por admin, por
// usuário-alvo e por período. Somente leitura (o log não se altera).
@Service
public class DataAccessLogService {

    private static final int MAX_SIZE = 200;

    @Autowired
    private DataAccessLogRepository repository;

    @Transactional(readOnly = true)
    public DataAccessLogPageDTO search(Long adminUserId, Long targetUserId,
                                       LocalDateTime from, LocalDateTime to,
                                       int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_SIZE);

        Page<DataAccessLog> result = repository.search(
                adminUserId, targetUserId, from, to, PageRequest.of(safePage, safeSize));

        return new DataAccessLogPageDTO(
                result.getContent().stream().map(DataAccessLogDTO::from).toList(),
                result.getTotalElements(),
                safePage,
                safeSize,
                result.hasNext());
    }
}
