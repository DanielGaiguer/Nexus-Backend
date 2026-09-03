package com.main.nexus.service;

import com.main.nexus.model.DataAccessLog;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.AuditTargetType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste a linha de auditoria numa transação NOVA e independente
 * ({@code REQUIRES_NEW}): a trilha é gravada mesmo que a transação da
 * requisição administrativa venha a dar rollback depois, e uma falha ao gravar
 * o log não derruba a ação do admin (só é registrada como WARN pelo aspecto).
 */
@Service
public class DataAccessLogWriter {

    @Autowired
    private com.main.nexus.repository.DataAccessLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(User admin, User targetUser, AuditTargetType targetType,
                      Long targetEntityId, String action, String httpMethod, String endpoint) {
        repository.save(new DataAccessLog(
                admin, targetUser, targetType, targetEntityId, action, httpMethod, endpoint));
    }
}
