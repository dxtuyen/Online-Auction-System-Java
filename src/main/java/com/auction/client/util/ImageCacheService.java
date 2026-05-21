package com.auction.client.util;

import com.auction.client.model.ClientModel;
import com.auction.protocol.Response;
import com.auction.util.AppLogger;
import javafx.application.Platform;
import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Client-side cache cho ảnh: URL tương đối → {@link Image}.
 *
 * <p>Vì {@code ClientModel} hiện route response theo {@code action} (1 queue cho mỗi action),
 * nhiều GET_IMAGE song song sẽ tranh chấp queue. Dùng <b>single-thread loader</b> để
 * tuần tự hóa các request fetch — đơn giản, đủ dùng cho dashboard có ~20 item.</p>
 *
 * <p>Hit cache → callback ngay trên FX thread. Miss → enqueue + callback sau khi
 * tải xong.</p>
 */
public final class ImageCacheService {

    private static final Logger log = AppLogger.get(ImageCacheService.class);

    private static final class Holder {
        private static final ImageCacheService INSTANCE = new ImageCacheService();
    }

    public static ImageCacheService getInstance() {
        return Holder.INSTANCE;
    }

    private final Map<String, Image> cache = new ConcurrentHashMap<>();

    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "image-loader");
        t.setDaemon(true);
        return t;
    });

    private ImageCacheService() {}

    /**
     * Load ảnh từ URL tương đối. Callback luôn chạy trên JavaFX thread.
     * Trả ngay nếu URL trống/null — caller nên hiển thị placeholder.
     */
    public void loadAsync(String url, Consumer<Image> callback) {
        if (url == null || url.isBlank()) return;

        Image cached = cache.get(url);
        if (cached != null) {
            Platform.runLater(() -> callback.accept(cached));
            return;
        }

        loader.submit(() -> fetch(url, callback));
    }

    /** Xóa khỏi cache — gọi sau khi upload avatar/item mới để lần load sau lấy bản fresh. */
    public void invalidate(String url) {
        if (url != null) cache.remove(url);
    }

    @SuppressWarnings("unchecked")
    private void fetch(String url, Consumer<Image> callback) {
        try {
            ClientModel model = ClientModel.getInstance();
            model.sendRequest("GET_IMAGE", Map.of("url", url));
            Response res = model.waitForResponse("GET_IMAGE", 5000);
            if (res == null || !res.isSuccess()) return;

            Map<String, Object> data = (Map<String, Object>) res.getData();
            String base64 = data == null ? null : (String) data.get("dataBase64");
            if (base64 == null || base64.isBlank()) return;

            byte[] bytes = Base64.getDecoder().decode(base64);
            Image img = new Image(new ByteArrayInputStream(bytes));
            cache.put(url, img);
            Platform.runLater(() -> callback.accept(img));
        } catch (Exception e) {
            log.warning(() -> "Tải ảnh " + url + " thất bại: " + e.getMessage());
        }
    }
}
