package com.main.nexus.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FileStorageService {

    @Value("${nexus.upload.dir}")
    private String uploadDir;

    public String storeResume(MultipartFile file, Long professionalId) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400), "File is empty.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Only PDF files are accepted.");
        }

        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            String fileName = "prof_" + professionalId + "_" + UUID.randomUUID() + ".pdf";
            Path targetPath = uploadPath.resolve(fileName);

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            return fileName;

        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(500),
                    "Failed to store file.", e);
        }
    }

    public byte[] loadResume(String fileName) {
        try {
            Path filePath = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(fileName);
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(404),
                    "File not found: " + fileName);
        }
    }

    public void deleteResume(String fileName) {
        if (fileName == null || fileName.isBlank()) return;
        try {
            Path filePath = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(fileName);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // Falha silenciosa na deleção — arquivo pode já ter sido removido
        }
    }
}