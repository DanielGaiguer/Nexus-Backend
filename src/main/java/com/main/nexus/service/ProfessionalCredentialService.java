package com.main.nexus.service;

import com.main.nexus.dto.ProfessionalCredentialDTO;
import com.main.nexus.model.ProfessionalCredential;
import com.main.nexus.model.enums.CredentialType;
import com.main.nexus.repository.ProfessionalCredentialRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfessionalCredentialService {

    private static final int MAX_NAME_LENGTH = 150;
    private static final int MAX_ITEMS_PER_TYPE = 30;

    @Autowired
    private ProfessionalCredentialRepository professionalCredentialRepository;

    public List<ProfessionalCredentialDTO> findByProfessional(Long professionalId) {
        return professionalCredentialRepository.findByProfessionalId(professionalId)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    public ProfessionalCredential save(ProfessionalCredential credential) {
        credential.setName(normalizeName(credential.getName()));
        validateColor(credential);
        checkLimit(credential.getProfessional().getId(), credential.getType(), null);
        return professionalCredentialRepository.save(credential);
    }

    public ProfessionalCredential update(Long id, Long professionalId, ProfessionalCredentialDTO request) {
        ProfessionalCredential credential = professionalCredentialRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Credential not found: " + id));

        // profissional só edita a própria credencial
        if (!credential.getProfessional().getId().equals(professionalId)) {
            throw new ResponseStatusException(
                    HttpStatusCode.valueOf(403), "Not authorized.");
        }

        credential.setType(request.type());
        credential.setName(normalizeName(request.name()));
        credential.setColor(request.color());
        validateColor(credential);

        return professionalCredentialRepository.save(credential);
    }

    public void delete(Long id, Long professionalId) {
        ProfessionalCredential credential = professionalCredentialRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatusCode.valueOf(404), "Credential not found: " + id));

        // profissional só deleta a própria credencial
        if (!credential.getProfessional().getId().equals(professionalId)) {
            throw new ResponseStatusException(
                    HttpStatusCode.valueOf(403), "Not authorized.");
        }

        professionalCredentialRepository.deleteById(id);
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "O nome do certificado/evento é obrigatório.");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "O nome excede o limite de " + MAX_NAME_LENGTH + " caracteres.");
        }
        return trimmed;
    }

    private void validateColor(ProfessionalCredential credential) {
        if (credential.getColor() == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Selecione uma cor para o badge.");
        }
        if (credential.getType() == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Tipo de credencial inválido.");
        }
    }

    private void checkLimit(Long professionalId, CredentialType type, Long ignoreId) {
        long count = professionalCredentialRepository.findByProfessionalId(professionalId)
                .stream()
                .filter(c -> c.getType() == type && !c.getId().equals(ignoreId))
                .count();
        if (count >= MAX_ITEMS_PER_TYPE) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Máximo de " + MAX_ITEMS_PER_TYPE + " itens por tipo.");
        }
    }

    private ProfessionalCredentialDTO toDTO(ProfessionalCredential c) {
        return new ProfessionalCredentialDTO(c.getId(), c.getType(), c.getName(), c.getColor());
    }
}
