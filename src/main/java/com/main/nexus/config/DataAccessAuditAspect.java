package com.main.nexus.config;

import com.main.nexus.dto.UserDTO;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.AuditTargetType;
import com.main.nexus.repository.CompanyRepository;
import com.main.nexus.repository.CustomPortalRepository;
import com.main.nexus.repository.CustomPortalRequestRepository;
import com.main.nexus.repository.ProfessionalRepository;
import com.main.nexus.repository.SupportConversationRepository;
import com.main.nexus.repository.UserRepository;
import com.main.nexus.service.DataAccessLogWriter;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Grava uma linha em {@code tb_data_access_log} a cada chamada BEM-SUCEDIDA de
 * um método anotado com {@link AuditDataAccess} (endpoints administrativos que
 * expõem dado pessoal de um usuário específico -- LGPD, accountability).
 *
 * Roda DEPOIS de {@code proceed()}: só registra acessos que de fato
 * aconteceram (um 404/403 antes do handler não gera linha). Falha ao gravar o
 * log vira WARN e não derruba a ação do admin -- quebrar o painel porque a
 * tabela de auditoria está indisponível seria pior operacionalmente; a escrita
 * é em transação própria ({@link DataAccessLogWriter}).
 */
@Aspect
@Component
public class DataAccessAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(DataAccessAuditAspect.class);

    @Autowired private DataAccessLogWriter writer;
    @Autowired private UserRepository userRepository;
    @Autowired private ProfessionalRepository professionalRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private SupportConversationRepository supportConversationRepository;
    @Autowired private CustomPortalRepository customPortalRepository;
    @Autowired private CustomPortalRequestRepository customPortalRequestRepository;

    @Around("@annotation(audit)")
    public Object audit(ProceedingJoinPoint pjp, AuditDataAccess audit) throws Throwable {
        Object result = pjp.proceed();
        try {
            record(pjp, audit);
        } catch (RuntimeException e) {
            log.warn("DataAccessAuditAspect: falha ao gravar auditoria de '{}': {}",
                    audit.action(), e.getMessage());
        }
        return result;
    }

    private void record(ProceedingJoinPoint pjp, AuditDataAccess audit) {
        User admin = currentAdmin();
        if (admin == null) {
            // Endpoints anotados são todos hasRole("ADMIN"); se não há principal
            // admin aqui, algo muito estranho -- não inventa uma linha.
            log.warn("DataAccessAuditAspect: sem admin no contexto para '{}'.", audit.action());
            return;
        }

        Long targetEntityId = extractTargetId(pjp, audit.param());
        User targetUser = resolveTargetUser(audit.target(), targetEntityId);

        String httpMethod = null;
        String endpoint = null;
        HttpServletRequest req = currentRequest();
        if (req != null) {
            httpMethod = req.getMethod();
            endpoint = req.getRequestURI();
        }

        writer.write(admin, targetUser, audit.target(), targetEntityId,
                audit.action(), httpMethod, endpoint);
    }

    private User currentAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDTO dto) {
            return userRepository.findById(dto.id()).orElse(null);
        }
        return null;
    }

    private Long extractTargetId(ProceedingJoinPoint pjp, String paramName) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        String[] names = sig.getParameterNames();
        Object[] args = pjp.getArgs();

        if (paramName != null && !paramName.isBlank() && names != null) {
            for (int i = 0; i < names.length; i++) {
                if (paramName.equals(names[i])) {
                    return toLong(args[i]);
                }
            }
        }
        // fallback: primeiro Long/long
        for (Object a : args) {
            if (a instanceof Long l) {
                return l;
            }
        }
        return null;
    }

    private Long toLong(Object o) {
        if (o instanceof Long l) {
            return l;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    // Resolve o id recebido pelo endpoint para o User titular do dado. Best
    // effort: se não achar, registra com targetUser nulo (o targetEntityId +
    // targetType ainda dizem o que foi acessado).
    private User resolveTargetUser(AuditTargetType type, Long id) {
        if (id == null || type == AuditTargetType.NONE) {
            return null;
        }
        try {
            return switch (type) {
                case USER -> userRepository.findById(id).orElse(null);
                case PROFESSIONAL -> professionalRepository.findById(id)
                        .map(p -> p.getUser()).orElse(null);
                case COMPANY -> companyRepository.findById(id)
                        .map(c -> c.getUser()).orElse(null);
                case SUPPORT_CONVERSATION -> supportConversationRepository.findById(id)
                        .map(sc -> sc.getUser()).orElse(null);
                case CUSTOM_PORTAL -> customPortalRepository.findById(id)
                        .map(cp -> cp.getCompany() != null ? cp.getCompany().getUser() : null)
                        .orElse(null);
                case CUSTOM_PORTAL_REQUEST -> customPortalRequestRepository.findById(id)
                        .map(r -> r.getCompany() != null ? r.getCompany().getUser() : null)
                        .orElse(null);
                case NONE -> null;
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }
}
