package com.serdar.user.service;

import com.serdar.common.ServiceException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stores profile / group photos on a local directory (Docker volume in prod).
 * Public URLs are served by the API gateway from the same mounted path.
 */
@Service
@Slf4j
public class LocalImageStorageService {

    @Value("${app.upload.storage-dir}")
    private String storageDir;

    @Value("${app.upload.public-base-url}")
    private String publicBaseUrl;

    private Path root;

    @PostConstruct
    void init() throws IOException {
        root = Path.of(storageDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("Local image storage ready at {}", root);
    }

    public String upload(byte[] content, String contentType, String safeRelativePath) {
        Path dest = root.resolve(safeRelativePath).normalize();
        if (!dest.startsWith(root)) {
            throw ServiceException.invalid("Invalid upload path");
        }
        try {
            Files.createDirectories(dest.getParent());
            Files.write(dest, content);
        } catch (IOException e) {
            throw ServiceException.invalid("Failed to store image");
        }
        String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        return base + "/" + safeRelativePath.replace('\\', '/');
    }

    public void deleteByPublicUrl(String publicUrl) {
        if (publicUrl == null || publicUrl.isBlank()) {
            return;
        }
        String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        if (!publicUrl.startsWith(base + "/")) {
            return;
        }
        String relative = publicUrl.substring(base.length() + 1);
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("Failed to delete old upload {}", target);
        }
    }
}
