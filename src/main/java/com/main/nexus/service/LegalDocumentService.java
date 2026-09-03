package com.main.nexus.service;

import com.main.nexus.dto.AdminLegalOverviewDTO;
import com.main.nexus.dto.LegalDocumentDTO;
import com.main.nexus.dto.LegalDocumentVersionDTO;
import com.main.nexus.model.LegalDocument;
import com.main.nexus.model.User;
import com.main.nexus.model.enums.LegalDocumentType;
import com.main.nexus.repository.LegalDocumentRepository;
import com.main.nexus.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Versionamento dos documentos legais (Termos de Uso / Politica de Privacidade).
//
// Regras:
//  - Uma versao ativa por tipo. publishNewVersion() derruba a anterior e sobe a
//    nova na MESMA transacao -- invariante de servico, nao do banco.
//  - Versao monotonica por tipo, comeca em 1 (a v1 e semeada por
//    LegalDocumentSeed no bootstrap).
//  - Publicar uma nova versao dos TERMOS e o que dispara o re-aceite obrigatorio
//    para todo mundo (ver ConsentGateFilter / UserConsentService).
@Service
public class LegalDocumentService {

    @Autowired
    private LegalDocumentRepository repository;

    @Autowired
    private UserRepository userRepository;

    // ── leitura ────────────────────────────────────────────────────────

    public LegalDocument activeEntity(LegalDocumentType type) {
        return repository.findByTypeAndActiveTrue(type)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No active version for " + type + "."));
    }

    public LegalDocumentDTO active(LegalDocumentType type) {
        return LegalDocumentDTO.from(activeEntity(type));
    }

    // Versao ativa como numero -- consultado pelo filtro/serviço de consentimento.
    // Se ainda nao ha nenhuma versao (bootstrap incompleto), retorna null e o
    // chamador trata como "nada a re-aceitar" para nao travar o app.
    public Integer activeVersionOrNull(LegalDocumentType type) {
        return repository.findByTypeAndActiveTrue(type)
                .map(LegalDocument::getVersion)
                .orElse(null);
    }

    public List<LegalDocumentVersionDTO> versions(LegalDocumentType type) {
        return repository.findByTypeOrderByVersionDesc(type).stream()
                .map(LegalDocumentVersionDTO::from)
                .toList();
    }

    public LegalDocumentDTO version(LegalDocumentType type, int version) {
        return repository.findByTypeAndVersion(type, version)
                .map(LegalDocumentDTO::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Version " + version + " not found for " + type + "."));
    }

    public AdminLegalOverviewDTO adminOverview() {
        return new AdminLegalOverviewDTO(
                typeView(LegalDocumentType.TERMS_OF_USE),
                typeView(LegalDocumentType.PRIVACY_POLICY));
    }

    private AdminLegalOverviewDTO.TypeView typeView(LegalDocumentType type) {
        LegalDocumentDTO active = repository.findByTypeAndActiveTrue(type)
                .map(LegalDocumentDTO::from)
                .orElse(null);
        return new AdminLegalOverviewDTO.TypeView(
                type.name(), type.slug(), active, versions(type));
    }

    // ── escrita ────────────────────────────────────────────────────────

    @Transactional
    public LegalDocumentDTO publishNewVersion(LegalDocumentType type,
                                              String content,
                                              String summaryOfChanges,
                                              Long adminId) {
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Document content is required.");
        }

        repository.findByTypeAndActiveTrue(type).ifPresent(current -> {
            current.setActive(false);
            repository.save(current);
        });

        Integer max = repository.findMaxVersion(type);
        int next = (max == null ? 0 : max) + 1;

        User admin = adminId == null ? null
                : userRepository.findById(adminId).orElse(null);

        LegalDocument doc = new LegalDocument();
        doc.setType(type);
        doc.setVersion(next);
        doc.setTitle(defaultTitle(type));
        doc.setContent(content.trim());
        doc.setSummaryOfChanges(
                summaryOfChanges != null && !summaryOfChanges.isBlank()
                        ? summaryOfChanges.trim() : null);
        doc.setActive(true);
        doc.setPublishedAt(LocalDateTime.now());
        doc.setPublishedByAdmin(admin);
        doc.setCreatedAt(LocalDateTime.now());

        return LegalDocumentDTO.from(repository.save(doc));
    }

    // Usado pelo seed (bootstrap) -- insere a v1 ativa se ainda nao existe
    // nenhuma versao do tipo. Idempotente.
    @Transactional
    public void seedInitialVersion(LegalDocumentType type, String content) {
        if (repository.existsByType(type)) {
            return;
        }
        LegalDocument doc = new LegalDocument();
        doc.setType(type);
        doc.setVersion(1);
        doc.setTitle(defaultTitle(type));
        doc.setContent(content);
        doc.setSummaryOfChanges(null);
        doc.setActive(true);
        doc.setPublishedAt(LocalDateTime.now());
        doc.setPublishedByAdmin(null);
        doc.setCreatedAt(LocalDateTime.now());
        repository.save(doc);
    }

    private String defaultTitle(LegalDocumentType type) {
        return type == LegalDocumentType.TERMS_OF_USE
                ? "Termos de Uso" : "Política de Privacidade";
    }
}
