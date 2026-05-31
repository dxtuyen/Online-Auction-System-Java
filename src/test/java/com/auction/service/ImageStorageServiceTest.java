package com.auction.service;

import com.auction.config.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ImageStorageService")
class ImageStorageServiceTest {

    private final ImageStorageService service = ImageStorageService.getInstance();

    @Test
    @DisplayName("save + loadAsBase64 round-trip ảnh hợp lệ")
    void saveAndLoad_roundTrip() throws Exception {
        service.init();
        String base64 = Base64.getEncoder().encodeToString("image-bytes".getBytes(StandardCharsets.UTF_8));
        String url = null;
        try {
            url = service.save(ImageStorageService.AVATAR_DIR, base64, "avatar.PNG");

            assertTrue(url.startsWith("avatars/"));
            assertTrue(url.endsWith(".png"));
            assertEquals(base64, service.loadAsBase64(url));
        } finally {
            deleteUploaded(url);
        }
    }

    @Test
    @DisplayName("save từ chối base64 lỗi, file rỗng, extension không whitelist")
    void save_rejectsInvalidInputs() {
        service.init();

        assertThrows(IllegalArgumentException.class,
                () -> service.save(ImageStorageService.AVATAR_DIR, "not-base64", "avatar.png"));

        String empty = Base64.getEncoder().encodeToString(new byte[0]);
        assertThrows(IllegalArgumentException.class,
                () -> service.save(ImageStorageService.AVATAR_DIR, empty, "avatar.png"));

        String valid = Base64.getEncoder().encodeToString("bytes".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(ImageStorageService.AVATAR_DIR, valid, "avatar.exe"));
    }

    @Test
    @DisplayName("loadAsBase64 trả null khi thiếu file hoặc path traversal")
    void load_rejectsMissingAndTraversal() {
        service.init();

        assertNull(service.loadAsBase64(null));
        assertNull(service.loadAsBase64(""));
        assertNull(service.loadAsBase64("avatars/missing-file.png"));
        assertNull(service.loadAsBase64("../pom.xml"));
    }

    private void deleteUploaded(String url) throws Exception {
        if (url == null || url.isBlank()) return;
        Path root = Path.of(AppConfig.get("UPLOAD_DIR", "uploads")).toAbsolutePath();
        Path target = root.resolve(url).normalize();
        if (target.startsWith(root)) {
            Files.deleteIfExists(target);
        }
    }
}
