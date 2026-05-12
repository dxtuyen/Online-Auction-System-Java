package com.auction.server;

import com.auction.model.entity.Auction;
import com.auction.model.entity.BidTransaction;
import com.auction.model.enums.AuctionStatus;
import com.auction.model.observer.AuctionObserver;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.server.observer.AuctionEventManager;
import com.auction.util.JsonHelper;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Phục vụ 1 client kết nối.
 *
 * <p>Implement {@link AuctionObserver} — khi client subscribe vào phiên,
 * có bid mới là ClientHandler tự push JSON xuống client qua socket.</p>
 *
 * <p>Luồng chạy:
 * <ol>
 *   <li>Mở reader/writer UTF-8 từ socket</li>
 *   <li>Loop đọc từng dòng JSON từ client</li>
 *   <li>Parse → gọi {@link RequestRouter} để xử lý</li>
 *   <li>Gửi Response JSON về client</li>
 *   <li>Client ngắt → dọn dẹp observer subscription</li>
 * </ol>
 * </p>
 *
 * <p>Class này đồng thời nắm 3 vai trò:
 * giữ session của connection hiện tại ({@code currentUserId}),
 * làm adapter mạng giữa JSON text và object Java,
 * và làm observer để nhận event realtime từ domain rồi push ngược ra client.</p>
 */
public class ClientHandler implements Runnable, AuctionObserver {

    private final Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private final RequestRouter router;
    private final AuctionEventManager eventManager = AuctionEventManager.getInstance();

    // id user sau khi login — null nghĩa là chưa đăng nhập
    private UUID currentUserId;

    /**
     * Mỗi socket client được bọc trong đúng một handler.
     *
     * <p>Router cũng được tạo tại đây để toàn bộ request của connection này
     * luôn dùng chung một ngữ cảnh session.</p>
     */
    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.router = new RequestRouter(this);
    }

    @Override
    public void run() {
        try {
            // UTF-8 bắt buộc để xử lý tiếng Việt đúng
            reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new PrintWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8), true);

            String line;
            while ((line = reader.readLine()) != null) {
                // Protocol hiện tại là "mỗi dòng là một JSON request".
                handleLine(line);
            }
        } catch (IOException e) {
            // Client ngắt connection hoặc mạng lỗi
        } finally {
            cleanup();
        }
    }

    /**
     * Parse một dòng JSON rồi chuyển xuống router.
     *
     * <p>Nếu payload không parse được hoặc thiếu action, server không đóng connection ngay
     * mà trả về một {@code ERROR} response để client biết request nào bị lỗi.</p>
     */
    private void handleLine(String line) {
        try {
            Request req = JsonHelper.parseRequest(line);
            if (req == null || req.getAction() == null) {
                String requestId = req != null ? req.getRequestId() : null;
                send(Response.error("UNKNOWN", "Request không hợp lệ").withRequestId(requestId));
                return;
            }

            // Router chịu trách nhiệm điều phối action và bọc lỗi nghiệp vụ thành error response.
            Response res = router.route(req);
            if (res != null) send(res);

        } catch (Exception e) {
            send(Response.error("UNKNOWN", "Lỗi parse: " + e.getMessage()));
        }
    }

    /**
     * Gửi Response xuống client. Dùng synchronized tránh 2 thread ghi vào
     * cùng 1 writer gây lỗi interleaving.
     *
     * <p>Điều này quan trọng vì cùng một handler có thể vừa trả response cho request hiện tại,
     * vừa nhận event push từ observer callback ở thread khác.</p>
     */
    public synchronized void send(Response response) {
        if (writer != null) {
            writer.println(JsonHelper.toJson(response));
        }
    }

    /**
     * Dọn dẹp khi client disconnect.
     *
     * <p>Việc unsubscribe khỏi toàn bộ auction là bắt buộc; nếu bỏ sót,
     * event manager sẽ còn giữ reference tới handler đã chết và tiếp tục push vô ích.</p>
     */
    private void cleanup() {
        // Gỡ khỏi TẤT cả observer subscription để tránh memory leak
        eventManager.unsubscribeAll(this);

        try { if (socket != null) socket.close(); }
        catch (IOException ignored) {}

        System.out.println("[Server] Client disconnect: "
                + socket.getRemoteSocketAddress());
    }

    // ============= Accessors =============
    // currentUserId chính là "session state" đơn giản nhất của connection này.

    public UUID getCurrentUserId() { return currentUserId; }
    public void setCurrentUserId(UUID currentUserId) { this.currentUserId = currentUserId; }

    // ============= AuctionObserver implementation =============
    // Mỗi callback dưới đây sẽ được AuctionEventManager forward khi auction mà client đang
    // subscribe có thay đổi. Handler chỉ map event domain thành JSON push tương ứng.

    @Override
    public void onBidPlaced(Auction auction, BidTransaction bid) {
        // Dùng LinkedHashMap để giữ thứ tự field JSON đẹp
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctionId", auction.getId());
        data.put("bidId", bid.getId());
        data.put("bidderId", bid.getBidderId());
        data.put("amount", bid.getBidAmount());
        data.put("currentPrice", auction.getCurrentPrice());
        data.put("totalBids", auction.getTotalBids());
        data.put("timestamp", bid.getTimestamp().toString());

        // PUSH response không đi qua flow request/response thông thường mà được đẩy chủ động.
        send(Response.push("BID_UPDATE", data));
    }

    @Override
    public void onStatusChanged(Auction auction, AuctionStatus oldStatus, AuctionStatus newStatus) {
        // Client dùng event này để disable nút bid, cập nhật status label, đóng countdown...
        UUID highestBidderId = auction.getHighestBidderId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctionId", auction.getId());
        data.put("oldStatus", oldStatus.name());
        data.put("status", newStatus.name());
        data.put("currentPrice", auction.getCurrentPrice());
        // Auction có thể kết thúc mà không có ai bid, nên field leader phải chấp nhận null.
        data.put("highestBidderId", highestBidderId);

        send(Response.push("AUCTION_STATUS", data));
    }

    @Override
    public void onAuctionExtended(Auction auction, int extendedSeconds) {
        // Event riêng cho anti-sniping, để client cập nhật lại endTime mà không cần reload toàn bộ detail.
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("auctionId", auction.getId());
        data.put("newEndTime", auction.getEndTime().toString());
        data.put("extendedSeconds", extendedSeconds);

        send(Response.push("AUCTION_EXTENDED", data));
    }
}
