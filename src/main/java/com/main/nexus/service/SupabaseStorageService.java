package com.main.nexus.service;

import java.util.UUID;
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

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.bucket}")
    private String bucket;

    @Value("${supabase.service-key}")
    private String serviceKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public String uploadProfilePhoto(MultipartFile file, String folder, Long entityId) {
        validateImageFile(file);

        String extension = getExtension(file.getOriginalFilename());
        String fileName = folder + "/" + entityId + "_" + UUID.randomUUID() + "." + extension;
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
            throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                    "Failed to upload image to storage. Please try again.");
        }

        // Retorna a URL pública da imagem
        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + fileName;
    }

    public void deleteProfilePhoto(String publicUrl) {
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
            // Falha silenciosa na deleção — arquivo pode já ter sido removido
        }
    }

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

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}