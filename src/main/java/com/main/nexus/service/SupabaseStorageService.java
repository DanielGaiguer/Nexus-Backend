package com.main.nexus.service;

import java.util.List;
import java.util.Map;
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

    // Videos de resposta de triagem. UNICO bucket PRIVADO do sistema -- os outros tres sao
    // publicos e servem URL direta. Imagem e voz de um candidato identificavel nao podem ficar
    // atras de "o path tem um UUID, ninguem adivinha": aqui todo acesso passa por signed URL de
    // curta validade, gerada sob guard (ver ScreeningVideoService).
    @Value("${supabase.bucket.screening-videos}")
    private String screeningVideosBucket;

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

    // Imagens de branding da plataforma personalizada (logo/banner/favicon).
    // Reusa a mesma validacao (JPEG/PNG/WebP, 5MB) e o mesmo bucket compartilhado
    // de imagens; so muda a pasta.
    public String uploadCustomPortalImage(MultipartFile file, Long portalId, String kind) {
        validateImageFile(file);

        String extension = getExtension(file.getOriginalFilename());
        String fileName = "custom-portals/" + portalId + "/" + kind + "_" + UUID.randomUUID() + "." + extension;

        return uploadToBucket(file, profileImagesBucket, fileName);
    }

    public void deleteCustomPortalImage(String publicUrl) {
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

    // ── VIDEO DE TRIAGEM (bucket privado) ──────────────────────────────────────────────
    //
    // Fluxo diferente dos outros tres uploads DE PROPOSITO. Ali o arquivo atravessa
    // browser -> route handler do Next -> Spring -> Supabase, com buffer inteiro em memoria nos
    // dois saltos do meio; com 50MB de video e alguns candidatos ao mesmo tempo isso derruba o
    // backend. Aqui o Spring so ASSINA: o browser sobe direto pro Supabase com a signed URL, e
    // o backend volta a participar apenas na confirmacao.
    //
    // Consequencia que precisa ficar explicita: como o arquivo nao passa por aqui, nao da pra
    // validar antes. O limite de tamanho do BUCKET (configurado no painel, ver
    // application.properties) e a unica trava preventiva; a checagem em
    // screeningVideoSizeBytes acontece depois do upload e serve pra recusar e apagar.

    private static final java.util.Set<String> SCREENING_VIDEO_CONTENT_TYPES = java.util.Set.of(
            "video/webm", "video/mp4", "video/quicktime");

    // uploadUrl ja absoluta e pronta pro browser; objectUrl e o que fica gravado em
    // ScreeningAnswer.videoUrl.
    public record VideoUploadTicket(String uploadUrl, String token, String objectUrl) {}

    // Validacao declarada, no mesmo molde das outras tres deste service -- roda ANTES de assinar
    // (content-type) e de novo na confirmacao (tamanho real medido no Supabase).
    public void validateScreeningVideoContentType(String contentType) {
        if (contentType == null || !SCREENING_VIDEO_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Only WebM, MP4 and QuickTime video files are accepted.");
        }
    }

    public void validateScreeningVideoSize(long sizeBytes, long maxSizeBytes) {
        if (sizeBytes <= 0) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "The uploaded video is empty.");
        }
        if (sizeBytes > maxSizeBytes) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Video size must not exceed " + (maxSizeBytes / (1024 * 1024)) + "MB.");
        }
    }

    public VideoUploadTicket createScreeningVideoUploadTicket(
            Long invitationId, Long questionId, String contentType, int ttlSeconds) {
        validateScreeningVideoContentType(contentType);

        String path = "screenings/" + invitationId + "/" + questionId + "/"
                + UUID.randomUUID() + "." + videoExtension(contentType);
        String signUrl = supabaseUrl + "/storage/v1/object/upload/sign/"
                + screeningVideosBucket + "/" + path;

        HttpHeaders headers = serviceHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // expiresIn nao e suportado por toda versao do endpoint de upload assinado; quando for
        // ignorado vale o default do Supabase. Mandar e inofensivo e da o TTL curto onde ha
        // suporte.
        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(Map.of("expiresIn", ttlSeconds), headers);

        Map<?, ?> body;
        try {
            body = restTemplate.exchange(signUrl, HttpMethod.POST, entity, Map.class).getBody();
        } catch (Exception e) {
            log.error("Supabase signed upload URL failed: bucket={}, path={}",
                    screeningVideosBucket, path, e);
            throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                    "Failed to prepare the video upload. Please try again.", e);
        }

        Object relative = body != null ? body.get("url") : null;
        if (relative == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                    "Storage did not return an upload URL.");
        }

        String uploadUrl = supabaseUrl + "/storage/v1" + relative;
        return new VideoUploadTicket(uploadUrl, extractToken(uploadUrl), screeningVideoObjectUrl(path));
    }

    // Signed URL de LEITURA, curta. Nunca devolvida sem passar pelo guard de
    // ScreeningVideoService -- este metodo por si so nao autoriza ninguem.
    public String signScreeningVideoUrl(String objectUrl, int ttlSeconds) {
        String path = screeningVideoPath(objectUrl);
        if (path == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404), "Video not found.");
        }

        String signUrl = supabaseUrl + "/storage/v1/object/sign/" + screeningVideosBucket + "/" + path;
        HttpHeaders headers = serviceHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(Map.of("expiresIn", ttlSeconds), headers);

        Map<?, ?> body;
        try {
            body = restTemplate.exchange(signUrl, HttpMethod.POST, entity, Map.class).getBody();
        } catch (Exception e) {
            log.error("Supabase signed read URL failed: bucket={}, path={}",
                    screeningVideosBucket, path, e);
            throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                    "Failed to prepare the video playback link. Please try again.", e);
        }

        Object relative = body != null ? body.get("signedURL") : null;
        if (relative == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404),
                    "This video is no longer available.");
        }
        return supabaseUrl + "/storage/v1" + relative;
    }

    // Tamanho real do objeto ja no bucket, medido no Supabase -- e o que permite recusar um
    // arquivo grande DEPOIS do upload direto. Devolve null quando nao da pra saber (objeto
    // ausente, ou a consulta de metadados falhou): quem chama decide o que fazer com a duvida,
    // aqui nao se inventa um numero.
    public Long screeningVideoSizeBytes(String objectUrl) {
        String path = screeningVideoPath(objectUrl);
        if (path == null) {
            return null;
        }
        int slash = path.lastIndexOf('/');
        String folder = slash > 0 ? path.substring(0, slash) : "";
        String name = slash > 0 ? path.substring(slash + 1) : path;

        HttpHeaders headers = serviceHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
                Map.of("prefix", folder, "limit", 100, "search", name), headers);

        try {
            List<?> items = restTemplate.exchange(
                    supabaseUrl + "/storage/v1/object/list/" + screeningVideosBucket,
                    HttpMethod.POST, entity, List.class).getBody();
            if (items == null) {
                return null;
            }
            for (Object item : items) {
                if (!(item instanceof Map<?, ?> row) || !name.equals(row.get("name"))) {
                    continue;
                }
                if (row.get("metadata") instanceof Map<?, ?> metadata
                        && metadata.get("size") instanceof Number size) {
                    return size.longValue();
                }
            }
        } catch (Exception e) {
            log.warn("Supabase object metadata unavailable: bucket={}, path={}: {}",
                    screeningVideosBucket, path, e.getMessage());
        }
        return null;
    }

    public void deleteScreeningVideo(String objectUrl) {
        String path = screeningVideoPath(objectUrl);
        if (path == null) {
            return;
        }
        HttpHeaders headers = serviceHeaders();
        try {
            restTemplate.exchange(
                    supabaseUrl + "/storage/v1/object/" + screeningVideosBucket + "/" + path,
                    HttpMethod.DELETE, new HttpEntity<Void>(headers), String.class);
        } catch (Exception e) {
            // Mesma falha silenciosa dos outros deletes deste service: a operacao do usuario
            // (excluir a conta) nunca trava por causa da limpeza do arquivo. Aqui, porem, a
            // sobra tem peso diferente de uma foto orfa -- por isso e log de WARN, nao silencio.
            log.warn("Screening video not deleted from storage (path={}): {}", path, e.getMessage());
        }
    }

    // Forma canonica gravada em ScreeningAnswer.videoUrl: o endpoint AUTENTICADO do objeto, nao
    // o /public/... dos outros buckets. E uma URL de verdade (e reversivel pra path, que e o que
    // assinar e apagar precisam), mas inutil colada no browser -- exatamente o que se quer.
    private String screeningVideoObjectUrl(String path) {
        return supabaseUrl + "/storage/v1/object/" + screeningVideosBucket + "/" + path;
    }

    private String screeningVideoPath(String objectUrl) {
        if (objectUrl == null || objectUrl.isBlank()) {
            return null;
        }
        String prefix = supabaseUrl + "/storage/v1/object/" + screeningVideosBucket + "/";
        return objectUrl.startsWith(prefix) ? objectUrl.substring(prefix.length()) : null;
    }

    private String extractToken(String signedUrl) {
        int marker = signedUrl.indexOf("token=");
        if (marker < 0) {
            return null;
        }
        String token = signedUrl.substring(marker + "token=".length());
        int amp = token.indexOf('&');
        return amp >= 0 ? token.substring(0, amp) : token;
    }

    private String videoExtension(String contentType) {
        return switch (contentType) {
            case "video/mp4" -> "mp4";
            case "video/quicktime" -> "mov";
            default -> "webm";
        };
    }

    private HttpHeaders serviceHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + serviceKey);
        headers.set("apikey", serviceKey);
        return headers;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
