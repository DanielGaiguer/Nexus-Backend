package com.main.nexus.service;

import com.main.nexus.dto.ConsentStatusDTO;
import com.main.nexus.model.User;
import com.main.nexus.model.UserConsent;
import com.main.nexus.model.enums.ConsentSource;
import com.main.nexus.model.enums.ConsentType;
import com.main.nexus.model.enums.LegalDocumentType;
import com.main.nexus.ratelimit.ClientIpResolver;
import com.main.nexus.repository.UserConsentRepository;
import com.main.nexus.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

// Consentimento LGPD: registro (append-only) e leitura de estado.
//
// Ponto sensivel (Regra 6 do enunciado + investigacao do Passo 0.3): a
// finalidade ALGORITHM_IMPROVEMENT e registrada aqui mas NAO tem efeito pratico
// nenhum no sistema atual -- nao existe hoje nenhum uso agregado/analitico de
// dados entre usuarios (a formula de match tem pesos fixos em MatchService; a
// reputacao e calculada por entidade a partir das reviews da propria entidade em
// ReputationService). Recusar essa finalidade NAO afeta o calculo do proprio
// score de matchmaking do usuario, que e execucao de contrato. Se algum dia
// surgir um uso agregado real, o ponto de leitura deste consentimento entra
// AQUI (currentGranted(userId, ALGORITHM_IMPROVEMENT)) -- hoje ninguem chama.
@Service
public class UserConsentService {

    @Autowired
    private UserConsentRepository consentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LegalDocumentService legalDocumentService;

    @Autowired
    private ClientIpResolver clientIpResolver;

    // ── registro no cadastro ───────────────────────────────────────────

    // Chamado por AuthService apos criar o usuario. Lanca 400 se os Termos nao
    // foram aceitos -- o cadastro nao se conclui.
    @Transactional
    public void recordRegistrationConsents(User user,
                                           Boolean acceptedTerms,
                                           Boolean acceptedMarketing,
                                           Boolean acceptedAlgorithm) {
        requireTermsAccepted(acceptedTerms);
        String ip = currentIpOrNull();

        write(user, ConsentType.TERMS_OF_USE, true, ConsentSource.REGISTRATION, ip);
        write(user, ConsentType.MARKETING_COMMUNICATIONS,
                Boolean.TRUE.equals(acceptedMarketing), ConsentSource.REGISTRATION, ip);
        write(user, ConsentType.ALGORITHM_IMPROVEMENT,
                Boolean.TRUE.equals(acceptedAlgorithm), ConsentSource.REGISTRATION, ip);
    }

    // ── re-aceite (tela obrigatoria apos nova versao dos Termos) ────────

    @Transactional
    public void reaccept(Long userId,
                         Boolean acceptedTerms,
                         Boolean acceptedMarketing,
                         Boolean acceptedAlgorithm) {
        requireTermsAccepted(acceptedTerms);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        String ip = currentIpOrNull();

        write(user, ConsentType.TERMS_OF_USE, true, ConsentSource.REACCEPT_GATE, ip);

        // As duas opcionais so geram linha nova se o valor mudou de fato (ou se
        // nunca houve registro) -- "nao mexeu" mantem o ultimo estado, sem poluir
        // o log com repeticoes.
        maybeUpdateOptional(user, ConsentType.MARKETING_COMMUNICATIONS, acceptedMarketing, ip);
        maybeUpdateOptional(user, ConsentType.ALGORITHM_IMPROVEMENT, acceptedAlgorithm, ip);
    }

    private void maybeUpdateOptional(User user, ConsentType type, Boolean incoming, String ip) {
        if (incoming == null) {
            return;
        }
        boolean current = currentGranted(user.getId(), type);
        boolean hasRow = consentRepository
                .findTopByUserIdAndTypeOrderByCreatedAtDesc(user.getId(), type).isPresent();
        if (!hasRow || current != incoming) {
            write(user, type, incoming, ConsentSource.REACCEPT_GATE, ip);
        }
    }

    // ── leitura ────────────────────────────────────────────────────────

    // O usuario tem aceite valido dos Termos NA VERSAO ATIVA? Cobre os dois
    // casos que exigem re-aceite: (a) saiu versao nova; (b) conta criada via
    // OAuth que nunca passou por um checkbox (nenhuma linha).
    public boolean hasAcceptedActiveTerms(Long userId) {
        Integer activeVersion = legalDocumentService.activeVersionOrNull(LegalDocumentType.TERMS_OF_USE);
        if (activeVersion == null) {
            // Bootstrap incompleto: nao ha o que re-aceitar, nao trava ninguem.
            return true;
        }
        return consentRepository.existsByUserIdAndTypeAndGrantedTrueAndDocumentVersion(
                userId, ConsentType.TERMS_OF_USE, activeVersion);
    }

    public boolean currentGranted(Long userId, ConsentType type) {
        return consentRepository.findTopByUserIdAndTypeOrderByCreatedAtDesc(userId, type)
                .map(UserConsent::getGranted)
                .map(Boolean.TRUE::equals)
                .orElse(false);
    }

    public ConsentStatusDTO status(Long userId) {
        Integer activeTermsVersion =
                legalDocumentService.activeVersionOrNull(LegalDocumentType.TERMS_OF_USE);
        Integer activePrivacyVersion =
                legalDocumentService.activeVersionOrNull(LegalDocumentType.PRIVACY_POLICY);

        Integer acceptedTermsVersion = consentRepository
                .findTopByUserIdAndTypeOrderByCreatedAtDesc(userId, ConsentType.TERMS_OF_USE)
                .filter(c -> Boolean.TRUE.equals(c.getGranted()))
                .map(UserConsent::getDocumentVersion)
                .orElse(null);

        boolean mustReaccept = !hasAcceptedActiveTerms(userId);

        String summary = null;
        if (mustReaccept && activeTermsVersion != null) {
            summary = legalDocumentService
                    .active(LegalDocumentType.TERMS_OF_USE)
                    .summaryOfChanges();
        }

        return new ConsentStatusDTO(
                mustReaccept,
                activeTermsVersion,
                acceptedTermsVersion,
                summary,
                activePrivacyVersion,
                currentGranted(userId, ConsentType.MARKETING_COMMUNICATIONS),
                currentGranted(userId, ConsentType.ALGORITHM_IMPROVEMENT));
    }

    // ── interno ────────────────────────────────────────────────────────

    private void requireTermsAccepted(Boolean acceptedTerms) {
        if (!Boolean.TRUE.equals(acceptedTerms)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You must accept the Terms of Use to continue.");
        }
    }

    private void write(User user, ConsentType type, boolean granted,
                       ConsentSource source, String ip) {
        LegalDocumentType docType = (type == ConsentType.TERMS_OF_USE)
                ? LegalDocumentType.TERMS_OF_USE
                : LegalDocumentType.PRIVACY_POLICY; // marketing/algoritmo sao descritos na Politica
        Integer docVersion = legalDocumentService.activeVersionOrNull(docType);

        UserConsent row = new UserConsent();
        row.setUser(user);
        row.setType(type);
        row.setGranted(granted);
        row.setDocumentType(docType);
        row.setDocumentVersion(docVersion);
        row.setSource(source);
        row.setIpAddress(ip);
        row.setCreatedAt(LocalDateTime.now());
        consentRepository.save(row);
    }

    // IP do request corrente, best-effort. Fora de um escopo de request
    // (job, teste) retorna null -- IP nao e obrigatorio no registro de consent.
    private String currentIpOrNull() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest req = sra.getRequest();
                return clientIpResolver.resolve(req);
            }
        } catch (RuntimeException ignored) {
            // sem contexto de request -- segue sem IP
        }
        return null;
    }
}
