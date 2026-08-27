package com.main.nexus.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SupabaseStorageService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageService.class);

    // O sistema usa dois buckets do Supabase Storage, configurados no application.properties , profile-images-nexus (fotos de perfil, compartilhado entre profissionais e empresas)
    // e resume-professional-nexus (currículos, exclusivo de profissionais). O serviceKey a chave de serviço do Supabase
    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.bucket.profile-images}")
    private String profileImagesBucket;

    @Value("${supabase.bucket.resumes}")
    private String resumeBucket;

    @Value("${supabase.bucket.proposal-attachments}")
    private String proposalAttachmentsBucket;

    @Value("${supabase.service-key}")
    private String serviceKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public String uploadProfilePhoto(MultipartFile file, String folder, Long entityId) {
        validateImageFile(file);

        String extension = getExtension(file.getOriginalFilename());
        String fileName = folder + "/" + entityId + "_" + UUID.randomUUID() + "." + extension;

        return uploadToBucket(file, profileImagesBucket, fileName);
    }

    public void deleteProfilePhoto(String publicUrl) {
        deleteFromBucket(publicUrl, profileImagesBucket);
    }

    public String uploadResume(MultipartFile file, Long professionalId) {
        validateResumeFile(file);

        String fileName = "professionals/" + professionalId + "_" + UUID.randomUUID() + ".pdf";

        return uploadToBucket(file, resumeBucket, fileName);
    }

    public void deleteResume(String publicUrl) {
        deleteFromBucket(publicUrl, resumeBucket);
    }

    // Anexos/portfólio de Proposal -- diferente de foto de perfil/currículo, aqui não é "1
    // arquivo que substitui o anterior": cada proposta pode ter vários anexos, cada um com sua
    // própria linha em ProposalAttachment, então não há um "arquivo anterior" pra deletar antes
    // do upload -- o chamador decide se remove um anexo específico via deleteProposalAttachment.
    public String uploadProposalAttachment(MultipartFile file, Long proposalId) {
        validateProposalAttachmentFile(file);

        String extension = getExtension(file.getOriginalFilename());
        String fileName = "proposals/" + proposalId + "/" + UUID.randomUUID() + "." + extension;

        return uploadToBucket(file, proposalAttachmentsBucket, fileName);
    }

    public void deleteProposalAttachment(String publicUrl) {
        deleteFromBucket(publicUrl, proposalAttachmentsBucket);
    }

    public byte[] downloadResume(String publicUrl) {
        try {
            return restTemplate.getForObject(publicUrl, byte[].class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404),
                    "Resume file not found.");
        }
    }

    private String uploadToBucket(MultipartFile file, String bucket, String fileName) {
        String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucket + "/" + fileName;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + serviceKey);
        headers.set("apikey", serviceKey);
        headers.setContentType(MediaType.parseMediaType(file.getContentType()));
        headers.set("x-upsert", "true"); // substitui se já existir

        try {
            byte[] bytes = file.getBytes();
            HttpEntity<byte[]> entity = new HttpEntity<>(bytes, headers);
            restTemplate.exchange(uploadUrl, HttpMethod.POST, entity, String.class);
        } catch (Exception e) {
            log.error("Supabase upload failed: bucket={}, fileName={}, url={}",
                    bucket, fileName, uploadUrl, e);
            throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                    "Failed to upload file to storage. Please try again.", e);
        }

        // Retorna a URL pública do arquivo
        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + fileName;
    }

    private void deleteFromBucket(String publicUrl, String bucket) {
        if (publicUrl == null || publicUrl.isBlank()) return;

        // Extrai o path relativo da URL pública
        String prefix = supabaseUrl + "/storage/v1/object/public/" + bucket + "/";
        if (!publicUrl.startsWith(prefix)) return;

        String filePath = publicUrl.substring(prefix.length());
        String deleteUrl = supabaseUrl + "/storage/v1/object/" + bucket + "/" + filePath;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + serviceKey);
        headers.set("apikey", serviceKey);

        try {
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            restTemplate.exchange(deleteUrl, HttpMethod.DELETE, entity, String.class);
        } catch (Exception e) {
            // Falha silenciosa na deleção, arquivo pode já ter sido removido
        }
    }
    
    // se o Supabase estiver temporariamente indisponível, ou a exclusão falhar por qualquer motivo, o fluxo continua normalmente para o upload do novo arquivo 
    // a operação do usuário  sempre é priorizada sobre a garantia de limpeza do arquivo antigo. ao longo do tempo, 
    // o bucket pode acumular arquivos órfãos (fotos/currículos antigos que nunca foram efetivamente removidos do Supabase)

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Image file is required.");
        }

        String contentType = file.getContentType();
        if (contentType == null ||
                (!contentType.equals("image/jpeg") &&
                 !contentType.equals("image/png") &&
                 !contentType.equals("image/webp"))) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Only JPEG, PNG and WebP images are accepted.");
        }

        long maxSize = 5 * 1024 * 1024; // 5MB
        if (file.getSize() > maxSize) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Image size must not exceed 5MB.");
        }
    }

    private void validateResumeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Resume file is required.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Only PDF files are accepted.");
        }

        long maxSize = 10 * 1024 * 1024; // 10MB
        if (file.getSize() > maxSize) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Resume size must not exceed 10MB.");
        }
    }

    private static final java.util.Set<String> PROPOSAL_ATTACHMENT_CONTENT_TYPES = java.util.Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp", "application/zip",
            "application/x-zip-compressed");

    private void validateProposalAttachmentFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Attachment file is required.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !PROPOSAL_ATTACHMENT_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Only PDF, PNG, JPEG, WebP and ZIP files are accepted.");
        }

        long maxSize = 15 * 1024 * 1024; // 15MB
        if (file.getSize() > maxSize) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Attachment size must not exceed 15MB.");
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
