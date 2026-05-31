package com.auction.service;

import com.auction.config.AppConfig;
import com.auction.util.AppLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Service lưu/đọc file ảnh ở thư mục uploads/ trên server.
 *
 * <p>Convention:
 * <ul>
 *   <li>Avatar lưu ở {@code uploads/avatars/<uuid>.<ext>}</li>
 *   <li>Ảnh item lưu ở {@code uploads/items/<uuid>.<ext>}</li>
 *   <li>URL lưu trong DB là path tương đối, ví dụ {@code avatars/abc.png}</li>
 * </ul>
 *
 * <p>Bytes truyền giữa client/server qua base64 string trong JSON payload —
 * tránh phải mở socket kênh thứ 2 cho binary.</p>
 */
public final class ImageStorageService {

    private static final Logger log = AppLogger.get(ImageStorageService.class);

    public static final String AVATAR_DIR = "avatars";
    public static final String ITEM_DIR = "items";

    /** Whitelist extension — chống upload file lạ (exe, script, ...). */
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /** Giới hạn dung lượng — tránh client đẩy file 100MB qua JSON. */
    private static final int MAX_BYTES = 3 * 1024 * 1024;   // 3 MB

    private static final class Holder {
        private static final ImageStorageService INSTANCE = new ImageStorageService();
    }

    public static ImageStorageService getInstance() {
        return Holder.INSTANCE;
    }

    private final Path root;

    private ImageStorageService() {
        this.root = Path.of(AppConfig.get("UPLOAD_DIR", "uploads")).toAbsolutePath();
    }

    /** Tạo thư mục uploads/ + subdirs nếu chưa có. Gọi 1 lần lúc server khởi động. */
    public void init() {
        try {
            Files.createDirectories(root.resolve(AVATAR_DIR));
            Files.createDirectories(root.resolve(ITEM_DIR));
            log.info(() -> "Upload dir: " + root);
        } catch (IOException e) {
            throw new IllegalStateException("Không tạo được thư mục upload: " + root, e);
        }
    }

    /**
     * Lưu bytes ảnh xuống disk. Trả URL tương đối để lưu vào DB.
     *
     * @param subDir   "avatars" hoặc "items"
     * @param base64   chuỗi base64 client gửi lên
     * @param fileName tên file gốc (chỉ dùng để extract extension)
     * @return relative URL ví dụ "avatars/abc.png"
     */
    public String save(String subDir, String base64, String fileName) {
        Objects.requireNonNull(subDir, "subDir");
        Objects.requireNonNull(base64, "base64");

        byte[] bytes = decodeBase64(base64);
        if (bytes.length == 0) {
            throw new IllegalArgumentException("File rỗng");
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException(
                    "File quá lớn (>" + (MAX_BYTES / 1024 / 1024) + "MB)");
        }

        String ext = extractExt(fileName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new IllegalArgumentException(
                    "Định dạng không hỗ trợ. Cho phép: " + ALLOWED_EXT);
        }

        String newName = UUID.randomUUID() + "." + ext;
        Path target = root.resolve(subDir).resolve(newName);
        try {
            Files.write(target, bytes);   // mặc định: CREATE + TRUNCATE_EXISTING + WRITE
        } catch (IOException e) {
            throw new IllegalStateException("Không ghi được file: " + e.getMessage(), e);
        }
        // URL trả về dùng forward slash để client/server đồng nhất (không phụ thuộc OS).
        return subDir + "/" + newName;
    }

    /** Đọc bytes từ URL tương đối, trả base64 để gửi qua JSON. Trả null nếu file không có. */
    public String loadAsBase64(String url) {
        if (url == null || url.isBlank()) return null;
        Path safe = resolveSafe(url);
        if (safe == null || !Files.isRegularFile(safe)) return null;
        try {
            byte[] data = Files.readAllBytes(safe);
            return Base64.getEncoder().encodeToString(data);
        } catch (IOException e) {
            log.warning(() -> "Đọc file ảnh thất bại " + url + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Resolve url tương đối thành Path tuyệt đối — chặn path traversal (../etc/passwd).
     * Trả null nếu url cố vượt khỏi root.
     */
    private Path resolveSafe(String url) {
        Path candidate = root.resolve(url).normalize();
        if (!candidate.startsWith(root)) return null;
        return candidate;
    }

    private static byte[] decodeBase64(String s) {
        try {
            return Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Base64 không hợp lệ");
        }
    }

    private static String extractExt(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
